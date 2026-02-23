package ru.andvl.chatter.app.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.andvl.chatter.app.models.AnalysisConfig
import ru.andvl.chatter.app.models.LLMProvider
import ru.andvl.chatter.app.repository.GithubAnalysisRepository
import ru.andvl.chatter.shared.models.github.AnalysisEventOrResult
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse

class GithubAnalysisInteractor(
    private val repository: GithubAnalysisRepository = GithubAnalysisRepository()
) {

    suspend fun analyzeRepository(config: AnalysisConfig): Result<GithubAnalysisResponse> {
        val validationError = validateConfig(config)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        return repository.analyzeGithubRepository(config)
    }

    fun analyzeRepositoryWithEvents(config: AnalysisConfig): Flow<AnalysisEventOrResult> {
        val validationError = validateConfig(config)
        if (validationError != null) {
            return flow {
                emit(AnalysisEventOrResult.Error(message = validationError, stackTrace = null))
            }
        }
        return repository.analyzeGithubRepositoryWithEvents(config)
    }

    private fun validateConfig(config: AnalysisConfig): String? {
        return when {
            config.userInput.isBlank() ->
                "Input cannot be empty. Provide GitHub repository URL and task description."
            config.apiKey.isBlank() && config.llmProvider != LLMProvider.OLLAMA ->
                "API Key is required. Please enter your ${config.llmProvider.displayName} API key to proceed."
            config.selectedModel.isBlank() && config.llmProvider != LLMProvider.CUSTOM ->
                "Model selection is required"
            config.llmProvider.requiresCustomUrl && config.customBaseUrl.isNullOrBlank() ->
                "Base URL is required for custom provider"
            config.llmProvider.requiresCustomUrl && config.customModel.isNullOrBlank() ->
                "Model name is required for custom provider"
            else -> null
        }
    }
}
