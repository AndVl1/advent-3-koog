package ru.andvl.chatter.koog.agents.utils

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

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
            LLMCapability.OpenAIEndpoint.Completions,
        ),
        contextLength = FIXING_MAX_CONTEXT_LENGTH,
    )
}
