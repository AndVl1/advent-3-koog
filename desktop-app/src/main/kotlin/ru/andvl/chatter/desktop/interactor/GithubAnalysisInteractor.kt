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
            config.githubUrl.isBlank() -> "GitHub URL cannot be empty"
            !isValidGithubUrl(config.githubUrl) -> "Invalid GitHub URL format. Expected: github.com/owner/repo"
            config.apiKey.isBlank() -> "API Key is required"
            else -> null
        }
    }

    /**
     * Check if URL is a valid GitHub repository URL
     */
    private fun isValidGithubUrl(url: String): Boolean {
        val githubPattern = Regex(
            """^(https?://)?(www\.)?github\.com/[\w.-]+/[\w.-]+/?$""",
            RegexOption.IGNORE_CASE
        )
        return githubPattern.matches(url) || url.matches(Regex("""^[\w.-]+/[\w.-]+$"""))
    }
}
