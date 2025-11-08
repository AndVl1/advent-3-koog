package ru.andvl.chatter.desktop.repository

import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.andvl.chatter.desktop.models.AnalysisConfig
import ru.andvl.chatter.desktop.models.LLMProvider
import ru.andvl.chatter.desktop.utils.customOpenAICompatibleExecutor
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.shared.models.github.GithubAnalysisRequest
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse

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
                    } else null
                )

                val llmConfig = ru.andvl.chatter.shared.models.github.LLMConfig(
                    provider = config.llmProvider.name,
                    model = config.selectedModel,
                    apiKey = config.apiKey,
                    baseUrl = config.customBaseUrl,
                    fixingModel = config.fixingModel
                )

                val response = koogService.analyseGithub(promptExecutor, request, llmConfig)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Create PromptExecutor based on selected LLM provider
     */
    private fun createPromptExecutor(config: AnalysisConfig): PromptExecutor {
        return when (config.llmProvider) {
            LLMProvider.OPEN_ROUTER -> {
                // API key validation happens in Interactor, so config.apiKey is guaranteed to be non-blank
                simpleOpenRouterExecutor(
                    apiKey = config.apiKey
                )
            }
            LLMProvider.OPENAI -> {
                // TODO: Implement OpenAI executor
                throw NotImplementedError("OpenAI provider not yet implemented. Use OpenRouter for now.")
            }
            LLMProvider.ANTHROPIC -> {
                // TODO: Implement Anthropic executor
                throw NotImplementedError("Anthropic provider not yet implemented. Use OpenRouter for now.")
            }
            LLMProvider.CUSTOM -> {
                // For custom provider, use OpenAI-compatible executor with custom baseUrl
                // This works with any OpenAI-compatible API (vLLM, FastChat, LocalAI, LM Studio, etc.)
                customOpenAICompatibleExecutor(
                    apiKey = config.apiKey,
                    baseUrl = config.customBaseUrl ?: throw IllegalArgumentException("Base URL is required for custom provider")
                )
            }
        }
    }
}
