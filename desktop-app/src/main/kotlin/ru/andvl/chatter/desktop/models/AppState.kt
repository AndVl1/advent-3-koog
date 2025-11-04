package ru.andvl.chatter.desktop.models

import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse

/**
 * Application UI state
 */
data class AppState(
    val userInput: String = "",
    val apiKey: String = "",
    val llmProvider: LLMProvider = LLMProvider.OPEN_ROUTER,
    val selectedModel: String = LLMProvider.OPEN_ROUTER.defaultModel,
    val customBaseUrl: String = "",
    val customModel: String = "",
    // Fixing model configuration (для исправления ошибок парсинга)
    val useMainModelForFixing: Boolean = true,
    val fixingModel: String = LLMProvider.OPEN_ROUTER.defaultModel,
    val isLoading: Boolean = false,
    val analysisResult: GithubAnalysisResponse? = null,
    val error: String? = null
)

/**
 * LLM Provider selection
 */
enum class LLMProvider(
    val displayName: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val requiresCustomUrl: Boolean = false
) {
    OPEN_ROUTER(
        "OpenRouter",
        "z-ai/glm-4.6",
        listOf(
            "z-ai/glm-4.6",
            "qwen/qwen3-coder",
            "z-ai/glm-4.5-air",
            "deepseek/deepseek-chat",
            "openai/gpt-5-nano",
        )
    ),
    OPENAI(
        "OpenAI",
        "gpt-4",
        listOf("gpt-4", "gpt-4-turbo", "gpt-3.5-turbo")
    ),
    ANTHROPIC(
        "Anthropic",
        "claude-3-5-sonnet-20241022",
        listOf(
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229"
        )
    ),
    CUSTOM(
        "Custom",
        "",
        emptyList(),
        requiresCustomUrl = true
    );

    companion object {
        fun fromDisplayName(name: String): LLMProvider =
            entries.find { it.displayName == name } ?: OPEN_ROUTER
    }
}

/**
 * Analysis request configuration
 */
data class AnalysisConfig(
    val userInput: String,
    val apiKey: String,
    val llmProvider: LLMProvider,
    val selectedModel: String,
    val customBaseUrl: String? = null,
    val customModel: String? = null,
    val useMainModelForFixing: Boolean = true,
    val fixingModel: String
)
