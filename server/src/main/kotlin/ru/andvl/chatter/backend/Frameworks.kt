package ru.andvl.chatter.backend

import ai.koog.ktor.Koog
import ai.koog.ktor.aiAgent
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.*
import ru.andvl.SampleService
import ru.andvl.SampleServiceImpl
import java.util.Properties

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
                    log.info("Received request: $userInput")

                    // Проверяем наличие ключа в реальном времени
                    val currentGoogleKey = System.getProperty("GOOGLE_API_KEY")

                    log.info("Current Google API key check: ${currentGoogleKey?.take(10)}...")

                    if (currentGoogleKey.isNullOrEmpty()) {
                        log.error("Google API key is not set")
                        call.respond(HttpStatusCode.InternalServerError, "Google API key is not configured")
                        return@post
                    }

                    val output = aiAgent(userInput, model = OpenRouterModels.Gemini2_5Flash)
                    log.info("AI response: ${output.take(100)}...")
                    call.respondText(output)
                } catch (e: Exception) {
                    log.error("Error processing AI request", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Error processing request: ${e.message}"
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