package ru.andvl.chatter.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.runBlocking
import ru.andvl.chatter.cli.api.ChatApiClient
import ru.andvl.chatter.cli.history.ChatHistory
import ru.andvl.chatter.cli.interactive.InteractiveMode

fun main(args: Array<String>): Unit = runBlocking {
    val parser = ArgParser("chatter-cli")

    val host by parser.option(
        ArgType.String,
        shortName = "H",
        fullName = "host",
        description = "Server host"
    ).default("localhost")

    val port by parser.option(
        ArgType.Int,
        shortName = "p",
        fullName = "port",
        description = "Server port"
    ).default(8081)

    val message by parser.option(
        ArgType.String,
        shortName = "m",
        fullName = "message",
        description = "Message to send to AI"
    )

    val interactive by parser.option(
        ArgType.Boolean,
        shortName = "i",
        fullName = "interactive",
        description = "Run in interactive mode"
    ).default(false)

    parser.parse(args)

    val client = ChatApiClient()
    val baseUrl = "http://$host:$port"

    try {
        if (interactive) {
            val interactiveMode = InteractiveMode(client, baseUrl)
            interactiveMode.start()
        } else if (message != null) {
            val history = ChatHistory()
            client.sendMessage(baseUrl, message!!, history)
        } else {
            println("Please provide either --message or use --interactive mode")
            println("Use --help for more information")
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    } finally {
        client.close()
    }
}