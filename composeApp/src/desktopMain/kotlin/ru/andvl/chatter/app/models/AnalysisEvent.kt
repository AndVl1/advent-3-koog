package ru.andvl.chatter.app.models

sealed interface AnalysisEvent {
    data class UpdateUserInput(val input: String) : AnalysisEvent
    data class UpdateApiKey(val apiKey: String) : AnalysisEvent
    data class SelectLLMProvider(val provider: LLMProvider) : AnalysisEvent
    data class SelectModel(val model: String) : AnalysisEvent
    data class UpdateCustomBaseUrl(val url: String) : AnalysisEvent
    data class UpdateCustomModel(val model: String) : AnalysisEvent
    data class UpdateCustomMaxContextTokens(val tokens: Long) : AnalysisEvent
    data class UpdateCustomFixingMaxContextTokens(val tokens: Long) : AnalysisEvent
    data class ToggleUseMainModelForFixing(val useMain: Boolean) : AnalysisEvent
    data class SelectFixingModel(val model: String) : AnalysisEvent
    data class ToggleAttachGoogleSheets(val attach: Boolean) : AnalysisEvent
    data class UpdateGoogleSheetsUrl(val url: String) : AnalysisEvent
    data class ToggleForceSkipDocker(val skip: Boolean) : AnalysisEvent
    data class ToggleEnableEmbeddings(val enable: Boolean) : AnalysisEvent
    data object StartAnalysis : AnalysisEvent
    data object CancelAnalysis : AnalysisEvent
    data object ClearError : AnalysisEvent
    data object ClearResult : AnalysisEvent
    data class SaveReport(val content: String) : AnalysisEvent
}
