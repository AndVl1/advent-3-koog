package ru.andvl.chatter.koog.agents.codemod.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.model.codemod.CodeModificationRequest
import ru.andvl.chatter.koog.model.codemod.SetupResult
import java.io.File
import java.time.Instant

private val logger = LoggerFactory.getLogger("codemod-setup")
private val json = Json { ignoreUnknownKeys = true }

// Storage keys
internal val repositoryPathKey = createStorageKey<String>("repository-path")
internal val defaultBranchKey = createStorageKey<String>("default-branch")
internal val featureBranchKey = createStorageKey<String>("feature-branch")
internal val ownerKey = createStorageKey<String>("owner")
internal val repoKey = createStorageKey<String>("repo")
internal val userRequestKey = createStorageKey<String>("user-request")

// Git helper functions
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

private fun cloneRepository(repositoryUrl: String, targetDirectory: String): Result<String> {
    val dir = File(targetDirectory)
    if (dir.exists()) {
        logger.info("Target directory already exists: $targetDirectory")
        return Result.success(targetDirectory)
    }

    return try {
        logger.info("Cloning repository $repositoryUrl to $targetDirectory")
        val process = ProcessBuilder("git", "clone", repositoryUrl, targetDirectory)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            Result.success(targetDirectory)
        } else {
            Result.failure(RuntimeException("Git clone failed with exit code $exitCode: $output"))
        }
    } catch (e: Exception) {
        logger.error("Error cloning repository", e)
        Result.failure(e)
    }
}

private fun getCurrentBranch(repositoryPath: String): Result<String> {
    return executeGitCommand(repositoryPath, "git", "rev-parse", "--abbrev-ref", "HEAD")
        .map { it.trim() }
}

private fun createBranch(repositoryPath: String, branchName: String, baseBranch: String): Result<Unit> {
    return executeGitCommand(repositoryPath, "git", "checkout", "-b", branchName, baseBranch)
        .map { Unit }
}

private suspend fun getDefaultBranchFromGitHub(httpClient: HttpClient, owner: String, repo: String): String? {
    return try {
        val response: HttpResponse = httpClient.get("https://api.github.com/repos/$owner/$repo")
        if (response.status.value == 200) {
            val body = response.bodyAsText()
            val jsonElement = json.parseToJsonElement(body).jsonObject
            jsonElement["default_branch"]?.jsonPrimitive?.content
        } else {
            null
        }
    } catch (e: Exception) {
        logger.error("Failed to get default branch from GitHub: ${e.message}")
        null
    }
}

/**
 * Subgraph 1: Repository Setup
 *
 * Flow:
 * 1. Check if repository already exists in session
 * 2. Clone or reuse existing repository
 * 3. Detect default branch via GitHub API
 * 4. Create feature branch with timestamp
 */
internal suspend fun AIAgentGraphStrategyBuilder<CodeModificationRequest, *>.subgraphRepositorySetup(): AIAgentSubgraphDelegate<CodeModificationRequest, SetupResult> =
    subgraph(name = "repository-setup") {
        val nodeCheckRepository by nodeCheckRepository()
        val nodeCloneOrReuse by nodeCloneOrReuse()
        val nodeDetectDefaultBranch by nodeDetectDefaultBranch()
        val nodeCreateFeatureBranch by nodeCreateFeatureBranch()

        edge(nodeStart forwardTo nodeCheckRepository)
        edge(nodeCheckRepository forwardTo nodeCloneOrReuse)
        edge(nodeCloneOrReuse forwardTo nodeDetectDefaultBranch)
        edge(nodeDetectDefaultBranch forwardTo nodeCreateFeatureBranch)
        edge(nodeCreateFeatureBranch forwardTo nodeFinish)
    }

/**
 * Node: Check if repository exists in session storage
 */
