package ru.andvl.chatter.desktop.models

/**
 * User actions in the application
 */
sealed interface GithubAnalysisAction {
    // Navigation
    data class NavigateToScreen(val screen: AppScreen) : GithubAnalysisAction

    // Chat actions
    data class UpdateChatInput(val input: String) : GithubAnalysisAction
    data class SendChatMessage(val text: String) : GithubAnalysisAction
    data object StartVoiceRecording : GithubAnalysisAction
    data object StopVoiceRecording : GithubAnalysisAction
    data object SendVoiceMessage : GithubAnalysisAction
    data class PlayVoiceMessage(val messageId: String) : GithubAnalysisAction
    data class PauseVoiceMessage(val messageId: String) : GithubAnalysisAction
    data class StopVoicePlayback(val messageId: String) : GithubAnalysisAction
    data object ClearChat : GithubAnalysisAction
    data object ClearChatError : GithubAnalysisAction

    // Chat configuration actions
    data class SelectChatProvider(val provider: ChatProvider) : GithubAnalysisAction
    data class UpdateChatModel(val model: String) : GithubAnalysisAction
    data class UpdateChatApiKey(val apiKey: String) : GithubAnalysisAction
    data class ToggleChatHistorySaving(val enabled: Boolean) : GithubAnalysisAction

    /**
     * User changed input text (combined github URL + request)
     */
    data class UpdateUserInput(val input: String) : GithubAnalysisAction

    /**
     * User changed API key
     */
    data class UpdateApiKey(val apiKey: String) : GithubAnalysisAction

    /**
     * User selected LLM provider
     */
    data class SelectLLMProvider(val provider: LLMProvider) : GithubAnalysisAction

    /**
     * User selected a model from dropdown
     */
    data class SelectModel(val model: String) : GithubAnalysisAction

    /**
     * User changed custom base URL (for Custom provider)
     */
    data class UpdateCustomBaseUrl(val url: String) : GithubAnalysisAction

    /**
     * User changed custom model name (for Custom provider)
     */
    data class UpdateCustomModel(val model: String) : GithubAnalysisAction

    /**
     * User changed custom max context tokens (for Custom provider)
     */
    data class UpdateCustomMaxContextTokens(val maxContextTokens: Long) : GithubAnalysisAction

    /**
     * User changed custom fixing max context tokens (for Custom provider)
     */
    data class UpdateCustomFixingMaxContextTokens(val fixingMaxContextTokens: Long) : GithubAnalysisAction

    /**
     * Toggle between using main model or separate model for fixing
     */
    data class ToggleUseMainModelForFixing(val useMain: Boolean) : GithubAnalysisAction

    /**
     * User selected a fixing model from dropdown
     */
    data class SelectFixingModel(val model: String) : GithubAnalysisAction

    /**
     * Toggle Google Sheets attachment
     */
    data class ToggleAttachGoogleSheets(val attach: Boolean) : GithubAnalysisAction

    /**
     * User changed Google Sheets URL
     */
    data class UpdateGoogleSheetsUrl(val url: String) : GithubAnalysisAction

    /**
     * Toggle force skip Docker build
     */
    data class ToggleForceSkipDocker(val forceSkip: Boolean) : GithubAnalysisAction

    /**
     * Toggle embeddings generation
     */
    data class ToggleEnableEmbeddings(val enable: Boolean) : GithubAnalysisAction

    /**
     * User clicked "Analyze" button
     */
    data object StartAnalysis : GithubAnalysisAction

    /**
     * User clicked "Cancel" button to stop analysis
     */
    data object CancelAnalysis : GithubAnalysisAction

    /**
     * Clear error message
     */
    data object ClearError : GithubAnalysisAction

    /**
     * Clear analysis result
     */
    data object ClearResult : GithubAnalysisAction
}
