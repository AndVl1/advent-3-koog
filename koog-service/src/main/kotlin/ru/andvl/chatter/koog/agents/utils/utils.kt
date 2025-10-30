package ru.andvl.chatter.koog.agents.utils

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase

internal suspend fun AIAgentContext.getLatestTokenUsage(): Int = llm.readSession { prompt.latestTokenUsage }

internal suspend fun AIAgentContext.isHistoryTooLong(): Boolean = llm.readSession { getLatestTokenUsage() > FIXING_MAX_CONTEXT_LENGTH * 0.8 }
    .also { println("TOKEN USAGE: ${getLatestTokenUsage()}, MAX CONTEXT: ${FIXING_MAX_CONTEXT_LENGTH * 0.8}") }

internal const val FIXING_MAX_CONTEXT_LENGTH: Long = 50_000L

internal inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeLLMCPrintCompressedHistory(
    name: String? = null,
): AIAgentNodeDelegate<T, T> = node(name) { input ->
    llm.readSession {
        println("COMPRESSED PROMPT: $prompt")
    }

    input
}
