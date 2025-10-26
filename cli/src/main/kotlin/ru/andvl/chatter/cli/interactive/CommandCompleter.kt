package ru.andvl.chatter.cli.interactive

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

/**
 * Slash command definition with execution
 */
data class SlashCommand(
    val name: String,
    val description: String,
    val action: suspend () -> Unit
)

/**
 * JLine completer for slash commands
 */
class SlashCommandCompleter(
    private val commands: List<SlashCommand>
) : Completer {
    
    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        val word = line.word()
        
        // Only complete if line starts with /
        if (line.line().startsWith("/")) {
            val prefix = word.removePrefix("/")
            
            commands
                .filter { it.name.startsWith(prefix, ignoreCase = true) }
                .forEach { command ->
                    candidates.add(
                        Candidate(
                            "/${command.name}",
                            command.name,
                            null,
                            command.description,
                            null,
                            null,
                            true
                        )
                    )
                }
        }
    }
}

/**
 * Command processor for handling slash commands
 */
class SlashCommandProcessor(
    private val commands: List<SlashCommand>
) {
    
    /**
     * Check if input is a slash command and execute it
     * @return true if command was executed, false if input should be sent to backend
     */
    suspend fun processCommand(input: String): Boolean {
        if (!input.startsWith("/")) {
            return false
        }
        
        val commandName = input.removePrefix("/").trim()
        if (commandName.isEmpty()) {
            return false
        }
        
        val command = commands.find { it.name.equals(commandName, ignoreCase = true) }
        if (command != null) {
            println("\n✅ Executing: /${command.name}")
            command.action()
            return true
        } else {
            println("\n❌ Unknown command: /$commandName")
            println("Available commands: ${commands.joinToString(", ") { "/${it.name}" }}")
            return true
        }
    }
    
    /**
     * Get available commands for display
     */
    fun getAvailableCommands(): List<SlashCommand> = commands
}

/**
 * Factory for creating slash commands
 */
object SlashCommands {
    
    fun createCommands(
        onHelp: suspend () -> Unit,
        onClear: suspend () -> Unit,
        onHistory: suspend () -> Unit,
        onExit: suspend () -> Unit,
        onTestTokens: suspend () -> Unit,
        onStats: suspend () -> Unit
    ): List<SlashCommand> {
        return listOf(
            SlashCommand(
                name = "help",
                description = "Show available commands and help information",
                action = onHelp
            ),
            SlashCommand(
                name = "clear",
                description = "Clear conversation history",
                action = onClear
            ),
            SlashCommand(
                name = "history", 
                description = "Show conversation history",
                action = onHistory
            ),
            SlashCommand(
                name = "test-tokens",
                description = "Run token usage tests",
                action = onTestTokens
            ),
            SlashCommand(
                name = "stats",
                description = "Show token usage statistics for this session",
                action = onStats
            ),
            SlashCommand(
                name = "exit",
                description = "Exit the application",
                action = onExit
            )
        )
    }
}