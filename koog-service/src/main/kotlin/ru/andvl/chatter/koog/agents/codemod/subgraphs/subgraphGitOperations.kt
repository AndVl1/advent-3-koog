package ru.andvl.chatter.koog.agents.codemod.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.model.codemod.GitResult
import ru.andvl.chatter.koog.model.codemod.VerificationResult
import java.io.File
import java.time.Instant

private val logger = LoggerFactory.getLogger("codemod-git")
private val json = Json { ignoreUnknownKeys = true }

// Git helper functions for direct operations
private fun executeGitCommand(repositoryPath: String, vararg command: String): Result<String> {
    return try {
        val dir = File(repositoryPath)
        if (!dir.exists() || !dir.isDirectory) {
            return Result.failure(IllegalArgumentException("Repository path does not exist or is not a directory"))
        }

        logger.info("Executing git command in $repositoryPath: ${command.joinToString(" ")}")

        val process = ProcessBuilder(*command)
            .directory(dir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            Result.success(output)
        } else {
            Result.failure(RuntimeException("Git command failed with exit code $exitCode: $output"))
        }
    } catch (e: Exception) {
        logger.error("Error executing git command", e)
        Result.failure(e)
    }
}

private fun gitCommit(repositoryPath: String, message: String): Result<String> {
    // Add all changes
    val addResult = executeGitCommand(repositoryPath, "git", "add", ".")
    if (addResult.isFailure) {
        return Result.failure(addResult.exceptionOrNull() ?: RuntimeException("Failed to add files"))
    }
    executeGitCommand(repositoryPath, "git", "status")

    // Commit with message
    val commitResult = executeGitCommand(repositoryPath, "git", "commit", "-m", message)
    if (commitResult.isFailure) {
        return Result.failure(commitResult.exceptionOrNull() ?: RuntimeException("Failed to commit"))
    }

    // Get commit SHA
    return executeGitCommand(repositoryPath, "git", "rev-parse", "HEAD")
        .map { it.trim() }
}

private fun gitPush(repositoryPath: String, branchName: String, force: Boolean = false): Result<Boolean> {
    val args = mutableListOf("git", "push", "origin", branchName)
    if (force) {
        args.add("--force")
    }

    return executeGitCommand(repositoryPath, *args.toTypedArray())
        .fold(
            onSuccess = { Result.success(true) },
            onFailure = { error ->
                // Check if error is due to remote rejection
                val errorMessage = error.message ?: ""
                if (errorMessage.contains("rejected") || errorMessage.contains("non-fast-forward")) {
                    Result.success(false) // Push was rejected, not a hard failure
                } else {
                    Result.failure(error)
                }
            }
        )
}

private fun gitCreateBranch(repositoryPath: String, branchName: String, baseBranch: String? = null): Result<Unit> {
    val args = if (baseBranch != null) {
        arrayOf("git", "checkout", "-b", branchName, baseBranch)
    } else {
        arrayOf("git", "checkout", "-b", branchName)
    }

    return executeGitCommand(repositoryPath, *args).map { Unit }
}

private fun gitGetDiff(repositoryPath: String, base: String, head: String): Result<String> {
    return executeGitCommand(repositoryPath, "git", "diff", "$base...$head")
}

/**
 * Subgraph 5: Git Operations
 *
 * Flow:
 * 1. Create commit with changes
 * 2. Push changes to remote
 * 3. Handle push rejection (create new branch if needed)
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphGitOperations(
    model: LLModel
): AIAgentSubgraphDelegate<VerificationResult, GitResult> =
    subgraph(name = "git-operations") {
        val nodeCreateCommit by nodeCreateCommit(model)
        val nodePushChanges by nodePushChanges()
        val nodeHandlePushRejection by nodeHandlePushRejection()
        val nodeCheckPushStatus by nodeCheckPushStatus()

        edge(nodeStart forwardTo nodeCreateCommit)
        edge(nodeCreateCommit forwardTo nodePushChanges)
        edge(nodePushChanges forwardTo nodeCheckPushStatus)

        // If push successful, finish
        edge(nodeCheckPushStatus forwardTo nodeFinish onCondition { result: GitResult ->
            result.pushed
        })

        // If push rejected, handle rejection and try again
        edge(nodeCheckPushStatus forwardTo nodeHandlePushRejection onCondition { result: GitResult ->
            result.pushRejected
        })
        edge(nodeHandlePushRejection forwardTo nodePushChanges)
    }

/**
 * Node: Create commit message using LLM
 */
