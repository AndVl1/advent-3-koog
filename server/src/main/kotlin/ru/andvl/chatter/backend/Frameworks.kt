package ru.andvl.ru.andvl.chatter.backend

import ai.koog.ktor.Koog
import ai.koog.ktor.aiAgent
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMProvider
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.*
import ru.andvl.SampleService
import ru.andvl.SampleServiceImpl

fun Application.configureFrameworks() {
    dependencies {
        provide { GreetingService { "Hello, World!" } }
    }
    install(Krpc)
    install(Koog) {
        llm {
            google(apiKey = "your-google-api-key")
            openRouter(apiKey = "your-openrouter-api-key")
        }
    }

    routing {
        route("/ai") {
            post("/chat") {
                val userInput = call.receive<String>()
                val output = aiAgent(userInput, model = GoogleModels.Gemini2_5Flash)
                call.respondText(output)
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