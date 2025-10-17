package ru.andvl.chatter.backend.dto

import kotlinx.serialization.Serializable
import ai.koog.prompt.message.Message
import ru.andvl.chatter.koog.model.StructuredResponse

/**
 * Request DTO for chat with context
 */
@Serializable
data class ChatRequestDto(
    val message: String,
    val history: List<Message> = emptyList(),
    val maxHistoryLength: Int = 10,
    val provider: String? = null // "google" or "openrouter"
)

/**
 * Response DTO for chat
 */
@Serializable
data class ChatResponseDto(
    val response: StructuredResponse,
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