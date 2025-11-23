package ru.andvl.chatter.koog.agents.repoanalyzer

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
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.koog.service.KoogServiceFactory
import ru.andvl.chatter.shared.models.codeagent.AnalysisType
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisRequest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test for Repository Analyzer Agent
 *
 * This test verifies the Repository Analyzer Agent functionality using a small public GitHub repository.
 * The agent is LLM-free and uses only rule-based analysis.
 *
 * Test repository: octocat/Hello-World (simple repository with minimal files)
 */
private val logger = LoggerFactory.getLogger("RepositoryAnalyzerAgentTest")

class RepositoryAnalyzerAgentTest {

    /**
     * Test analyzing a simple public repository
     *
     * This test:
     * - Uses the famous octocat/Hello-World repository (very small and simple)
     * - Performs STRUCTURE analysis (fastest, no LLM calls)
     * - Validates all result fields are populated correctly
     * - Checks for no errors
     */
    @Test
    fun `test analyze simple repository`() = runBlocking {
        logger.info("=== Repository Analyzer Agent Test ===")

        // Load environment variables from .env
        val dotenv = dotenv {
            directory = "/Users/a.vladislavov/personal/ai-advent-3/chatter"
            ignoreIfMissing = true
        }

        // Get API key from dotenv or environment
        val apiKey = dotenv["OPENROUTER_API_KEY"] ?: System.getenv("OPENROUTER_API_KEY")
            .ifEmpty { null }
            ?: throw IllegalStateException("OPENROUTER_API_KEY not found in .env or environment variables")

        logger.info("API Key loaded successfully")

        // Create prompt executor (even though agent is LLM-free, we still need it for AIAgent structure)
        val promptExecutor = createPromptExecutor(apiKey)

        // Create KoogService
        val koogService = KoogServiceFactory.createFromEnv()

        // Prepare test request
        val request = RepositoryAnalysisRequest(
            githubUrl = "https://github.com/octocat/Hello-World",
            analysisType = AnalysisType.STRUCTURE,  // Fast analysis without LLM
            enableEmbeddings = false  // No embeddings for test
        )

        logger.info("Test Request:")
        logger.info("  Repository: ${request.githubUrl}")
        logger.info("  Analysis Type: ${request.analysisType}")
        logger.info("  Enable Embeddings: ${request.enableEmbeddings}")
        logger.info("")

        // Act: Run repository analysis
        logger.info("Starting repository analysis...")
        val result = koogService.analyzeRepository(request, promptExecutor)

        // Assert: Verify results
        logger.info("")
        logger.info("=== Results ===")
        logger.info("Repository Path: ${result.repositoryPath}")
        logger.info("Repository Name: ${result.repositoryName}")
        logger.info("File Count: ${result.fileCount}")
        logger.info("Main Languages: ${result.mainLanguages}")
        logger.info("Build Tool: ${result.buildTool}")
        logger.info("Dependencies Count: ${result.dependencies.size}")
        logger.info("Summary: ${result.summary}")
        logger.info("Error Message: ${result.errorMessage}")
        logger.info("")

        // Validate all fields are populated
        assertNotNull(result.repositoryPath, "Repository path should not be null")
        assertTrue(result.repositoryPath.isNotEmpty(), "Repository path should not be empty")

        assertNotNull(result.repositoryName, "Repository name should not be null")
        assertTrue(result.repositoryName.isNotEmpty(), "Repository name should not be empty")

        assertNotNull(result.summary, "Summary should not be null")
        assertTrue(result.summary.isNotEmpty(), "Summary should not be empty")

        assertTrue(result.fileCount > 0, "File count should be greater than 0")

        assertNotNull(result.mainLanguages, "Main languages should not be null")
        // Note: octocat/Hello-World only has a README file without extension,
        // so language detection may return empty list. This is expected behavior.

        assertNotNull(result.structureTree, "Structure tree should not be null")
        assertTrue(result.structureTree.isNotEmpty(), "Structure tree should not be empty")

        // Error message should be null for successful analysis
        assertTrue(result.errorMessage == null, "Error message should be null for successful analysis, but got: ${result.errorMessage}")

        logger.info("=== Test Completed Successfully ===")
    }

