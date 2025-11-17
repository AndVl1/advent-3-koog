package ru.andvl.chatter.desktop.repository

import ai.koog.prompt.cache.files.FilePromptCache
import ai.koog.prompt.executor.cached.CachedPromptExecutor
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryablePattern
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import ru.andvl.chatter.desktop.models.AnalysisConfig
import ru.andvl.chatter.desktop.models.LLMProvider
import ru.andvl.chatter.desktop.utils.customOpenAICompatibleClient
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.shared.models.github.AnalysisEventOrResult
import ru.andvl.chatter.shared.models.github.GithubAnalysisRequest
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse
import ru.andvl.chatter.shared.models.github.LLMConfig
import kotlin.io.path.Path

/**
 * Repository for GitHub analysis operations
 * Wraps KoogService and provides LLM executor creation
 */
class GithubAnalysisRepository {

    private val koogService = KoogService()

    /**
     * Analyze GitHub repository using configured LLM
     */
    suspend fun analyzeGithubRepository(config: AnalysisConfig): Result<GithubAnalysisResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val promptExecutor = createPromptExecutor(config)
                val request = GithubAnalysisRequest(
                    userMessage = config.userInput,
                    googleSheetsUrl = if (config.attachGoogleSheets && config.googleSheetsUrl.isNotBlank()) {
                        config.googleSheetsUrl
                    } else null,
                    forceSkipDocker = config.forceSkipDocker,
                    enableEmbeddings = config.enableEmbeddings
                )

                val llmConfig = LLMConfig(
                    provider = config.llmProvider.name,
                    model = config.selectedModel,
                    apiKey = config.apiKey,
                    baseUrl = config.customBaseUrl,
                    fixingModel = config.fixingModel,
                    maxContextTokens = config.maxContextTokens,
                    fixingMaxContextTokens = config.fixingMaxContextTokens
                )

                val response = koogService.analyseGithub(promptExecutor, request, llmConfig)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Analyze GitHub repository with streaming events
     */
    fun analyzeGithubRepositoryWithEvents(config: AnalysisConfig): Flow<AnalysisEventOrResult> {
        val promptExecutor = createPromptExecutor(config)
        val request = GithubAnalysisRequest(
            userMessage = config.userInput,
            googleSheetsUrl = if (config.attachGoogleSheets && config.googleSheetsUrl.isNotBlank()) {
                config.googleSheetsUrl
            } else null,
            forceSkipDocker = config.forceSkipDocker,
            enableEmbeddings = config.enableEmbeddings
        )

        val llmConfig = LLMConfig(
            provider = config.llmProvider.name,
            model = config.selectedModel,
            apiKey = config.apiKey,
            baseUrl = config.customBaseUrl,
            fixingModel = config.fixingModel,
            maxContextTokens = config.maxContextTokens,
            fixingMaxContextTokens = config.fixingMaxContextTokens
        )

        return koogService.analyseGithubWithEvents(promptExecutor, request, llmConfig)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Create PromptExecutor based on selected LLM provider
     */
    private fun createPromptExecutor(config: AnalysisConfig): PromptExecutor {
        val timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 120_000,
            connectTimeoutMillis = 10_000
        )
        val baseClient = when (config.llmProvider) {
            LLMProvider.OPEN_ROUTER -> {
                // API key validation happens in Interactor, so config.apiKey is guaranteed to be non-blank
                OpenRouterLLMClient(
                    apiKey = config.apiKey,
                    settings = OpenRouterClientSettings(
                        timeoutConfig = timeoutConfig
                    )
                )
            }
            LLMProvider.GOOGLE -> {
                GoogleLLMClient(config.apiKey)
            }
            LLMProvider.CUSTOM -> {
                // For custom provider, use OpenAI-compatible executor with custom baseUrl
                // This works with any OpenAI-compatible API (vLLM, FastChat, LocalAI, LM Studio, etc.)
                customOpenAICompatibleClient(
                    apiKey = config.apiKey,
                    baseUrl = config.customBaseUrl ?: throw IllegalArgumentException("Base URL is required for custom provider"),
                    timeoutConfig = timeoutConfig
                )
            }
        }
        val resilientClient = RetryingLLMClient(
            delegate = baseClient,
            config = RetryConfig.CONSERVATIVE.copy(
                retryablePatterns = buildList {
                    addAll(RetryConfig.DEFAULT_PATTERNS)
                    add(RetryablePattern.Regex(Regex("Field \'.*\' is required for.*")))
                }
            )
        )
        val baseExecutor = SingleLLMPromptExecutor(resilientClient)
        val cachedExecutor = CachedPromptExecutor(
            cache = FilePromptCache(
                storage = Path("./memory/prompt_cache"),
                maxFiles = 10
            ),
            nested = baseExecutor
        )
        return cachedExecutor
    }
}
