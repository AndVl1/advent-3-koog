package ru.andvl.chatter.shared.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Chat history container
 */
@Serializable
data class ChatHistory(
    val messages: List<SharedMessage> = emptyList(),
    val maxHistoryLength: Int = 100
) {
    fun addMessage(message: SharedMessage): ChatHistory {
        val updated = messages + message
        val limited = if (updated.size > maxHistoryLength) {
            updated.takeLast(maxHistoryLength)
        } else updated
        return copy(messages = limited)
    }

    fun getLastMessages(count: Int): List<SharedMessage> {
        return messages.takeLast(count)
    }
}

/**
 * Conversation state to maintain checklist across iterations
 */
@Serializable
data class ConversationState(
    val history: List<SharedMessage> = emptyList(),
    val activeChecklist: List<SharedCheckListItem> = emptyList(),
    val conversationId: String? = null,
    val lastUpdated: Instant = kotlinx.datetime.Clock.System.now()
) {
    fun addMessage(message: SharedMessage): ConversationState {
        return copy(
            history = history + message,
            lastUpdated = kotlinx.datetime.Clock.System.now()
        )
    }

    fun updateChecklist(newChecklist: List<SharedCheckListItem>): ConversationState {
        return copy(
            activeChecklist = newChecklist,
            lastUpdated = kotlinx.datetime.Clock.System.now()
        )
    }

    fun getRecentHistory(count: Int): List<SharedMessage> {
        return history.takeLast(count)
    }
}