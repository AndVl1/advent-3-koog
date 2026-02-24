package ru.andvl.chatter.app.utils

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor

fun customOpenAICompatibleExecutor(
    apiKey: String,
    baseUrl: String
): PromptExecutor {
    return SingleLLMPromptExecutor(customOpenAICompatibleClient(apiKey, baseUrl))
}

fun customOpenAICompatibleClient(
    apiKey: String,
    baseUrl: String,
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
): LLMClient {
    val settings = OpenAIClientSettings(
        baseUrl = baseUrl,
        timeoutConfig = timeoutConfig
    )
    return OpenAILLMClient(apiKey, settings)
}
