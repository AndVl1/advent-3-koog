package ru.andvl.chatter.cli.history

import ru.andvl.chatter.cli.statistics.TokenStatistics
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
    // Note: No client-side history limits - compression handled by backend
    private val tokenStatistics = TokenStatistics()

    fun addMessage(message: SharedMessage) {
        conversationState = conversationState.addMessage(message)
        // Note: Context compression/trimming is handled by the backend
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
        tokenStatistics.recordHistoryCleared()
        ColorPrinter.printHistoryCleared()
    }

    fun display() {
        ColorPrinter.printHistory(conversationState.history)
    }

    fun isEmpty(): Boolean = conversationState.history.isEmpty()

    fun size(): Int = conversationState.history.size
    
    /**
     * Add token usage statistics from a request
     */
    fun addTokenUsage(usage: ru.andvl.chatter.cli.models.TokenUsageDto) {
        tokenStatistics.addUsage(usage)
    }
    
    /**
     * Get token statistics
     */
    fun getTokenStatistics(): TokenStatistics = tokenStatistics
    
    /**
     * Display token statistics
     */
    fun displayTokenStatistics() {
        println(tokenStatistics.getSummary())
    }
    
    /**
     * Get compact token statistics for status display
     */
    fun getCompactTokenStats(): String = tokenStatistics.getCompactSummary()
}