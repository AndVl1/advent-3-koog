package ru.andvl.chatter.koog.model.structured

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.message.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.andvl.chatter.shared.models.SharedCheckListItem

/**
 * Data class for chat requests with context
 */
data class ChatRequest(
    val message: String,
    val systemPrompt: String? = null,
    val history: List<Message> = emptyList(),
    val maxHistoryLength: Int = 10, // Limit history to prevent token overflow
    val currentChecklist: List<SharedCheckListItem> = emptyList() // Current checklist for context
)

/**
 * Data class for chat responses
 */
data class ChatResponse(
    val response: StructuredResponse,
    val originalMessage: Message.Assistant?,
    val usage: TokenUsage? = null,
    val model: String? = null,
    val mcpCalls: List<String> = emptyList(),
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
    @SerialName("title")
    val title: String,
    @property:LLMDescription("Full response message")
    @SerialName("message")
    val message: String,
    @property:LLMDescription("Users question checklist. Use it when needed to specify details from user. Try to make it no longer then 10 point")
    val checkList: List<CheckListItem> = emptyList(),
)

@LLMDescription("Chat checklist item to collect requirements for users task")
@Serializable
@SerialName("ChecklistItem")
data class CheckListItem(
    @property:LLMDescription("Checklist point. Points are created to answer to ask maximum details from user")
    @SerialName("point")
    val point: String,
    @property:LLMDescription("Checklist resolution from users answer. Null if need to specify from user. Should not be " +
            "changed after user gave info")
    @SerialName("resolution")
    val resolution: String?
)
