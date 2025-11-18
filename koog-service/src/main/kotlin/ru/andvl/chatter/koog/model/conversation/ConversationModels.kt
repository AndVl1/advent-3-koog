package ru.andvl.chatter.koog.model.conversation

import ai.koog.prompt.message.Message
import ru.andvl.chatter.koog.model.common.TokenUsage

/**
 * Data class for conversation requests
 */
data class ConversationRequest(
    val message: String,
    val systemPrompt: String? = null,
    val history: List<Message> = emptyList(),
    val maxHistoryLength: Int = 10,
    val audioFilePath: String? = null
)

/**
 * Data class for conversation responses
 */
data class ConversationResponse(
    val text: String,
    val originalMessage: Message.Assistant? = null,
    val usage: TokenUsage? = null,
    val model: String? = null
)
