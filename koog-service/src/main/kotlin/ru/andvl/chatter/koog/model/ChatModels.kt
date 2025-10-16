package ru.andvl.chatter.koog.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Simple message data class
 */
data class SimpleMessage(
    val role: String, // "user", "assistant", "system", "tool"
    val content: String
)

/**
 * Data class for chat requests with context
 */
data class ChatRequest(
    val message: String,
    val systemPrompt: String? = null,
    val history: List<SimpleMessage> = emptyList(),
    val maxHistoryLength: Int = 10 // Limit history to prevent token overflow
)

/**
 * Data class for chat responses
 */
data class ChatResponse(
    val response: StructuredResponse,
    val usage: TokenUsage? = null,
    val model: String? = null
)

/**
 * Token usage information
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

@LLMDescription("Simple structured LLM Response")
@Serializable
@SerialName("StructuredResponse")
data class StructuredResponse(
    @property:LLMDescription("Short title of what this dialog is about")
    val title: String,
    @property:LLMDescription("Full response message")
    val message: String,
)