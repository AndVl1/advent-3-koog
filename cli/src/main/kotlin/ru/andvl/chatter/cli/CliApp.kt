package ru.andvl.chatter.cli

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.cli.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.util.*

fun main(args: Array<String>) = runBlocking {
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

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    val baseUrl = "http://$host:$port"

    try {
        if (interactive == true) {
            runInteractiveMode(client, baseUrl)
        } else if (message != null) {
            sendMessage(client, baseUrl, message!!)
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

@Serializable
data class StructuredResponse(
    val title: String,
    val message: String,
    val checklistItems: List<ChecklistItem>
)

@Serializable
@SerialName("ChecklistItem")
data class ChecklistItem(
    @SerialName("point")
    val point: String,
    @SerialName("resolution")
    val resolution: String?,
)

suspend fun sendMessage(client: HttpClient, baseUrl: String, message: String) {
    println("\n📝 Sending: $message")
    print("🤖 AI: ")

    try {
        val response = client.post("$baseUrl/ai/chat") {
            contentType(ContentType.Application.Json)
            setBody(message)
        }

        if (response.status == HttpStatusCode.OK) {

            try {
                val responseBody = response.body<StructuredResponse>()
                // Print title in green
                println("\u001B[32m${responseBody.title}\u001B[0m")

                // Print message in white
                println("\u001B[37m${responseBody.message}\u001B[0m")
            } catch (e: Exception) {
                // Fallback to plain text if not JSON
                println(response.body<String>())
            }
        } else {
            println("\u001B[31m❌ Error: ${response.status}\u001B[0m")
            val errorBody = response.body<String>()
            println("\u001B[37mDetails: $errorBody\u001B[0m")
        }
    } catch (e: Exception) {
        println("\u001B[31m❌ Failed to connect to server: ${e.message}\u001B[0m")
        println("\u001B[37mMake sure the server is running at $baseUrl\u001B[0m")
    }

    println()
}

suspend fun runInteractiveMode(client: HttpClient, baseUrl: String) {
    val scanner = Scanner(System.`in`)

    println("\n🚀 Chatter CLI - Interactive Mode")
    println("📍 Server: $baseUrl")
    println("💡 Type 'exit' or 'quit' to exit")
    println("💡 Type 'help' for commands")
    println("----------------------------------------")

    while (true) {
        print("\n💬 You: ")
        val input = scanner.nextLine()?.trim() ?: continue

        when (input.lowercase()) {
            "exit", "quit" -> {
                println("👋 Goodbye!")
                break
            }
            "help" -> {
                println("\n📚 Available commands:")
                println("  help  - Show this help message")
                println("  exit  - Exit the program")
                println("  quit  - Exit the program")
                println("\n💡 Just type any message to chat with AI!")
            }
            "" -> continue
            else -> {
                sendMessage(client, baseUrl, input)
            }
        }
    }
}