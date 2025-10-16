package ru.andvl.chatter.backend

import ai.koog.ktor.Koog
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.koog.service.KoogServiceFactory
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.*
import ru.andvl.SampleService
import ru.andvl.SampleServiceImpl
import ru.andvl.chatter.koog.config.KoogConfig
import ru.andvl.chatter.koog.model.ChatRequest
import ru.andvl.chatter.koog.model.ChatResponse
import ru.andvl.chatter.koog.model.SimpleMessage
import ru.andvl.chatter.backend.dto.ChatRequestDto
import ru.andvl.chatter.backend.dto.ChatResponseDto
import ru.andvl.chatter.backend.dto.MessageDto
import ru.andvl.chatter.koog.service.Provider
import java.util.Properties
import kotlinx.serialization.Serializable

@Serializable
data class HealthStatus(
    val status: String,
    val message: String,
    val providers: Map<String, Boolean>
)

fun Application.configureFrameworks() {
    // Теперь API ключи загружаются из .env через Application.main и устанавливаются как системные свойства
    val googleApiKey = System.getProperty("GOOGLE_API_KEY")
    val openRouterApiKey = System.getProperty("OPENROUTER_API_KEY")

    log.info("Loading API keys from system properties (loaded from .env)...")
    log.info("Google API key: ${googleApiKey?.take(10)}...")
    log.info("OpenRouter API key: ${openRouterApiKey?.take(10)}...")

  dependencies {
        provide { GreetingService { "Hello, World!" } }
    }
    install(ContentNegotiation) {
        json()
    }
    install(Krpc)
    install(Koog) {
        llm {
            google(apiKey = googleApiKey ?: "your-google-api-key")
            openRouter(apiKey = openRouterApiKey ?: "your-openrouter-api-key")
        }
    }

    routing {
        route("/ai") {
            post("/chat") {
                try {
                    val userInput = call.receive<String>()
                    log.info("Received simple chat request: ${userInput.take(100)}...")

                    // Use KoogService from koog-service module (pure AiAgents, no RoutingContext)
                    val koogService = KoogServiceFactory.createFromEnv()
                    val response = koogService.chat(userInput, this)

                    log.info("AI response generated successfully: $response")
                    call.respond(response)
                } catch (e: Exception) {
                    log.error("Error processing AI request", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Error processing request: ${e.message}"
                    )
                }
            }

            post("/chat/context") {
                try {
                    val request = call.receive<ChatRequestDto>()
                    log.info("Received context chat request: ${request.message.take(100)}...")

                    val koogService = KoogServiceFactory.createFromEnv()

                    // Convert DTO history to SimpleMessage objects
                    val history = request.history.map {
                        SimpleMessage(
                            role = it.role.lowercase(),
                            content = it.content
                        )
                    }

                    // Create ChatRequest
                    val chatRequest = ChatRequest(
                        message = request.message,
                        history = history,
                        maxHistoryLength = request.maxHistoryLength
                    )

                    // Use provider if specified (pure AiAgents, no RoutingContext)
                    val response: ChatResponse = if (request.provider != null) {
                        val provider = when (request.provider.lowercase()) {
                            "google" -> Provider.GOOGLE
                            "openrouter" -> Provider.OPENROUTER
                            else -> Provider.OPENROUTER
                        }
                        koogService.chatWithContext(chatRequest, provider, this)
                    } else {
                        koogService.chat(chatRequest, this)
                    }

                    log.info("AI response with context generated successfully")
                    call.respond(ChatResponseDto(
                        response = response.response,
                        model = response.model,
                        usage = response.usage?.let {
                            ru.andvl.chatter.backend.dto.TokenUsageDto(
                                promptTokens = it.promptTokens,
                                completionTokens = it.completionTokens,
                                totalTokens = it.totalTokens
                            )
                        }
                    ))
                } catch (e: Exception) {
                    log.error("Error processing AI context request", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Unknown error"))
                    )
                }
            }

            // Health check endpoint for AI services
            get("/health") {
                try {
                // Use KoogService to check health
                val koogService = KoogServiceFactory.createFromEnv()
                val serviceHealth = koogService.getHealthStatus()
                val healthStatus = HealthStatus(
                    status = serviceHealth["status"] as? String ?: "unknown",
                    message = serviceHealth["message"] as? String ?: "Unknown status",
                    providers = mapOf(
                        "google" to true,
                        "openrouter" to true
                    )
                )
                call.respond(HttpStatusCode.OK, healthStatus)
                } catch (e: Exception) {
                    log.error("Error checking AI service health", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("status" to "error", "message" to e.message)
                    )
                }
            }
        }
    }
    routing {
        rpc("/api") {
            rpcConfig {
                serialization {
                    json()
                }
            }

            registerService<SampleService> { SampleServiceImpl() }
        }
    }
}