package ru.andvl.chatter.codeagent.interactor

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryablePattern
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.cdimascio.dotenv.Dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.andvl.chatter.codeagent.repository.RepositoryAnalysisRepository
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.koog.service.KoogServiceFactory
import ru.andvl.chatter.shared.models.codeagent.AnalysisType
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisResult
import java.io.File

/**
 * Business logic layer for Repository Analysis
 *
 * This interactor contains all business logic for repository analysis feature.
 * It coordinates between Repository layer and ViewModel.
 * It follows Clean Architecture principles by keeping business logic separate from UI.
 *
 * Dependencies are created internally for simplicity in this standalone desktop app.
 */
class RepositoryAnalysisInteractor {
    private val logger = KotlinLogging.logger {}

    private val koogService: KoogService by lazy {
        KoogServiceFactory.createFromEnv()
    }

    private val repository: RepositoryAnalysisRepository by lazy {
        RepositoryAnalysisRepository(koogService)
    }

    private val dotenv: Dotenv by lazy {
        // Load .env from project root
        val projectRoot = File(System.getProperty("user.dir"))
        Dotenv.configure()
            .directory(projectRoot.absolutePath)
            .ignoreIfMissing()
            .load()
    }

    /**
     * Create PromptExecutor for Qwen3-Coder model from .env configuration
     */
    private fun createPromptExecutor(): PromptExecutor {
        val apiKey = dotenv["OPENROUTER_API_KEY"]
            ?: throw IllegalStateException("OPENROUTER_API_KEY not found in .env")

        logger.info { "Creating PromptExecutor with OpenRouter (qwen/qwen3-coder)" }

        val timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 120_000, // 2 minutes
            connectTimeoutMillis = 10_000   // 10 seconds
        )

        val settings = OpenRouterClientSettings(
            timeoutConfig = timeoutConfig
        )

        val baseClient = OpenRouterLLMClient(
            apiKey = apiKey,
            settings = settings
        )

        // Add resilience with retry logic
        val resilientClient = RetryingLLMClient(
            delegate = baseClient,
            config = RetryConfig.CONSERVATIVE.copy(
                retryablePatterns = RetryConfig.DEFAULT_PATTERNS
            )
        )

        return SingleLLMPromptExecutor(resilientClient)
    }

    /**
     * Validate GitHub URL format
     */
    fun validateGitHubUrl(url: String): Result<String> {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("GitHub URL cannot be empty"))
        }

        val githubPattern = Regex("^https?://github\\.com/[\\w.-]+/[\\w.-]+/?$")
        if (!githubPattern.matches(trimmedUrl)) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid GitHub URL format. Expected: https://github.com/owner/repo"
                )
            )
        }

        return Result.success(trimmedUrl)
    }

    /**
     * Analyze GitHub repository
     *
     * This method:
     * - Validates GitHub URL
     * - Creates PromptExecutor from .env
     * - Calls repository layer to perform analysis
     * - Returns result or error
     *
     * @param githubUrl GitHub repository URL
     * @param analysisType Type of analysis (STRUCTURE, DEPENDENCIES, FULL)
     * @param enableEmbeddings Whether to enable embeddings for RAG
     * @return Result with RepositoryAnalysisResult or error
     */
    suspend fun analyzeRepository(
        githubUrl: String,
        analysisType: AnalysisType = AnalysisType.STRUCTURE,
        enableEmbeddings: Boolean = false
    ): Result<RepositoryAnalysisResult> {
        // Validate URL
        val validationResult = validateGitHubUrl(githubUrl)
        if (validationResult.isFailure) {
            return Result.failure(validationResult.exceptionOrNull()!!)
        }

        val validatedUrl = validationResult.getOrThrow()

        // Create PromptExecutor
        val promptExecutor = try {
            createPromptExecutor()
        } catch (e: Exception) {
            logger.error(e) { "Failed to create PromptExecutor" }
            return Result.failure(
                IllegalStateException("Failed to initialize AI model: ${e.message}", e)
            )
        }

        // Perform analysis
        return repository.analyzeRepository(
            githubUrl = validatedUrl,
            analysisType = analysisType,
            enableEmbeddings = enableEmbeddings,
            promptExecutor = promptExecutor
        )
    }
}
