package ru.andvl.mcp.github

import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

fun main() = runBlocking {
    val dotenv = dotenv { ignoreIfMissing = true }
    val token: String? = dotenv["GITHUB_TOKEN"]
    println("🧪 Тестирование GitHub клиента...")

    val httpTransport = StreamableHttpClientTransport(
        client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            install(SSE)
        },
        url = "https://api.githubcopilot.com/mcp",
        reconnectionTime = 15.seconds,
        requestBuilder = {
            token?.let {
                header("Authorization", "Bearer $it")
            } ?: error("No Authorization header found")
        },
    )

    val client = Client(
        clientInfo = Implementation(name = "github", version = "1.0.0"),
    )

    try {
        client.connect(httpTransport)
    } catch (e: Exception) {
        println(e)
        return@runBlocking
    }

    val toolsList = client.listTools()?.tools?.map { it.name }
    println("Available Tools = $toolsList")
    val result = client.callTool(
        name = "get_me",
        arguments = mapOf()
    )
        ?.content?.map { if (it is TextContent) it.text else it.toString() }

    println(result?.joinToString())

    client.close()

    println("\n✅ Тестирование завершено")
}
