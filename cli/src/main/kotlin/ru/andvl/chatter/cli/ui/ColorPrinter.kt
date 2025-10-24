package ru.andvl.chatter.cli.ui

import ru.andvl.chatter.cli.models.CheckListItem
import ru.andvl.chatter.cli.models.StructuredResponse
import ru.andvl.chatter.cli.models.TokenUsageDto
import ru.andvl.chatter.shared.models.MessageRole
import ru.andvl.chatter.shared.models.SharedMessage

object ColorPrinter {
    private const val RESET = "\u001B[0m"
    private const val GREEN = "\u001B[32m"
    private const val WHITE = "\u001B[37m"
    private const val YELLOW = "\u001B[33m"
    private const val BLUE = "\u001B[34m"
    private const val CYAN = "\u001B[36m"
    private const val RED = "\u001B[31m"

    fun printResponse(response: StructuredResponse) {
        // Print title in green
        println("$GREEN${response.title}$RESET")

        // Print message in white
        println("$WHITE${response.message}$RESET")

        // Print checklist if present
        if (response.checkList.isNotEmpty()) {
            printChecklist(response.checkList)
        }
    }

    private fun printChecklist(checklist: List<CheckListItem>) {
        println("\n$YELLOW📋 Checklist:$RESET")
        checklist.forEachIndexed { index, item ->
            val status = if (item.resolution != null) "✅" else "❓"
            val color = if (item.resolution != null) GREEN else YELLOW
            println("  $color${index + 1}. ${item.point}$RESET")
            if (item.resolution != null) {
                println("     $CYAN→ ${item.resolution}$RESET")
            }
        }
        println()
    }

    fun printHistory(history: List<SharedMessage>) {
        if (history.isEmpty()) {
            println("$YELLOW📝 No history yet. Start a conversation!$RESET")
        } else {
            println("\n$CYAN📜 Conversation History:$RESET")
            history.forEachIndexed { index, message ->
                val role = when (message.role) {
                    MessageRole.User -> "You"
                    MessageRole.Assistant -> "AI"
                    else -> "System"
                }
                val color = when (message.role) {
                    MessageRole.User -> BLUE
                    MessageRole.Assistant -> GREEN
                    else -> YELLOW
                }
                val content = message.content
                val truncated = if (content.length > 100) "$content..." else content
                println("  $color$role: $truncated$RESET")
            }
        }
    }

    fun printError(message: String) {
        println("$RED❌ Error: $message$RESET")
    }

    fun printErrorDetails(details: String) {
        println("$WHITE Details: $details$RESET")
    }

    fun printConnectionError(message: String, baseUrl: String) {
        println("$RED❌ Failed to connect to server: $message$RESET")
        println("$WHITE Make sure the server is running at $baseUrl$RESET")
    }

    fun printSending(message: String) {
        println("\n📝 Sending: $message")
        print("🤖 AI: ")
    }

    fun printSendingWithContext(message: String, historySize: Int) {
        println("\n📝 Sending: $message ${if (historySize > 0) "(with $historySize messages in context)" else ""}")
        print("🤖 AI: ")
    }

    fun printWelcome(baseUrl: String) {
        println("\n🚀 Chatter CLI - Interactive Mode")
        println("📍 Server: $baseUrl")
        println("💡 Type 'exit' or 'quit' to exit")
        println("💡 Type 'help' for commands")
        println("💡 Type 'clear' to clear history")
        println("💡 Type 'history' to show conversation history")
        println("----------------------------------------")
    }

    fun printHelp() {
        println("\n📚 Available commands:")
        println("  help    - Show this help message")
        println("  exit    - Exit the program")
        println("  quit    - Exit the program")
        println("  clear   - Clear conversation history")
        println("  history - Show conversation history")
        println("\n💡 Just type any message to chat with AI!")
    }

    fun printHistoryCleared() {
        println("$YELLOW✨ History cleared!$RESET")
    }

    fun printGoodbye() {
        println("👋 Goodbye!")
    }

    fun printPrompt() {
        print("\n💬 You: ")
    }

    fun printChecklistUpdate(previousChecklist: List<CheckListItem>, newChecklist: List<CheckListItem>) {
        if (previousChecklist.isEmpty() && newChecklist.isNotEmpty()) {
            println("$CYAN📋 Checklist created with ${newChecklist.size} items$RESET")
        } else if (previousChecklist.isNotEmpty() && newChecklist.isNotEmpty()) {
            val resolvedCount = newChecklist.count { it.resolution != null }
            val previousResolvedCount = previousChecklist.count { it.resolution != null }

            if (resolvedCount > previousResolvedCount) {
                val newlyResolved = resolvedCount - previousResolvedCount
                println("$GREEN✅ $newlyResolved item(s) resolved in checklist ($resolvedCount/${newChecklist.size} total)$RESET")
            }
        }
    }

    fun printTokenUsage(usage: TokenUsageDto) {
        println("$CYAN📊 Tokens: ${usage.promptTokens} prompt + ${usage.completionTokens} completion = ${usage.totalTokens} total$RESET")
    }
}