package ru.andvl.chatter.koog.agents.utils

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Storage key for fixing LLModel
 */
val fixingModelKey = createStorageKey<LLModel>("fixing-model")

/**
 * Thread-local holder for current fixing model
 * This allows passing fixing model to subgraphs without modifying request structure
 */
object FixingModelHolder {
    private val threadLocal = ThreadLocal<LLModel?>()

    fun set(model: LLModel) {
        threadLocal.set(model)
    }

    fun get(): LLModel {
        return threadLocal.get() ?: createFixingModel("OPEN_ROUTER", "z-ai/glm-4.6")
    }

    fun clear() {
        threadLocal.remove()
    }
}

/**
 * Create fixing LLModel based on provider and model ID
 */
fun createFixingModel(provider: String, modelId: String): LLModel {
    val llmProvider = when (provider.uppercase()) {
        "OPEN_ROUTER", "OPENROUTER" -> LLMProvider.OpenRouter
        "OPENAI" -> LLMProvider.OpenAI
        "ANTHROPIC" -> LLMProvider.Anthropic
        "CUSTOM" -> LLMProvider.OpenRouter  // Use OpenRouter-compatible for custom providers
        else -> LLMProvider.OpenRouter
    }

    return LLModel(
        provider = llmProvider,
        id = modelId,
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Completion,
        ),
        contextLength = FIXING_MAX_CONTEXT_LENGTH,
    )
}
