package ru.andvl.chatter.koog.config

import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Configuration for Koog model selection
 *
 * This enum provides a centralized way to manage LLM model selection
 * for different providers and tasks.
 *
 * **Usage:**
 * ```kotlin
 * // Use default model for a provider
 * val model = KoogModelProvider.OPEN_ROUTER.createLLModel()
 *
 * // Use specific model
 * val model = KoogModelProvider.OPEN_ROUTER.createLLModel("z-ai/glm-4.5-air")
 *
 * // Get max context tokens
 * val maxTokens = KoogModelProvider.OPEN_ROUTER.getMaxContextTokens("qwen/qwen3-coder")
 * ```
 *
 * **Environment Variables:**
 * - KOOG_DEFAULT_MODEL: Override default model for main tasks
 * - KOOG_FIXING_MODEL: Override model used for error fixing/parsing
 */
enum class KoogModelProvider(
    val displayName: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val modelMaxContextTokens: Map<String, Long> = emptyMap(),
    val defaultMaxContextTokens: Long = 100_000L
) {
    /**
     * OpenRouter provider - supports multiple models
     *
     * **Minimum requirement:** qwen/qwen3-coder
     * **Maximum requirement:** z-ai/glm-4.5-air:free
     */
    OPEN_ROUTER(
        "OpenRouter",
        "qwen/qwen3-coder",  // Default model
        listOf(
            // Primary models (minimum/maximum requirements)
            "qwen/qwen3-coder",  // Minimum requirement - good for code
            "z-ai/glm-4.5-air:free",  // Maximum requirement - free tier

            // Additional models (paid/better versions)
            "z-ai/glm-4.5-air",  // Paid version of glm-4.5
            "z-ai/glm-4.6",  // Newer version

            // Alternative models
            "google/gemini-2.5-flash",  // For comparison
            "deepseek/deepseek-chat-v3-0324:free",  // Free DeepSeek
            "deepseek/deepseek-chat",  // Paid DeepSeek
            "openai/gpt-5-nano"  // GPT-5 nano
        ),
        modelMaxContextTokens = mapOf(
            "qwen/qwen3-coder" to 200_000L,
            "z-ai/glm-4.5-air:free" to 100_000L,
            "z-ai/glm-4.5-air" to 100_000L,
            "z-ai/glm-4.6" to 100_000L,
            "google/gemini-2.5-flash" to 1_000_000L,
            "deepseek/deepseek-chat-v3-0324:free" to 64_000L,
            "deepseek/deepseek-chat" to 64_000L,
            "openai/gpt-5-nano" to 50_000L
        ),
        defaultMaxContextTokens = 200_000L
    ),

    /**
     * Google Gemini provider
     */
    GOOGLE(
        "Gemini",
        "gemini-2.5-flash",
        listOf("gemini-2.5-flash"),
        modelMaxContextTokens = mapOf("gemini-2.5-flash" to 1_000_000L),
        defaultMaxContextTokens = 1_000_000L
    );

    /**
     * Get maximum context tokens for a specific model
     *
     * @param modelId The model identifier
     * @return Maximum context tokens, or default if not found
     */
    fun getMaxContextTokens(modelId: String): Long {
        return modelMaxContextTokens[modelId] ?: defaultMaxContextTokens
    }

    /**
     * Create LLModel instance for this provider
     *
     * @param modelId Model identifier (defaults to provider's default model)
     * @param capabilities Optional list of capabilities (auto-detected if not provided)
     * @return Configured LLModel instance
     */
    fun createLLModel(
        modelId: String = defaultModel,
        capabilities: List<LLMCapability>? = null
    ): LLModel {
        return when (this) {
            OPEN_ROUTER -> {
                val contextLength = getMaxContextTokens(modelId)
                LLModel(
                    provider = LLMProvider.OpenRouter,
                    id = modelId,
                    capabilities = capabilities ?: listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Completion,
                        LLMCapability.Tools,
                        LLMCapability.OpenAIEndpoint.Completions,
                        LLMCapability.MultipleChoices,
                    ),
                    contextLength = contextLength
                )
            }
            GOOGLE -> when (modelId) {
                "gemini-2.5-flash" -> GoogleModels.Gemini2_5Flash
                else -> GoogleModels.Gemini2_5Flash
            }
        }
    }

    /**
     * Create a simplified LLModel for tasks that don't need all capabilities
     * (e.g., error fixing, parsing, simple completion)
     *
     * @param modelId Model identifier (defaults to provider's default model)
     * @return LLModel with basic capabilities only
     */
    fun createSimpleLLModel(modelId: String = defaultModel): LLModel {
        return when (this) {
            OPEN_ROUTER -> {
                val contextLength = getMaxContextTokens(modelId)
                LLModel(
                    provider = LLMProvider.OpenRouter,
                    id = modelId,
                    capabilities = listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Completion,
                    ),
                    contextLength = contextLength
                )
            }
            GOOGLE -> GoogleModels.Gemini2_5Flash
        }
    }

    companion object {
        /**
         * Map Provider enum to KoogModelProvider
         */
        fun fromProvider(provider: ru.andvl.chatter.koog.service.Provider?): KoogModelProvider {
            return when (provider) {
                ru.andvl.chatter.koog.service.Provider.GOOGLE -> GOOGLE
                ru.andvl.chatter.koog.service.Provider.OPENROUTER -> OPEN_ROUTER
                null -> OPEN_ROUTER
            }
        }
    }
}