private fun AIAgentSubgraphBuilderBase<VerificationResult, GitResult>.nodeCreateCommit(model: LLModel) =
    node<VerificationResult, String>("create-commit") { verificationResult ->
        val repositoryPath = storage.get(repositoryPathKey) ?: throw IllegalStateException("Repository path not found")
        val userRequest = storage.get(userRequestKey) ?: "Code modifications"
        val modifications = storage.get(modificationsAppliedKey)

        logger.info("Creating commit for changes")

        val filesModified = modifications?.filesModified?.joinToString(", ") ?: "Unknown files"
        val filesCreated = modifications?.filesCreated?.joinToString(", ") ?: "None"

        val commitMessage = llm.writeSession {
            appendPrompt {
                user(
                    """
Create a Git commit message for the code changes made.

**Original User Request**: $userRequest

**Files Modified**: $filesModified
**Files Created**: $filesCreated
**Patches Applied**: ${modifications?.patchesApplied ?: 0}

**Verification Status**: ${if (verificationResult.success) "SUCCESS" else "FAILED"}

**Guidelines**:
- Use conventional commit format: type(scope): description
- Types: feat, fix, refactor, test, docs, chore
- Keep description concise (max 72 chars for first line)
- Add body with details if needed
- Mention verification status

**Example**:
```
feat(api): Add user authentication endpoint

- Implemented JWT token generation
- Added login/logout routes
- Verification: Tests passed successfully
```

**Output**: Only output the commit message text, no extra formatting or markdown blocks.
""".trimIndent()
                )
            }

            val response = requestLLM()
            response.content
        }

        logger.info("Commit message generated, creating commit")
        logger.debug("Commit message: $commitMessage")

        // Use direct Git operations
        val commitSha = gitCommit(repositoryPath, commitMessage).getOrElse { error ->
            logger.error("Failed to create commit: ${error.message}")
            throw IllegalStateException("Failed to create commit: ${error.message}", error)
        }

        storage.set(createStorageKey<String>("commit-sha"), commitSha)

        logger.info("Commit created successfully: $commitSha")
        commitSha
    }

/**
 * Node: Push changes to remote
 */
private fun AIAgentSubgraphBuilderBase<VerificationResult, GitResult>.nodePushChanges() =
    node<String, Boolean>("push-changes") { commitSha ->
        val repositoryPath = storage.get(repositoryPathKey) ?: throw IllegalStateException("Repository path not found")
        val branchName = storage.get(featureBranchKey) ?: throw IllegalStateException("Feature branch not found")

        logger.info("Pushing changes to remote: $branchName")

        // Use direct Git operations
        val pushSucceeded = gitPush(repositoryPath, branchName, force = false).getOrElse { error ->
            logger.error("Failed to push changes: ${error.message}")
            throw IllegalStateException("Failed to push changes: ${error.message}", error)
        }

        logger.info("Push result: ${if (pushSucceeded) "SUCCESS" else "REJECTED"}")

        // Store result for status check
        storage.set(createStorageKey<Boolean>("push-result"), pushSucceeded)

        pushSucceeded
    }

/**
 * Node: Check push status (success or rejected)
 */
private fun AIAgentSubgraphBuilderBase<VerificationResult, GitResult>.nodeCheckPushStatus() =
    node<Boolean, GitResult>("check-push-status") { pushSucceeded ->
        storage.set(createStorageKey<Boolean>("push-success"), pushSucceeded)
        storage.set(createStorageKey<Boolean>("push-rejected"), !pushSucceeded)

        val commitSha = storage.get(createStorageKey<String>("commit-sha")) ?: "unknown"
        val branchName = storage.get(featureBranchKey) ?: "unknown"

        if (pushSucceeded) {
            logger.info("Push successful")
            GitResult(
                commitSha = commitSha,
                pushed = true,
                branchName = branchName,
                pushRejected = false,
                message = "Changes pushed successfully"
            )
        } else {
            logger.warn("Push rejected, will create new branch")
            GitResult(
                commitSha = commitSha,
                pushed = false,
                branchName = branchName,
                pushRejected = true,
                message = "Push rejected, creating new branch"
            )
        }
    }

/**
 * Node: Handle push rejection by creating new branch
 */
private fun AIAgentSubgraphBuilderBase<VerificationResult, GitResult>.nodeHandlePushRejection() =
    node<GitResult, String>("handle-push-rejection") { gitResult ->
        val repositoryPath = storage.get(repositoryPathKey) ?: throw IllegalStateException("Repository path not found")
        val oldBranch = storage.get(featureBranchKey) ?: throw IllegalStateException("Feature branch not found")
        val timestamp = Instant.now().epochSecond
        val newBranch = "$oldBranch-retry-$timestamp"

        logger.info("Creating new branch due to push rejection: $newBranch")

        // Use direct Git operations
        gitCreateBranch(repositoryPath, newBranch, baseBranch = null).getOrElse { error ->
            logger.error("Failed to create new branch: ${error.message}")
            throw IllegalStateException("Failed to create new branch: ${error.message}", error)
        }

        // Update feature branch in storage
        storage.set(featureBranchKey, newBranch)
        storage.set(createStorageKey<Boolean>("push-rejected"), false) // Reset flag

        // Get commit SHA for the new branch
        val commitSha = storage.get(createStorageKey<String>("commit-sha")) ?: "unknown"

        logger.info("New branch created successfully: $newBranch")
        commitSha // Return commit SHA to continue push flow
    }
