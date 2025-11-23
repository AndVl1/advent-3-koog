package ru.andvl.chatter.codeagent.viewmodel

import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisResult

/**
 * UI state for Repository Analysis screen
 *
 * This state represents the complete UI state for the repository analysis feature.
 * It follows Clean Architecture principles by separating presentation state from domain models.
 *
 * @property githubUrl GitHub repository URL entered by user
 * @property isAnalyzing Whether analysis is currently in progress
 * @property progressMessage Current progress message during analysis
 * @property analysisResult Result of the analysis (null if not yet analyzed or error occurred)
 * @property error Error message if analysis failed (null if no error)
 * @property fileTreeExpansionState Map of file paths to their expansion state (true = expanded, false = collapsed)
 */
data class RepositoryAnalysisUiState(
    val githubUrl: String = "",
    val isAnalyzing: Boolean = false,
    val progressMessage: String = "",
    val analysisResult: RepositoryAnalysisResult? = null,
    val error: String? = null,
    val fileTreeExpansionState: Map<String, Boolean> = emptyMap()
) {
    /**
     * Returns true if there is a valid result to display
     */
    val hasResult: Boolean
        get() = analysisResult != null && error == null

    /**
     * Returns true if there is an error to display
     */
    val hasError: Boolean
        get() = error != null

    /**
     * Returns true if the analyze button should be enabled
     */
    val canAnalyze: Boolean
        get() = githubUrl.isNotBlank() && !isAnalyzing

    /**
     * Validates GitHub URL format
     */
    fun isValidGitHubUrl(): Boolean {
        if (githubUrl.isBlank()) return false
        val githubPattern = Regex("^https?://github\\.com/[\\w.-]+/[\\w.-]+/?$")
        return githubPattern.matches(githubUrl.trim())
    }
}