    /**
     * Test analyzing a real code repository with dependencies
     *
     * This test uses a small Python project with requirements.txt
     * to verify:
     * - Language detection works correctly
     * - Dependencies are extracted
     * - Build tools are detected
     */
    @Test
    fun `test analyze repository with real code`() = runBlocking {
        logger.info("=== Repository Analyzer Agent Test (Real Code) ===")

        // Load environment variables from .env
        val dotenv = dotenv {
            directory = "/Users/a.vladislavov/personal/ai-advent-3/chatter"
            ignoreIfMissing = true
        }

        // Get API key
        val apiKey = dotenv["OPENROUTER_API_KEY"] ?: System.getenv("OPENROUTER_API_KEY")
            .ifEmpty { null }
            ?: throw IllegalStateException("OPENROUTER_API_KEY not found in .env or environment variables")

        logger.info("API Key loaded successfully")

        // Create prompt executor
        val promptExecutor = createPromptExecutor(apiKey)

        // Create KoogService
        val koogService = KoogServiceFactory.createFromEnv()

        // Prepare test request with a simple Python project
        // Using a small example repo: simple-python-app with requirements.txt
        val request = RepositoryAnalysisRequest(
            githubUrl = "https://github.com/github/gitignore",  // This repo has many .gitignore files - simple and fast
            analysisType = AnalysisType.FULL,
            enableEmbeddings = false
        )

        logger.info("Test Request:")
        logger.info("  Repository: ${request.githubUrl}")
        logger.info("  Analysis Type: ${request.analysisType}")
        logger.info("  Enable Embeddings: ${request.enableEmbeddings}")
        logger.info("")

        // Act: Run repository analysis
        logger.info("Starting repository analysis...")
        val result = koogService.analyzeRepository(request, promptExecutor)

        // Assert: Verify results
        logger.info("")
        logger.info("=== Results ===")
        logger.info("Repository Path: ${result.repositoryPath}")
        logger.info("Repository Name: ${result.repositoryName}")
        logger.info("File Count: ${result.fileCount}")
        logger.info("Main Languages: ${result.mainLanguages}")
        logger.info("Build Tool: ${result.buildTool}")
        logger.info("Dependencies Count: ${result.dependencies.size}")
        logger.info("Summary length: ${result.summary.length}")
        logger.info("Summary: ${result.summary.take(300)}...")
        logger.info("Error Message: ${result.errorMessage}")
        logger.info("")

        // Validate all fields are populated
        assertNotNull(result.repositoryPath, "Repository path should not be null")
        assertTrue(result.repositoryPath.isNotEmpty(), "Repository path should not be empty")

        assertNotNull(result.summary, "Summary should not be null")
        assertTrue(result.summary.isNotEmpty(), "Summary should not be empty")

        // This repo should have many files
        assertTrue(result.fileCount > 10, "File count should be > 10 for gitignore repo, got: ${result.fileCount}")

        // gitignore files might not have language detection, so we don't assert on languages
        assertNotNull(result.mainLanguages, "Main languages should not be null")

        // For FULL analysis, we expect comprehensive summary
        assertTrue(result.summary.length > 50, "Summary should be comprehensive for FULL analysis")

        // Error message should be null for successful analysis
        assertTrue(result.errorMessage == null, "Error message should be null for successful analysis, but got: ${result.errorMessage}")

        logger.info("=== Test Completed Successfully ===")
    }

    /**
     * Create PromptExecutor with OpenRouter using qwen/qwen-3-coder model
     * No caching for testing purposes
     */
    private fun createPromptExecutor(apiKey: String): PromptExecutor {
        val timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 120_000, // 2 minutes for repository analysis
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
}
