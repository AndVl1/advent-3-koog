package ru.andvl.chatter.cli.interactive

import org.jline.reader.LineReaderBuilder
import org.jline.terminal.TerminalBuilder
import ru.andvl.chatter.cli.api.ChatApiClient
import ru.andvl.chatter.cli.history.ChatHistory
import ru.andvl.chatter.cli.ui.ColorPrinter

class InteractiveMode(
    private val client: ChatApiClient,
    private val baseUrl: String
) {
    private val history = ChatHistory()
    private var shouldExit = false

    suspend fun start() {
        ColorPrinter.printWelcome(baseUrl)
        ColorPrinter.printSlashCommandHelp()

        // Create terminal and line reader
        val terminal = TerminalBuilder.builder()
            .system(true)
            .build()

        val commands = SlashCommands.createCommands(
            onHelp = {
                ColorPrinter.printHelp()
            },
            onClear = {
                history.clear()
            },
            onHistory = {
                history.display()
                println("\n${history.getCompactTokenStats()}")
            },
            onExit = {
                // Show session statistics before exit
                println("\n📊 Session Summary:")
                println(history.getCompactTokenStats())
                ColorPrinter.printGoodbye()
                shouldExit = true
            },
            onTestTokens = {
                println("🧪 Token testing functionality coming soon!")
            },
            onStats = {
                history.displayTokenStatistics()
            }
        )

        val completer = SlashCommandCompleter(commands)
        val processor = SlashCommandProcessor(commands)

        val reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(completer)
            .build()

        reader.setOpt(org.jline.reader.LineReader.Option.DISABLE_EVENT_EXPANSION)
        reader.variable(org.jline.reader.LineReader.COMPLETION_STYLE_DESCRIPTION, true)
        reader.variable(org.jline.reader.LineReader.COMPLETION_STYLE_GROUP, false)

        try {
            while (!shouldExit) {
                try {
                    val input = reader.readLine("\n💬 You: ")?.trim() ?: continue

                    when {
                        input.isEmpty() -> continue
                        input.startsWith("/") -> {
                            // Process slash command
                            if (!processor.processCommand(input)) {
                                // If processor returns false, treat as regular message
                                client.sendMessage(baseUrl, input, history)
                            }
                        }
                        input.lowercase() in listOf("exit", "quit") -> {
                            ColorPrinter.printGoodbye()
                            shouldExit = true
                        }
                        input.lowercase() == "help" -> {
                            ColorPrinter.printHelp()
                        }
                        input.lowercase() == "clear" -> {
                            history.clear()
                            ColorPrinter.printHistoryCleared()
                        }
                        input.lowercase() == "history" -> {
                            history.display()
                        }
                        else -> {
                            // Send message with context
                            client.sendMessage(baseUrl, input, history)
                        }
                    }
                } catch (e: org.jline.reader.UserInterruptException) {
                    // Ctrl+C pressed
                    ColorPrinter.printGoodbye()
                    shouldExit = true
                } catch (e: org.jline.reader.EndOfFileException) {
                    // Ctrl+D pressed
                    ColorPrinter.printGoodbye()
                    shouldExit = true
                }
            }
        } finally {
            terminal.close()
        }
    }
}
