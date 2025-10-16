package ru.andvl.chatter.backend.dto

import kotlinx.serialization.Serializable
import ai.koog.prompt.message.Message
import ru.andvl.chatter.koog.model.ChatResponse

/**
 * Request DTO for chat with context
 */
@Serializable
data class ChatRequestDto(
    val message: String,
    val systemPrompt: String? = null,
    val history: List<MessageDto> = emptyList(),
    val maxHistoryLength: Int = 10,
    val provider: String? = null // "google" or "openrouter"
)

/**
 * DTO for message in history
 */
@Serializable
data class MessageDto(
    val role: String, // "user", "assistant", "system"
    val content: String
)

/**
 * Response DTO for chat
 */
@Serializable
data class ChatResponseDto(
    val response: String,
    val model: String? = null,
    val usage: TokenUsageDto? = null
)

/**
 * Token usage DTO
 */
@Serializable
data class TokenUsageDto(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)