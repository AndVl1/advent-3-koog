package ru.andvl.chatter.koog.agents.repoanalyzer.subgraphs

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
import ru.andvl.chatter.koog.agents.repoanalyzer.*
import ru.andvl.chatter.koog.model.repoanalyzer.SetupResult
import ru.andvl.chatter.koog.model.repoanalyzer.ValidatedRequest
import java.io.File

private val logger = LoggerFactory.getLogger("repoanalyzer-setup")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Execute git command in a directory
 */
private fun executeGitCommand(repositoryPath: String, vararg command: String): Result<String> {
    return try {
        val dir = File(repositoryPath)
        if (!dir.exists() || !dir.isDirectory) {
            return Result.failure(IllegalArgumentException("Path does not exist or is not a directory: $repositoryPath"))
        }

        logger.debug("Executing git command: ${command.joinToString(" ")}")

        val process = ProcessBuilder(*command)
            .directory(dir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            Result.success(output.trim())
        } else {
            Result.failure(RuntimeException("Git command failed (exit code $exitCode): $output"))
        }
    } catch (e: Exception) {
        logger.error("Error executing git command: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Clone repository using git
 */
private fun cloneRepository(repositoryUrl: String, targetDirectory: String): Result<String> {
    return try {
        val dir = File(targetDirectory)
        if (dir.exists()) {
            logger.info("Target directory already exists: $targetDirectory")
            return Result.success(targetDirectory)
        }

        logger.info("Cloning repository: $repositoryUrl -> $targetDirectory")

        val process = ProcessBuilder("git", "clone", repositoryUrl, targetDirectory)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            logger.info("Repository cloned successfully")
            Result.success(targetDirectory)
        } else {
            logger.error("Git clone failed: $output")
            Result.failure(RuntimeException("Git clone failed (exit code $exitCode): $output"))
        }
    } catch (e: Exception) {
        logger.error("Error cloning repository: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Get current git branch
 */
private fun getCurrentBranch(repositoryPath: String): Result<String> {
    return executeGitCommand(repositoryPath, "git", "rev-parse", "--abbrev-ref", "HEAD")
}

/**
 * Fetch default branch from GitHub API
 */
private suspend fun getDefaultBranchFromGitHub(httpClient: HttpClient, owner: String, repo: String): String? {
    return try {
        logger.debug("Fetching default branch from GitHub API: $owner/$repo")

        val response: HttpResponse = httpClient.get("https://api.github.com/repos/$owner/$repo")

        if (response.status.value == 200) {
            val body = response.bodyAsText()
            val jsonElement = json.parseToJsonElement(body).jsonObject
            val defaultBranch = jsonElement["default_branch"]?.jsonPrimitive?.content

            logger.debug("GitHub API returned default branch: $defaultBranch")
            defaultBranch
        } else {
            logger.warn("GitHub API returned status: ${response.status.value}")
            null
        }
    } catch (e: Exception) {
        logger.warn("Failed to fetch default branch from GitHub API: ${e.message}")
        null
    }
}

/**
 * Subgraph: Repository Setup
 *
 * Flow:
 * 1. Check if repository already exists (reuse if possible)
 * 2. Clone repository if not exists
 * 3. Verify successful clone
 *
 * Input: ValidatedRequest
 * Output: SetupResult (contains repositoryPath, defaultBranch, owner, repo, alreadyExisted)
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphRepositorySetup():
        AIAgentSubgraphDelegate<ValidatedRequest, SetupResult> =
    subgraph(name = "repository-setup") {
        val nodeCheckExistingClone by nodeCheckExistingClone()
        val nodeCloneRepository by nodeCloneRepository()
        val nodeVerifyClone by nodeVerifyClone()

        edge(nodeStart forwardTo nodeCheckExistingClone)
        edge(nodeCheckExistingClone forwardTo nodeCloneRepository)
        edge(nodeCloneRepository forwardTo nodeVerifyClone)
        edge(nodeVerifyClone forwardTo nodeFinish)
    }

/**
 * Node: Check if repository already exists in working directory
 *
 * Checks if the repository was already cloned in previous runs.
 */
private fun AIAgentSubgraphBuilderBase<ValidatedRequest, SetupResult>.nodeCheckExistingClone() =
    node<ValidatedRequest, ValidatedRequest>("check-existing-clone") { request ->
        logger.info("Checking for existing clone of ${request.owner}/${request.repo}")

        val workDir = File("/tmp/repository-analyzer")
        val repoDir = File(workDir, "${request.owner}-${request.repo}")

        val alreadyExists = repoDir.exists() && repoDir.isDirectory && File(repoDir, ".git").exists()

        if (alreadyExists) {
            logger.info("Repository already exists at: ${repoDir.absolutePath}")
            storage.set(alreadyExistedKey, true)
            storage.set(repositoryPathKey, repoDir.absolutePath)
        } else {
            logger.info("Repository not found, will clone")
            storage.set(alreadyExistedKey, false)
        }

        request
    }

/**
 * Node: Clone repository
 *
 * Clones the repository if it doesn't exist already.
 */
private fun AIAgentSubgraphBuilderBase<ValidatedRequest, SetupResult>.nodeCloneRepository() =
    node<ValidatedRequest, String>("clone-repository") { request ->
        val alreadyExists = storage.get(alreadyExistedKey) ?: false

        if (alreadyExists) {
            val existingPath = storage.get(repositoryPathKey)!!
            logger.info("Reusing existing repository at: $existingPath")
            return@node existingPath
        }

        // Create work directory
        val workDir = File("/tmp/repository-analyzer")
        if (!workDir.exists()) {
            workDir.mkdirs()
            logger.info("Created work directory: ${workDir.absolutePath}")
        }

        val repoDir = File(workDir, "${request.owner}-${request.repo}")
        val repositoryUrl = request.githubUrl

        // Clone repository
        val clonedPath = cloneRepository(
            repositoryUrl = repositoryUrl,
            targetDirectory = repoDir.absolutePath
        ).getOrElse { error ->
            logger.error("Failed to clone repository: ${error.message}")
            throw IllegalStateException("Failed to clone repository: ${error.message}", error)
        }

        storage.set(repositoryPathKey, clonedPath)
        logger.info("Repository cloned successfully to: $clonedPath")

        clonedPath
    }

/**
 * Node: Verify clone and detect default branch
 *
 * Verifies that the clone was successful and detects the default branch.
 */
private suspend fun AIAgentSubgraphBuilderBase<ValidatedRequest, SetupResult>.nodeVerifyClone() =
    node<String, SetupResult>("verify-clone") { repositoryPath ->
        val owner = storage.get(ownerKey)!!
        val repo = storage.get(repoKey)!!
        val alreadyExisted = storage.get(alreadyExistedKey) ?: false

        logger.info("Verifying repository clone at: $repositoryPath")

        // Verify .git directory exists
        val gitDir = File(repositoryPath, ".git")
        if (!gitDir.exists()) {
            throw IllegalStateException("Repository clone verification failed: .git directory not found")
        }

        // Detect default branch
        logger.info("Detecting default branch for $owner/$repo")

        val httpClient = HttpClient()
        val defaultBranch = getDefaultBranchFromGitHub(httpClient, owner, repo) ?: run {
            logger.warn("Failed to get default branch from GitHub API, using local current branch")
            getCurrentBranch(repositoryPath).getOrElse {
                logger.warn("Failed to get current branch, defaulting to 'main'")
                "main"
            }
        }
        httpClient.close()

        storage.set(defaultBranchKey, defaultBranch)
        logger.info("Default branch: $defaultBranch")

        val setupResult = SetupResult(
            repositoryPath = repositoryPath,
            defaultBranch = defaultBranch,
            owner = owner,
            repo = repo,
            alreadyExisted = alreadyExisted
        )

        storage.set(setupResultKey, setupResult)
        logger.info("Repository setup completed successfully")

        setupResult
    }
