package ru.andvl.chatter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking
import ru.andvl.chatter.cli.api.ChatApiClient
import ru.andvl.chatter.cli.history.ChatHistory
import ru.andvl.chatter.cli.interactive.InteractiveMode

class ChatterCli : CliktCommand(
    name = "chatter-cli"
) {
    private val host by option("-H", "--host")
        .help("Server host")
        .default("localhost")

    private val port by option("-p", "--port")
        .int()
        .help("Server port")
        .default(8081)

    private val message by option("-m", "--message")
        .help("Message to send to AI")

    private val interactive by option("-i", "--interactive")
        .flag()
        .help("Run in interactive mode")

    private val testTokens by option("-t", "--test-tokens")
        .flag()
        .help("Run token usage tests")

    override fun run() {
        runBlocking {
        val client = ChatApiClient()
        val baseUrl = "http://$host:$port"

        try {
            when {
                testTokens -> {
                    echo("🧪 Running token usage tests...")
                    // TODO: Implement token testing when TokenTester is ready
                    echo("Token testing functionality coming soon!")
                }
                interactive -> {
                    val interactiveMode = InteractiveMode(client, baseUrl)
                    interactiveMode.start()
                }
                message != null -> {
                    val history = ChatHistory()
                    client.sendMessage(baseUrl, message!!, history)
                }
                else -> {
                    echo("Please provide either --message or use --interactive mode", err = true)
                    echo("Use --help for more information", err = true)
                }
            }
        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)
        } finally {
            client.close()
        }
        }
    }
}

fun main(args: Array<String>) = ChatterCli().main(args)