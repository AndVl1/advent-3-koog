package ru.andvl.chatter.desktop.models

/**
 * User actions in the application
 */
sealed interface GithubAnalysisAction {
    /**
     * User changed GitHub URL input
     */
    data class UpdateGithubUrl(val url: String) : GithubAnalysisAction

    /**
     * User changed request text
     */
    data class UpdateUserRequest(val request: String) : GithubAnalysisAction

    /**
     * User changed API key
     */
    data class UpdateApiKey(val apiKey: String) : GithubAnalysisAction

    /**
     * User selected LLM provider
     */
    data class SelectLLMProvider(val provider: LLMProvider) : GithubAnalysisAction

    /**
     * User clicked "Analyze" button
     */
    data object StartAnalysis : GithubAnalysisAction

    /**
     * Clear error message
     */
    data object ClearError : GithubAnalysisAction

    /**
     * Clear analysis result
     */
    data object ClearResult : GithubAnalysisAction
}
