package ru.andvl.chatter.koog.agents.codemod

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryablePattern
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.model.codemod.CodeModificationRequest
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.koog.service.Provider
import java.io.File

/**
 * Test for Code Modification Agent
 *
 * This is a manual test class to verify the Code Modification Agent functionality.
 * Run this as a main function to test the agent with a real repository.
 *
 * Uses qwen/qwen-3-coder from OpenRouter as a cheap testing model.
 * Loads configuration from .env file.
 */
private val logger = LoggerFactory.getLogger("CodeModificationAgentTest")

fun main() = runBlocking {
    logger.info("=== Code Modification Agent Test ===")

    // Load environment variables from .env
    val dotenv = dotenv {
        ignoreIfMissing = true
    }

    // Get API key from dotenv or environment
    val apiKey = dotenv["OPENROUTER_API_KEY"] ?: System.getenv("OPENROUTER_API_KEY")
        .ifEmpty { null }
        ?: throw IllegalStateException("OPENROUTER_API_KEY not found in .env or environment variables")

    logger.info("API Key loaded: ${apiKey}...")
    logger.info("Creating PromptExecutor with qwen/qwen-3-coder model...")
    val promptExecutor = createPromptExecutor(apiKey)

    // Clean up cached repository before test
    logger.info("Cleaning up cached repository...")
    cleanupCachedRepository("AndVl1", "test-example")

    // Create test request (Docker environment will be detected automatically by LLM)
    val request = CodeModificationRequest(
        githubRepo = "https://github.com/AndVl1/vibetesting",
        userRequest = """
            Напиши Юнит-тесты на функциональность репозитория. Убедись в их работоспособности
        """.trimIndent(),
        dockerEnv = null, // Will be detected by LLM in Code Analysis phase
        enableEmbeddings = false
    )

    logger.info("Test Request:")
    logger.info("  Repository: ${request.githubRepo}")
    logger.info("  User Request: ${request.userRequest}")
    logger.info("  Docker Environment: Auto-detect by LLM")
    logger.info("  Enable Embeddings: ${request.enableEmbeddings}")
    logger.info("")

    try {
        logger.info("Starting Code Modification Agent...")
        val koogService = KoogService()

        val response = koogService.modifyCode(
            request = request,
            promptExecutor = promptExecutor,
            provider = Provider.OPENROUTER
        )

        logger.info("")
        logger.info("=== Results ===")
        logger.info("Success: ${response.success}")

        if (response.success) {
            logger.info("PR URL: ${response.prUrl}")
            logger.info("PR Number: ${response.prNumber}")
            logger.info("Branch Name: ${response.branchName}")
            logger.info("Commit SHA: ${response.commitSha}")
            logger.info("Files Modified: ${response.filesModified.size}")
            response.filesModified.forEach { file ->
                logger.info("  - $file")
            }
            logger.info("Verification Status: ${response.verificationStatus}")
            logger.info("Iterations Used: ${response.iterationsUsed}")
            logger.info("Message: ${response.message}")
        } else {
            logger.error("Error Message: ${response.errorMessage}")
            logger.error("Message: ${response.message}")
        }

        logger.info("")
        logger.info("=== Test Completed ===")

    } catch (e: Exception) {
        logger.error("Test failed with exception", e)
        throw e
    }
}

/**
 * Create PromptExecutor with OpenRouter using qwen/qwen-3-coder model
 * No caching for testing purposes
 */
private fun createPromptExecutor(apiKey: String): PromptExecutor {
    val timeoutConfig = ConnectionTimeoutConfig(
        requestTimeoutMillis = 300_000, // 5 minutes for code modification
        connectTimeoutMillis = 30_000   // 30 seconds connect timeout
    )

    val baseClient = OpenRouterLLMClient(
        apiKey = apiKey,
        settings = OpenRouterClientSettings(
            timeoutConfig = timeoutConfig
        )
    )

    val resilientClient = RetryingLLMClient(
        delegate = baseClient,
        config = RetryConfig.CONSERVATIVE.copy(
            retryablePatterns = buildList {
                addAll(RetryConfig.DEFAULT_PATTERNS)
                add(RetryablePattern.Regex(Regex("Field \\'.*\\' is required for.*")))
            }
        )
    )

    return SingleLLMPromptExecutor(resilientClient)
}

/**
 * Clean up cached repository directory
 * Removes the repository from /tmp/code-modifications to force fresh clone
 */
private fun cleanupCachedRepository(owner: String, repo: String) {
    val workDir = File("/tmp/code-modifications")
    val repoDir = File(workDir, "$owner-$repo")

    if (repoDir.exists()) {
        logger.info("Removing cached repository at: ${repoDir.absolutePath}")
        try {
            repoDir.deleteRecursively()
            logger.info("Successfully removed cached repository")
        } catch (e: Exception) {
            logger.warn("Failed to remove cached repository: ${e.message}", e)
        }
    } else {
        logger.info("No cached repository found at: ${repoDir.absolutePath}")
    }
}
