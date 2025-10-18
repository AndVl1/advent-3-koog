package ru.andvl.chatter.cli.interactive

import ru.andvl.chatter.cli.api.ChatApiClient
import ru.andvl.chatter.cli.history.ChatHistory
import ru.andvl.chatter.cli.ui.ColorPrinter
import java.util.*

class InteractiveMode(
    private val client: ChatApiClient,
    private val baseUrl: String
) {
    private val scanner = Scanner(System.`in`)
    private val history = ChatHistory()
    
    suspend fun start() {
        ColorPrinter.printWelcome(baseUrl)
        
        while (true) {
            ColorPrinter.printPrompt()
            val input = scanner.nextLine()?.trim() ?: continue
            
            when (input.lowercase()) {
                "exit", "quit" -> {
                    ColorPrinter.printGoodbye()
                    break
                }
                "help" -> {
                    ColorPrinter.printHelp()
                }
                "clear" -> {
                    history.clear()
                }
                "history" -> {
                    history.display()
                }
                "" -> continue
                else -> {
                    // Send message with context
                    client.sendMessage(baseUrl, input, history)
                }
            }
        }
    }
}