package ru.andvl.chatter.koog.agents.codemod.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.mcp.McpProvider
import ru.andvl.chatter.koog.model.codemod.CodeModificationResponse
import ru.andvl.chatter.koog.model.codemod.GitResult
import ru.andvl.chatter.koog.model.codemod.VerificationResult
import ru.andvl.chatter.koog.model.codemod.VerificationStatus
import java.io.File

private val logger = LoggerFactory.getLogger("codemod-finalize")
private val json = Json { ignoreUnknownKeys = true }

internal val verificationResultKey = createStorageKey<VerificationResult>("verification-result")

// Git helper function for diff
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

private fun gitGetDiff(repositoryPath: String, base: String, head: String): Result<String> {
    return executeGitCommand(repositoryPath, "git", "diff", "$base...$head")
}

private fun gitGetFilesChanged(repositoryPath: String, base: String, head: String): Result<Int> {
    return executeGitCommand(repositoryPath, "git", "diff", "--numstat", "$base...$head")
        .map { output ->
            output.lines().count { it.isNotBlank() }
        }
}

/**
 * Subgraph 6: Finalize
 *
 * Flow:
 * 1. Check if push was successful
 * 2a. If pushed: Create PR using GitHub MCP tools
 * 2b. If not pushed: Generate diff
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphFinalize(
    model: LLModel
): AIAgentSubgraphDelegate<GitResult, CodeModificationResponse> =
    subgraph(
        name = "finalize",
        tools = McpProvider.getGithubToolsRegistry().tools
    ) {
        val nodeCheckGitStatus by nodeCheckGitStatus()
        val nodeCreatePR by nodeCreatePR()
        val nodeExecuteTool by nodeExecuteTool("finalize-execute-tool")
        val nodeSendToolResult by nodeLLMSendToolResult("finalize-send-tool-result")
        val nodeParsePRResult by nodeParsePRResult()
        val nodeGenerateDiff by nodeGenerateDiff()

        edge(nodeStart forwardTo nodeCheckGitStatus)

        // If pushed, create PR
        edge(nodeCheckGitStatus forwardTo nodeCreatePR onCondition { result: GitResult ->
            result.pushed
        })

        // If not pushed, generate diff
        edge(nodeCheckGitStatus forwardTo nodeGenerateDiff onCondition { result: GitResult ->
            !result.pushed
        })

        // Tool execution loop for PR creation
        edge(nodeCreatePR forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeCreatePR forwardTo nodeParsePRResult onAssistantMessage { true })

        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeSendToolResult forwardTo nodeParsePRResult onAssistantMessage { true })

        edge(nodeParsePRResult forwardTo nodeFinish)
        edge(nodeGenerateDiff forwardTo nodeFinish)
    }

/**
 * Node: Check Git status (pushed or not)
 */
private fun AIAgentSubgraphBuilderBase<GitResult, CodeModificationResponse>.nodeCheckGitStatus() =
    node<GitResult, GitResult>("check-git-status") { gitResult ->
        storage.set(createStorageKey<Boolean>("git-pushed"), gitResult.pushed)

        logger.info("Git status: pushed=${gitResult.pushed}, rejected=${gitResult.pushRejected}")

        gitResult
    }

/**
 * Node: Create Pull Request using GitHub MCP
 */
private fun AIAgentSubgraphBuilderBase<GitResult, CodeModificationResponse>.nodeCreatePR() =
    node<GitResult, Message.Response>("create-pull-request") { gitResult ->
        val owner = storage.get(ownerKey) ?: throw IllegalStateException("Owner not found")
        val repo = storage.get(repoKey) ?: throw IllegalStateException("Repo not found")
        val defaultBranch = storage.get(defaultBranchKey) ?: "main"
        val userRequest = storage.get(userRequestKey) ?: "Code modifications"
        val modifications = storage.get(modificationsAppliedKey)
        val verificationResult = storage.get(verificationResultKey)
        val iterationsUsed = storage.get(createStorageKey<Int>("iterations-used")) ?: 0

        logger.info("Creating Pull Request for $owner/$repo using GitHub MCP")

        val filesModified = modifications?.filesModified ?: emptyList()
        val filesCreated = modifications?.filesCreated ?: emptyList()

        // Build verification details for PR description
        val verificationDetails = if (verificationResult != null) {
            """
## Verification Results

- **Status**: ✅ Passed
- **Command**: `${verificationResult.commandExecuted}`
- **Exit Code**: ${verificationResult.exitCode}
- **Iterations**: $iterationsUsed
"""
        } else {
            "## Verification Results\n\n- **Status**: ⚠️ Not verified"
        }

        // Use LLM to call github-create-pull-request MCP tool
        llm.writeSession {
            appendPrompt {
                system(
                    """
You are an expert at creating clear, professional Pull Request descriptions.

**Your Task**:
1. Create a clear PR title (max 80 chars) based on the user request
2. Create a comprehensive PR description with:
   - Summary of changes made
   - Motivation from the original user request
   - List of modified/created files
   - Verification results
3. Use the **github-create-pull-request** tool to create the PR

**Tool Parameters**:
- owner: "$owner"
- repo: "$repo"
- title: Your generated title
- body: Your generated description (include verification results)
- head: "${gitResult.branchName}"
- base: "$defaultBranch"
""".trimIndent()
                )

                user(
                    """
Create a Pull Request for the following code modifications:

**Repository**: $owner/$repo
**Feature Branch**: ${gitResult.branchName}
**Base Branch**: $defaultBranch
**Commit SHA**: ${gitResult.commitSha}

**Original User Request**:
$userRequest

**Modifications**:
- Files Modified: ${filesModified.joinToString(", ")}
- Files Created: ${filesCreated.joinToString(", ")}
- Patches Applied: ${modifications?.patchesApplied ?: 0}

$verificationDetails

Please create the PR now using the github-create-pull-request tool.
""".trimIndent()
                )
            }

            requestLLM()
        }
    }

