package ru.andvl.chatter.cli.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.andvl.chatter.cli.history.ChatHistory
import ru.andvl.chatter.cli.models.*
import ru.andvl.chatter.cli.ui.ColorPrinter

class ChatApiClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
        }

        install(Logging) {
            level = LogLevel.BODY
        }

        install(SSE)
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendMessage(
        baseUrl: String,
        message: String,
        history: ChatHistory
    ): StructuredResponse? {
        ColorPrinter.printSendingWithContext(message, history.size())

        return try {
            // Send complete conversation state for checklist persistence
            val requestBody = ChatContextRequest(
                message = message,
                conversationState = history.getConversationState(),
                maxHistoryLength = 20,
                provider = null,
                systemPrompt = null
            )

            val response = client.post("$baseUrl/ai/chat/context") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status == HttpStatusCode.OK) {
                try {
                    // Parse directly as ChatResponseDto
                    val chatResponse = response.body<ChatResponseDto>()
                    val structuredResponse = chatResponse.response

                    // Show checklist updates
                    val previousChecklist = history.getActiveChecklist()

                    // Add messages to history
                    history.addMessage("user", message)
                    history.addMessage("assistant", structuredResponse.message)

                    // Update checklist in conversation state
                    history.updateChecklist(structuredResponse.checkList)

                    // Print checklist updates
                    ColorPrinter.printChecklistUpdate(previousChecklist, structuredResponse.checkList)

                    // Print response
                    ColorPrinter.printResponse(structuredResponse)

                    // Print token usage if available and add to statistics
                    chatResponse.usage?.let { usage ->
                        ColorPrinter.printTokenUsage(usage)
                        history.addTokenUsage(usage)
                    }

                    structuredResponse
                } catch (e: Exception) {
                    // Fallback: try to parse as plain StructuredResponse
                    try {
                        val structuredResponse = response.body<StructuredResponse>()

                        // Show checklist updates
                        val previousChecklist = history.getActiveChecklist()

                        // Add messages to history
                        history.addMessage("user", message)
                        history.addMessage("assistant", structuredResponse.message)

                        // Update checklist in conversation state
                        history.updateChecklist(structuredResponse.checkList)

                        // Print checklist updates
                        ColorPrinter.printChecklistUpdate(previousChecklist, structuredResponse.checkList)

                        // Print response
                        ColorPrinter.printResponse(structuredResponse)

                        structuredResponse
                    } catch (e2: Exception) {
                        // Final fallback: show error with raw response
                        val responseText = response.body<String>()
                        ColorPrinter.printError("Failed to parse response: ${e.message}")
                        ColorPrinter.printError("Fallback parse error: ${e2.message}")
                        ColorPrinter.printError("Raw response: $responseText")
                        null
                    }
                }
            } else {
                ColorPrinter.printError(response.status.toString())
                ColorPrinter.printErrorDetails(response.body())
                null
            }
        } catch (e: Exception) {
            ColorPrinter.printConnectionError(e.message ?: "Unknown error", baseUrl)
            null
        }
    }

    suspend fun testSseConnection(baseUrl: String) {
        println("🔌 Connecting to SSE endpoint: $baseUrl/ai/random-numbers")

        try {
            client.sse("$baseUrl/ai/random-numbers") {
                incoming.collect { event ->
                    when (event.event) {
                        "random-number" -> {
                            println("📡 Received random number event: ${event.data}")
                        }

                        "completed" -> {
                            println("✅ Stream completed: ${event.data}")
                        }

                        "error" -> {
                            println("❌ SSE Error: ${event.data}")
                        }
                    }
                    event.event
                    println(event)
                }
            }
        } catch (e: Exception) {
            ColorPrinter.printConnectionError(e.message ?: "Unknown SSE error", baseUrl)
        }
    }

    suspend fun analyzeGithub(
        baseUrl: String,
        message: String
    ): GithubAnalysisResponse? {
        ColorPrinter.printInfo("🔍 Starting GitHub repository analysis...")
        ColorPrinter.printInfo("📊 Request: ${message.take(100)}${if (message.length > 100) "..." else ""}")

        return try {
            val requestBody = GithubAnalysisRequest(userMessage = message)

            val response = client.post("$baseUrl/ai/analyze-github") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                timeout {
                    requestTimeoutMillis = 600_000 // 10 minutes timeout
                }
            }

            if (response.status == HttpStatusCode.OK) {
                val githubResponse = response.body<GithubAnalysisResponse>() //

                ColorPrinter.printSuccess("✅ GitHub analysis completed successfully!")

                // Print basic info about the response
                ColorPrinter.printInfo("📝 Analysis length: ${githubResponse.analysis.length} characters")
                ColorPrinter.printInfo("🔧 Tool calls made: ${githubResponse.toolCalls.size}")

                githubResponse.usage?.let { usage ->
                    ColorPrinter.printInfo("🎯 Token usage: ${usage.totalTokens} total (${usage.promptTokens} prompt + ${usage.completionTokens} completion)")
                }

                githubResponse
            } else {
                ColorPrinter.printError("❌ Analysis failed with status: ${response.status}")
                ColorPrinter.printErrorDetails(response.body())
                null
            }
        } catch (e: Exception) {
            ColorPrinter.printConnectionError(e.message ?: "Unknown error during GitHub analysis", baseUrl)
            null
        }
    }

    fun close() {
        client.close()
    }
}
