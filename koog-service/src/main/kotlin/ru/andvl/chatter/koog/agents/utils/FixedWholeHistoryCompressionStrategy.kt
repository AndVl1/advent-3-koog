package ru.andvl.chatter.koog.agents.utils

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.prompt.message.Message

internal class FixedWholeHistoryCompressionStrategy : HistoryCompressionStrategy() {

    override suspend fun compress(llmSession: AIAgentLLMWriteSession, memoryMessages: List<Message>) {
        val originalMessages = llmSession.prompt.messages
        val tldrMessages = compressPromptIntoTLDR(llmSession)
        val compressedMessages = composeMessageHistoryFixed(
            originalMessages,
            tldrMessages,
            memoryMessages,
        )
        llmSession.prompt = llmSession.prompt.withMessages { compressedMessages }
    }

    // copt from https://github.com/JetBrains/koog/pull/1067
    private fun composeMessageHistoryFixed(
        originalMessages: List<Message>,
        tldrMessages: List<Message>,
        memoryMessages: List<Message>,
    ): List<Message> {
        val messages = mutableListOf<Message>()
        // Leave all the system messages
        val systemMessages = originalMessages.filterIsInstance<Message.System>()
        messages.addAll(systemMessages)
        // Leave the first user message if present
        val firstUserMessage = originalMessages.firstOrNull { it is Message.User }
        firstUserMessage?.let { messages.add(it) }
        // Add the memory messages
        messages.addAll(memoryMessages)
        // Sort the messages by timestamp
        messages.sortWith { a, b -> a.metaInfo.timestamp.compareTo(b.metaInfo.timestamp) }
        // Add the tldr messages
        messages.addAll(tldrMessages)

        val trailingToolCalls = originalMessages.takeLastWhile { it is Message.Tool.Call }
        messages.addAll(trailingToolCalls)

        return messages
    }
}
