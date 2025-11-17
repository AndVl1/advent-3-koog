package ru.andvl.chatter.desktop.models

import ru.andvl.chatter.shared.models.github.AnalysisEvent
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
    // Max context configuration
    val customMaxContextTokens: Long = 50_000L,
    val customFixingMaxContextTokens: Long = 20_000L,
    // Fixing model configuration (для исправления ошибок парсинга)
    val useMainModelForFixing: Boolean = true,
    val fixingModel: String = LLMProvider.OPEN_ROUTER.defaultModel,
    // Google Sheets integration
    val attachGoogleSheets: Boolean = false,
    val googleSheetsUrl: String = "",
    // Docker integration
    val forceSkipDocker: Boolean = true,
    // Embeddings integration
    val enableEmbeddings: Boolean = false,
    val isLoading: Boolean = false,
    val analysisResult: GithubAnalysisResponse? = null,
    val error: String? = null,
    // Streaming analysis events
    val currentEvent: AnalysisEvent? = null,
    val analysisProgress: Int = 0,
    val currentStep: Int = 0,         // Текущий шаг анализа
    val totalSteps: Int = 6,          // Всего шагов
    val currentStepName: String = "", // Название текущего шага
    val recentEvents: List<AnalysisEvent> = emptyList()
)

/**
 * LLM Provider selection
 */
enum class LLMProvider(
    val displayName: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val requiresCustomUrl: Boolean = false,
    val modelMaxContextTokens: Map<String, Long> = emptyMap(),
    val defaultMaxContextTokens: Long = 100_000L
) {
    OPEN_ROUTER(
        "OpenRouter",
        "z-ai/glm-4.6",
        listOf(
            "z-ai/glm-4.6",
            "qwen/qwen3-coder",
            "google/gemini-2.5-flash",
            "z-ai/glm-4.5-air",
            "z-ai/glm-4.5-air:free",
            "deepseek/deepseek-chat-v3-0324:free",
            "deepseek/deepseek-chat",
            "openai/gpt-5-nano",
        ),
        modelMaxContextTokens = mapOf(
            "z-ai/glm-4.6" to 100_000L,
            "qwen/qwen3-coder" to 200_000L,
            "google/gemini-2.5-flash" to 1_000_000L,
            "z-ai/glm-4.5-air" to 100_000L,
            "z-ai/glm-4.5-air:free" to 100_000L,
            "deepseek/deepseek-chat-v3-0324:free" to 64_000L,
            "deepseek/deepseek-chat" to 64_000L,
            "openai/gpt-5-nano" to 50_000L,
        ),
        defaultMaxContextTokens = 100_000L
    ),
    GOOGLE(
        "Gemini",
        "gemini-2.5-flash",
        listOf(
            "gemini-2.5-flash"
        ),
        modelMaxContextTokens = mapOf(
            "gemini-2.5-flash" to 1_000_000L
        ),
        defaultMaxContextTokens = 1_000_000L
    ),
    CUSTOM(
        "Custom",
        "",
        emptyList(),
        requiresCustomUrl = true,
        defaultMaxContextTokens = 100_000L
    );

    fun getMaxContextTokens(modelId: String): Long {
        return modelMaxContextTokens[modelId] ?: defaultMaxContextTokens
    }

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
    val maxContextTokens: Long,
    val fixingMaxContextTokens: Long,
    val useMainModelForFixing: Boolean = true,
    val fixingModel: String,
    val attachGoogleSheets: Boolean = false,
    val googleSheetsUrl: String = "",
    val forceSkipDocker: Boolean = true,
    val enableEmbeddings: Boolean = false
)
