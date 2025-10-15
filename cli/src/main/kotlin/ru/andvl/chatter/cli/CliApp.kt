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

suspend fun sendMessage(client: HttpClient, baseUrl: String, message: String) {
    println("\n📝 Sending: $message")
    print("🤖 AI: ")
    
    try {
        val response = client.post("$baseUrl/ai/chat") {
            contentType(ContentType.Text.Plain)
            setBody(message)
        }
        
        if (response.status == HttpStatusCode.OK) {
            val responseBody = response.body<String>()
            println(responseBody)
        } else {
            println("❌ Error: ${response.status}")
            val errorBody = response.body<String>()
            println("Details: $errorBody")
        }
    } catch (e: Exception) {
        println("❌ Failed to connect to server: ${e.message}")
        println("Make sure the server is running at $baseUrl")
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