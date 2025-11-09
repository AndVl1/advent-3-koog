package ru.andvl.chatter.koog.agents.utils

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.message.Message
import ru.andvl.chatter.koog.model.common.TokenUsage

internal suspend fun AIAgentContext.getLatestTokenUsage(): Int = llm.readSession { prompt.latestTokenUsage }

internal suspend fun AIAgentContext.getLatestTotalTokenUsage(): TokenUsage? = llm.readSession {
    prompt.messages
        .lastOrNull { it is Message.Response }
        ?.let { it as? Message.Response }
        ?.metaInfo
        ?.let {
            TokenUsage(
                promptTokens = it.inputTokensCount ?: 0,
                completionTokens = it.outputTokensCount ?: 0,
                totalTokens = it.totalTokensCount ?: 0,
            )
        }
}

internal suspend fun AIAgentContext.isHistoryTooLong(): Boolean {
    val maxContextLength = llm.readSession { model.contextLength }
    val currentUsage = getLatestTokenUsage()
    val threshold = maxContextLength * 0.8
    return llm.readSession { currentUsage > threshold }
        .also { println("TOKEN USAGE: $currentUsage, MAX CONTEXT: $threshold (${maxContextLength} total)") }
}

internal const val FIXING_MAX_CONTEXT_LENGTH: Long = 20_000L

internal inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeLLMCPrintCompressedHistory(
    name: String? = null,
): AIAgentNodeDelegate<T, T> = node(name) { input ->
    llm.readSession {
        println("COMPRESSED PROMPT: $prompt")
    }

    input
}
