package ru.andvl.chatter.cli.models

import kotlinx.serialization.Serializable
import ru.andvl.chatter.shared.models.ConversationState
import ru.andvl.chatter.shared.models.SharedCheckListItem
import ru.andvl.chatter.shared.models.SharedStructuredResponse

typealias StructuredResponse = SharedStructuredResponse
typealias CheckListItem = SharedCheckListItem

@Serializable
data class ChatContextRequest(
    val message: String,
    val conversationState: ConversationState? = null, // Include conversation state for checklist persistence
    val maxHistoryLength: Int = 20,
    val provider: String? = null,
    val systemPrompt: String? = null
)

/**
 * Response DTO for chat - matches server's ChatResponseDto
 */
@Serializable
data class ChatResponseDto(
    val response: SharedStructuredResponse,
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

/**
 * GitHub analysis request DTO
 */
@Serializable
data class GithubAnalysisRequest(
    val userMessage: String
)

/**
 * GitHub analysis response DTO
 */
@Serializable
data class GithubAnalysisResponse(
    val analysis: String,
    val toolCalls: List<String>,
    val model: String? = null,
    val usage: GithubTokenUsageDto? = null
)

/**
 * GitHub Token usage DTO
 */
@Serializable
data class GithubTokenUsageDto(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)