package ru.andvl.chatter.desktop.repository

import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.andvl.chatter.desktop.models.AnalysisConfig
import ru.andvl.chatter.desktop.models.LLMProvider
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
                    userMessage = "${config.githubUrl} ${config.userRequest}".trim()
                )

                val response = koogService.analyseGithub(promptExecutor, request)
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
                simpleOpenRouterExecutor(
                    apiKey = config.apiKey.takeIf { it.isNotBlank() }
                        ?: System.getenv("OPENROUTER_API_KEY")
                        ?: throw IllegalArgumentException("OpenRouter API key is required")
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
        }
    }
}
