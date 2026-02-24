package ru.andvl.chatter.app.repository

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
import ai.koog.prompt.executor.ollama.client.OllamaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import ru.andvl.chatter.app.models.AnalysisConfig
import ru.andvl.chatter.app.models.LLMProvider
import ru.andvl.chatter.app.platform.FileSaver
import ru.andvl.chatter.app.utils.customOpenAICompatibleClient
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.shared.models.github.AnalysisEventOrResult
import ru.andvl.chatter.shared.models.github.GithubAnalysisRequest
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse
import ru.andvl.chatter.shared.models.github.LLMConfig
import kotlin.io.path.Path

class GithubAnalysisRepository {

    private val koogService = KoogService(logsDir = FileSaver.getLogsDir())

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

    private fun createPromptExecutor(config: AnalysisConfig): PromptExecutor {
        val timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 120_000,
            connectTimeoutMillis = 10_000
        )
        val baseClient = when (config.llmProvider) {
            LLMProvider.OPEN_ROUTER -> {
                OpenRouterLLMClient(
                    apiKey = config.apiKey,
                    settings = OpenRouterClientSettings(timeoutConfig = timeoutConfig)
                )
            }
            LLMProvider.GOOGLE -> {
                GoogleLLMClient(config.apiKey)
            }
            LLMProvider.OLLAMA -> {
                OllamaClient(baseUrl = "http://localhost:11434")
            }
            LLMProvider.CUSTOM -> {
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