/**
 * Node: Execute tool calls
 */
private fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
    name: String
): AIAgentNodeDelegate<Message.Tool.Call, ReceivedToolResult> =
    node(name) { toolCall ->
        environment.executeTool(toolCall)
    }

/**
 * Node: Parse PR creation result
 */
private fun AIAgentSubgraphBuilderBase<GitResult, CodeModificationResponse>.nodeParsePRResult() =
    node<String, CodeModificationResponse>("parse-pr-result") { prResult ->
        val modifications = storage.get(modificationsAppliedKey)
        val iterationsUsed = storage.get(createStorageKey<Int>("iterations-used")) ?: 0

        logger.info("Parsing PR creation result")
        logger.debug("PR result: $prResult")

        val filesModified = modifications?.filesModified ?: emptyList()

        // Try to extract PR URL and number from the assistant's message
        // The message should contain something like "Created pull request #123 at https://github.com/..."
        val prUrlRegex = "https://github\\.com/[^/]+/[^/]+/pull/\\d+".toRegex()
        val prNumberRegex = "#(\\d+)".toRegex()

        val prUrl = prUrlRegex.find(prResult)?.value
        val prNumber = prNumberRegex.find(prResult)?.groupValues?.get(1)?.toIntOrNull()

        logger.info("Extracted PR info: url=$prUrl, number=$prNumber")

        CodeModificationResponse(
            success = true,
            prUrl = prUrl,
            prNumber = prNumber,
            diff = null,
            commitSha = storage.get(createStorageKey<String>("commit-sha")),
            branchName = storage.get(featureBranchKey) ?: "unknown",
            filesModified = filesModified,
            verificationStatus = VerificationStatus.SUCCESS,
            iterationsUsed = iterationsUsed,
            errorMessage = null,
            message = if (prUrl != null) {
                "Pull Request created successfully: $prUrl"
            } else {
                "Pull Request creation completed: $prResult"
            }
        )
    }

/**
 * Node: Generate diff (if push failed)
 */
private fun AIAgentSubgraphBuilderBase<GitResult, CodeModificationResponse>.nodeGenerateDiff() =
    node<GitResult, CodeModificationResponse>("generate-diff") { gitResult ->
        val repositoryPath = storage.get(repositoryPathKey) ?: throw IllegalStateException("Repository path not found")
        val defaultBranch = storage.get(defaultBranchKey) ?: "main"
        val modifications = storage.get(modificationsAppliedKey)

        logger.info("Generating diff for changes")

        // Use direct Git operations
        val diffResult = gitGetDiff(repositoryPath, defaultBranch, gitResult.branchName)
        val filesChangedResult = gitGetFilesChanged(repositoryPath, defaultBranch, gitResult.branchName)

        if (diffResult.isFailure) {
            val error = diffResult.exceptionOrNull()
            logger.error("Failed to generate diff: ${error?.message}")

            CodeModificationResponse(
                success = false,
                prUrl = null,
                prNumber = null,
                diff = null,
                commitSha = gitResult.commitSha,
                branchName = gitResult.branchName,
                filesModified = modifications?.filesModified ?: emptyList(),
                verificationStatus = VerificationStatus.FAILED_PUSH,
                iterationsUsed = storage.get(createStorageKey<Int>("iterations-used")) ?: 0,
                errorMessage = "Failed to generate diff: ${error?.message}",
                message = "Changes committed but diff generation failed"
            )
        } else {
            val diff = diffResult.getOrThrow()
            val filesChanged = filesChangedResult.getOrElse { 0 }

            logger.info("Diff generated successfully ($filesChanged files changed)")

            CodeModificationResponse(
                success = true,
                prUrl = null,
                prNumber = null,
                diff = diff,
                commitSha = gitResult.commitSha,
                branchName = gitResult.branchName,
                filesModified = modifications?.filesModified ?: emptyList(),
                verificationStatus = VerificationStatus.FAILED_PUSH,
                iterationsUsed = storage.get(createStorageKey<Int>("iterations-used")) ?: 0,
                errorMessage = null,
                message = "Changes committed, diff generated (push was rejected)"
            )
        }
    }
