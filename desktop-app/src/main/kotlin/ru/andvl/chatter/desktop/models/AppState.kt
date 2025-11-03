package ru.andvl.chatter.desktop.models

import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse

/**
 * Application UI state
 */
data class AppState(
    val githubUrl: String = "",
    val userRequest: String = "",
    val apiKey: String = "",
    val llmProvider: LLMProvider = LLMProvider.OPEN_ROUTER,
    val isLoading: Boolean = false,
    val analysisResult: GithubAnalysisResponse? = null,
    val error: String? = null
)

/**
 * LLM Provider selection
 */
enum class LLMProvider(val displayName: String, val defaultModel: String) {
    OPEN_ROUTER("OpenRouter", "z-ai/glm-4.6"),
    OPENAI("OpenAI", "gpt-4"),
    ANTHROPIC("Anthropic", "claude-3-sonnet-20240229");

    companion object {
        fun fromDisplayName(name: String): LLMProvider =
            entries.find { it.displayName == name } ?: OPEN_ROUTER
    }
}

/**
 * Analysis request configuration
 */
data class AnalysisConfig(
    val githubUrl: String,
    val userRequest: String,
    val apiKey: String,
    val llmProvider: LLMProvider
)
