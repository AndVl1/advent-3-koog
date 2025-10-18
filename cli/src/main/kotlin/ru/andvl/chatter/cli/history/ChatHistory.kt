package ru.andvl.chatter.cli.history

import ru.andvl.chatter.cli.ui.ColorPrinter
import ru.andvl.chatter.shared.models.ConversationState
import ru.andvl.chatter.shared.models.MessageRole
import ru.andvl.chatter.shared.models.SharedCheckListItem
import ru.andvl.chatter.shared.models.SharedMessage
import java.util.*

class ChatHistory {
    private var conversationState = ConversationState(
        conversationId = UUID.randomUUID().toString()
    )
    private val maxHistorySize = 100 // Limit history size

    fun addMessage(message: SharedMessage) {
        conversationState = conversationState.addMessage(message)

        // Trim history if it gets too large
        if (conversationState.history.size > maxHistorySize) {
            val trimmedHistory = conversationState.history.takeLast(maxHistorySize)
            conversationState = conversationState.copy(history = trimmedHistory)
        }
    }

    // Convenience method for adding simple messages
    fun addMessage(role: String, content: String) {
        val messageRole = when (role.lowercase()) {
            "user" -> MessageRole.User
            "assistant" -> MessageRole.Assistant
            "system" -> MessageRole.System
            else -> MessageRole.User
        }

        addMessage(SharedMessage(
            role = messageRole,
            content = content,
            meta = ru.andvl.chatter.shared.models.MessageMeta()
        ))
    }

    fun updateChecklist(checklist: List<SharedCheckListItem>) {
        conversationState = conversationState.updateChecklist(checklist)
    }

    fun getConversationState(): ConversationState {
        return conversationState
    }

    fun getLastMessages(count: Int): List<SharedMessage> {
        return conversationState.getRecentHistory(count)
    }

    fun getAll(): List<SharedMessage> {
        return conversationState.history
    }

    fun getActiveChecklist(): List<SharedCheckListItem> {
        return conversationState.activeChecklist
    }

    fun clear() {
        conversationState = ConversationState(
            conversationId = UUID.randomUUID().toString()
        )
        ColorPrinter.printHistoryCleared()
    }

    fun display() {
        ColorPrinter.printHistory(conversationState.history)
    }

    fun isEmpty(): Boolean = conversationState.history.isEmpty()

    fun size(): Int = conversationState.history.size
}