private fun AIAgentSubgraphBuilderBase<CodeModificationRequest, SetupResult>.nodeCheckRepository() =
    node<CodeModificationRequest, CodeModificationRequest>("check-repository") { request ->
        logger.info("Checking if repository ${request.githubRepo} exists in session")

        // Parse owner and repo from URL
        val repoUrl = request.githubRepo.removeSuffix(".git")
        val parts = repoUrl.split("/")
        val owner = parts[parts.size - 2]
        val repo = parts.last()

        // Store owner, repo, user request, and dockerEnv for later use
        storage.set(ownerKey, owner)
        storage.set(repoKey, repo)
        storage.set(userRequestKey, request.userRequest)

        // Store dockerEnv if provided
        if (request.dockerEnv != null) {
            storage.set(dockerEnvKey, request.dockerEnv)
            logger.info("Docker environment stored: ${request.dockerEnv.baseImage}")
        } else {
            logger.info("No Docker environment provided")
        }

        // Check if repository path already exists in storage
        val existingPath = storage.get(repositoryPathKey)
        if (existingPath != null && File(existingPath).exists()) {
            logger.info("Repository already exists at: $existingPath")
        } else {
            logger.info("Repository not found in session, will clone")
        }

        request
    }

/**
 * Node: Clone repository or reuse existing one
 */
private fun AIAgentSubgraphBuilderBase<CodeModificationRequest, SetupResult>.nodeCloneOrReuse() =
    node<CodeModificationRequest, String>("clone-or-reuse-repository") { request ->
        val workDir = File("/tmp/code-modifications")
        if (!workDir.exists()) {
            workDir.mkdirs()
            logger.info("Created work directory: ${workDir.absolutePath}")
        }

        val owner = storage.get(ownerKey)!!
        val repo = storage.get(repoKey)!!
        val repoDir = File(workDir, "$owner-$repo")

        // Check if repository already exists
        val existingPath = storage.get(repositoryPathKey)
        if (existingPath != null && File(existingPath).exists()) {
            logger.info("Reusing existing repository at: $existingPath")
            storage.set(repositoryPathKey, existingPath)
            return@node existingPath
        }

        // Clone repository
        logger.info("Cloning repository ${request.githubRepo} to ${repoDir.absolutePath}")

        val clonedPath = cloneRepository(
            repositoryUrl = request.githubRepo,
            targetDirectory = repoDir.absolutePath
        ).getOrElse { error ->
            logger.error("Failed to clone repository: ${error.message}")
            throw IllegalStateException("Failed to clone repository: ${error.message}", error)
        }

        // Store repository path
        storage.set(repositoryPathKey, clonedPath)
        logger.info("Repository cloned successfully to: $clonedPath")

        clonedPath
    }

/**
 * Node: Detect default branch (main/master) via GitHub API
 */
private suspend fun AIAgentSubgraphBuilderBase<CodeModificationRequest, SetupResult>.nodeDetectDefaultBranch() =
    node<String, String>("detect-default-branch") { repositoryPath ->
        val owner = storage.get(ownerKey)!!
        val repo = storage.get(repoKey)!!

        logger.info("Detecting default branch for $owner/$repo via GitHub API")

        // Use HttpClient to get default branch from GitHub API
        val httpClient = HttpClient()
        val defaultBranch = getDefaultBranchFromGitHub(httpClient, owner, repo) ?: run {
            logger.warn("Failed to detect default branch via GitHub API, falling back to git current branch")
            // Fallback: get current branch from local repo
            getCurrentBranch(repositoryPath).getOrElse {
                logger.warn("Failed to get current branch, defaulting to 'main'")
                "main"
            }
        }
        httpClient.close()

        storage.set(defaultBranchKey, defaultBranch)
        logger.info("Default branch detected: $defaultBranch")

        defaultBranch
    }

/**
 * Node: Create feature branch with timestamp
 */
private fun AIAgentSubgraphBuilderBase<CodeModificationRequest, SetupResult>.nodeCreateFeatureBranch() =
    node<String, SetupResult>("create-feature-branch") { defaultBranch ->
        val repositoryPath = storage.get(repositoryPathKey)!!
        val owner = storage.get(ownerKey)!!
        val repo = storage.get(repoKey)!!
        val timestamp = Instant.now().epochSecond
        val featureBranch = "ai/task-$timestamp"

        logger.info("Creating feature branch: $featureBranch based on $defaultBranch")

        createBranch(
            repositoryPath = repositoryPath,
            branchName = featureBranch,
            baseBranch = defaultBranch
        ).getOrElse { error ->
            logger.error("Failed to create feature branch: ${error.message}")
            throw IllegalStateException("Failed to create feature branch: ${error.message}", error)
        }

        storage.set(featureBranchKey, featureBranch)
        logger.info("Feature branch created successfully: $featureBranch")

        SetupResult(
            repositoryPath = repositoryPath,
            defaultBranch = defaultBranch,
            featureBranch = featureBranch,
            owner = owner,
            repo = repo
        )
    }
