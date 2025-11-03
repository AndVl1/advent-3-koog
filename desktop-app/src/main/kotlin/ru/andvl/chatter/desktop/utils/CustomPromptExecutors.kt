package ru.andvl.chatter.desktop.utils

import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor

/**
 * Creates a PromptExecutor for custom OpenAI-compatible APIs.
 *
 * This function allows connecting to any API that implements the OpenAI-compatible interface,
 * such as:
 * - vLLM
 * - FastChat
 * - LocalAI
 * - LM Studio
 * - Text Generation Inference
 * - Custom OpenAI-compatible endpoints
 *
 * @param apiKey The API key for authentication
 * @param baseUrl The base URL of the custom API endpoint (e.g., "https://my-api.com")
 * @return A PromptExecutor configured for the custom API
 *
 * Example usage:
 * ```kotlin
 * val executor = customOpenAICompatibleExecutor(
 *     apiKey = "your-api-key",
 *     baseUrl = "https://api.custom-llm.com"
 * )
 * ```
 */
fun customOpenAICompatibleExecutor(
    apiKey: String,
    baseUrl: String
): PromptExecutor {
    val settings = OpenAIClientSettings(baseUrl = baseUrl)
    return SingleLLMPromptExecutor(OpenAILLMClient(apiKey, settings))
}
