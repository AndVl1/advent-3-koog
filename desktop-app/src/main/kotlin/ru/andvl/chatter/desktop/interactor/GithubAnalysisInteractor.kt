package ru.andvl.chatter.desktop.interactor

import ru.andvl.chatter.desktop.models.AnalysisConfig
import ru.andvl.chatter.desktop.repository.GithubAnalysisRepository
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse

/**
 * Interactor for GitHub analysis business logic
 * Handles validation and coordinates repository calls
 */
class GithubAnalysisInteractor(
    private val repository: GithubAnalysisRepository = GithubAnalysisRepository()
) {

    /**
     * Execute GitHub repository analysis with validation
     */
    suspend fun analyzeRepository(config: AnalysisConfig): Result<GithubAnalysisResponse> {
        // Validate inputs
        val validationError = validateConfig(config)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }

        // Execute analysis through repository
        return repository.analyzeGithubRepository(config)
    }

    /**
     * Validate analysis configuration
     * @return error message if invalid, null if valid
     */
    private fun validateConfig(config: AnalysisConfig): String? {
        return when {
            config.userInput.isBlank() ->
                "Input cannot be empty. Provide GitHub repository URL and task description."

            config.apiKey.isBlank() ->
                "API Key is required. Please enter your ${config.llmProvider.displayName} API key to proceed."

            config.selectedModel.isBlank() ->
                "Model selection is required"

            config.llmProvider.requiresCustomUrl && config.customBaseUrl.isNullOrBlank() ->
                "Base URL is required for custom provider"

            config.llmProvider.requiresCustomUrl && config.customModel.isNullOrBlank() ->
                "Model name is required for custom provider"

            else -> null
        }
    }
}
