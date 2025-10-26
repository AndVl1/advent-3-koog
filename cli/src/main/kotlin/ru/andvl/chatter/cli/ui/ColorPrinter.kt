package ru.andvl.chatter.cli.ui

import ru.andvl.chatter.cli.models.CheckListItem
import ru.andvl.chatter.cli.models.StructuredResponse
import ru.andvl.chatter.cli.models.TokenUsageDto
import ru.andvl.chatter.cli.utils.TextWrapper
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
        // Print title in green with proper wrapping
        val wrappedTitle = TextWrapper.wrap(response.title, 80)
        println("$GREEN$wrappedTitle$RESET")

        // Print message in white with proper wrapping  
        val wrappedMessage = TextWrapper.wrap(response.message, 80)
        println("$WHITE$wrappedMessage$RESET")

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
            
            // Wrap checklist point with proper indentation
            val wrappedPoint = TextWrapper.wrapWithIndent("${index + 1}. ${item.point}", "  ", 78)
            println("$color$wrappedPoint$RESET")
            
            if (item.resolution != null) {
                // Wrap resolution with more indentation
                val wrappedResolution = TextWrapper.wrapWithIndent("→ ${item.resolution}", "     ", 75)
                println("$CYAN$wrappedResolution$RESET")
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
                val truncated = if (content.length > 100) {
                    TextWrapper.wrap("${content.take(97)}...", 76)
                } else {
                    TextWrapper.wrap(content, 76)
                }
                val wrappedContent = TextWrapper.wrapWithIndent("$role: $truncated", "  ", 78)
                println("$color$wrappedContent$RESET")
            }
        }
    }

    fun printError(message: String) {
        val wrappedMessage = TextWrapper.wrap("❌ Error: $message", 80)
        println("$RED$wrappedMessage$RESET")
    }

    fun printErrorDetails(details: String) {
        val wrappedDetails = TextWrapper.wrap("Details: $details", 80)
        println("$WHITE$wrappedDetails$RESET")
    }

    fun printConnectionError(message: String, baseUrl: String) {
        val wrappedError = TextWrapper.wrap("❌ Failed to connect to server: $message", 80)
        val wrappedSuggestion = TextWrapper.wrap("Make sure the server is running at $baseUrl", 80)
        println("$RED$wrappedError$RESET")
        println("$WHITE$wrappedSuggestion$RESET")
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

    fun printSlashCommandHelp() {
        println("$CYAN💡 Tip: Type '/' followed by command name (e.g., /help, /clear)$RESET")
        println("$CYAN💡 Use Tab for auto-completion when available$RESET")
        println("$CYAN💡 Available: /help, /clear, /history, /stats, /test-tokens, /exit$RESET")
        println()
    }

    fun printHelp() {
        println("\n📚 Available commands:")
        println("  ${CYAN}Slash commands:$RESET")
        println("    /help       - Show this help message")
        println("    /clear      - Clear conversation history")
        println("    /history    - Show conversation history")
        println("    /stats      - Show token usage statistics")
        println("    /test-tokens - Run token usage tests")
        println("    /exit       - Exit the application")
        println("\n  ${CYAN}Legacy commands:$RESET")
        println("    help, clear, history, exit, quit")
        println("\n$CYAN💡 Pro tip: Type '/' and press Tab for auto-completion!$RESET")
        println("💡 Just type any message to chat with AI!")
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