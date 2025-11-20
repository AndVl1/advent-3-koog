package ru.andvl.chatter.backend

import ai.koog.ktor.Koog
import ai.koog.ktor.llm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.delay
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.andvl.SampleService
import ru.andvl.SampleServiceImpl
import ru.andvl.chatter.backend.dto.ChatRequestDto
import ru.andvl.chatter.backend.dto.ChatResponseDto
import ru.andvl.chatter.koog.mapping.toKoogMessages
import ru.andvl.chatter.koog.mapping.toSharedResponse
import ru.andvl.chatter.koog.model.structured.ChatRequest
import ru.andvl.chatter.koog.model.structured.ChatResponse
import ru.andvl.chatter.koog.service.KoogServiceFactory
import ru.andvl.chatter.koog.service.Provider
import ru.andvl.chatter.shared.models.github.AnalysisEventOrResult
import ru.andvl.chatter.shared.models.github.GithubAnalysisRequest
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse
import kotlin.random.Random

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
    val agentRouterApiKey = System.getProperty("AGENTROUTER_API_KEY")
    val openAIApiKey = System.getProperty("OPENAI_API_KEY")
    val zaiApiKey = System.getProperty("ZAI_API_KEY")

    log.info("Loading API keys from system properties (loaded from .env)...")
    log.info("Google API key: ${googleApiKey?.take(10)}...")
    log.info("OpenRouter API key: ${openRouterApiKey?.take(10)}...")
    log.info("AgentRouter API key: ${agentRouterApiKey?.take(10)}...")
    log.info("z-ai API key: ${zaiApiKey?.take(10)}...")
    log.info("openai API key: ${openAIApiKey?.take(10)}...")

    dependencies {
        provide { GreetingService { "Hello, World!" } }
    }
    install(ContentNegotiation) {
        json()
    }
    install(SSE)
    install(Krpc)
    install(Koog) {
        llm {
            google(apiKey = googleApiKey ?: "your-google-api-key")
            openRouter(apiKey = openRouterApiKey ?: "your-openrouter-api-key")
            openAI(apiKey = openAIApiKey ?: "your-agent-api-key") {
//                baseUrl = "https://agentrouter.org/"
            }
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
                    val response = koogService.chat(userInput, llm())

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
                    request.conversationState?.activeChecklist?.let {
                        log.info("Current checklist: ${it.joinToString()}")
                    }

                    val koogService = KoogServiceFactory.createFromEnv()

                    // Get history and checklist from conversation state
                    val conversationState = request.conversationState
                    val history = conversationState?.history?.toKoogMessages() ?: emptyList()
                    val activeChecklist = conversationState?.activeChecklist ?: emptyList()

                    // Create ChatRequest with checklist context
                    val chatRequest = ChatRequest(
                        message = request.message,
                        systemPrompt = request.systemPrompt,
                        history = history,
                        maxHistoryLength = request.maxHistoryLength,
                        currentChecklist = activeChecklist // Pass current checklist for context
                    )

                    // Use provider if specified (pure AiAgents, no RoutingContext)
                    val response: ChatResponse = run {
                        val provider = when (request.provider?.lowercase()) {
                            "google" -> Provider.GOOGLE
                            "openrouter" -> Provider.OPENROUTER
                            else -> Provider.OPENROUTER
                        }
                        koogService.chat(chatRequest, llm(), provider)
                    }

                    log.info("AI response with context generated successfully")
                    call.respond(ChatResponseDto(
                        response = response.response.toSharedResponse(),
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

            post("/analyze-github") {
                try {
                    val request = call.receive<GithubAnalysisRequest>()
                    log.info("Received GitHub analysis request: ${request.userMessage.take(100)}...")

                    val koogService = KoogServiceFactory.createFromEnv()

                    // Default LLM configuration for server
                    val defaultLLMConfig = ru.andvl.chatter.shared.models.github.LLMConfig(
                        provider = "OPEN_ROUTER",
                        model = "z-ai/glm-4.6",
                        apiKey = null,  // Will use env variable
                        baseUrl = null,  // Use default
                        fixingModel = "z-ai/glm-4.6"  // Same as main model by default
                    )

                    val response = koogService.analyseGithub(this.llm(), request, defaultLLMConfig)

                    val githubResponse = GithubAnalysisResponse(
                        analysis = response.analysis,
                        tldr = response.tldr,
                        toolCalls = response.toolCalls,
                        model = response.model,
                        repositoryReview = response.repositoryReview,
                        requirements = response.requirements,
                        usage = response.usage?.let { usage ->
                            ru.andvl.chatter.shared.models.github.TokenUsageDto(
                                promptTokens = usage.promptTokens,
                                completionTokens = usage.completionTokens,
                                totalTokens = usage.totalTokens
                            )
                        }
                    )

                    log.info("GitHub analysis completed successfully: $githubResponse")
                    call.respond(HttpStatusCode.OK, githubResponse)
                } catch (e: Exception) {
                    log.error("Error processing GitHub analysis request", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Unknown error"))
                    )
                }
            }

            // SSE endpoint for GitHub analysis with streaming events
            sse("/analyze-github-stream") {
                try {
                    log.info("SSE client connected to /analyze-github-stream")

                    // Get request from query parameters or expect it to be sent as first message
                    // For simplicity, we'll expect the request in query params encoded as JSON
                    val requestJson = call.request.queryParameters["request"]
                        ?: throw IllegalArgumentException("Missing 'request' query parameter")

                    val request = Json.decodeFromString<GithubAnalysisRequest>(requestJson)
                    log.info("Received GitHub streaming analysis request: ${request.userMessage.take(100)}...")

                    val koogService = KoogServiceFactory.createFromEnv()

                    // Get PromptExecutor from Koog plugin
                    val promptExecutor = requireNotNull(call.application.pluginOrNull(Koog)) {
                        "Plugin Koog is not configured"
                    }.promptExecutor

                    // Default LLM configuration for server
                    val defaultLLMConfig = ru.andvl.chatter.shared.models.github.LLMConfig(
                        provider = "OPEN_ROUTER",
                        model = "z-ai/glm-4.6",
                        apiKey = null,
                        baseUrl = null,
                        fixingModel = "z-ai/glm-4.6"
                    )

                    // Stream analysis events
                    koogService.analyseGithubWithEvents(promptExecutor, request, defaultLLMConfig)
                        .collect { eventOrResult ->
                            when (eventOrResult) {
                                is AnalysisEventOrResult.Event -> {
                                    send(
                                        data = Json.encodeToString(
                                            AnalysisEventOrResult.Event.serializer(),
                                            eventOrResult
                                        ),
                                        event = "analysis-event"
                                    )
                                    log.info("Sent analysis event: ${eventOrResult.event}")
                                }
                                is AnalysisEventOrResult.Result -> {
                                    send(
                                        data = Json.encodeToString(
                                            AnalysisEventOrResult.Result.serializer(),
                                            eventOrResult
                                        ),
                                        event = "analysis-result"
                                    )
                                    log.info("Sent final analysis result")
                                }
                                is AnalysisEventOrResult.Error -> {
                                    send(
                                        data = Json.encodeToString(
                                            AnalysisEventOrResult.Error.serializer(),
                                            eventOrResult
                                        ),
                                        event = "analysis-error"
                                    )
                                    log.error("Analysis error: ${eventOrResult.message}")
                                }
                            }
                        }

                    log.info("SSE stream for GitHub analysis completed")
                } catch (e: Exception) {
                    log.error("Error in GitHub analysis SSE stream", e)
                    send(
                        data = Json.encodeToString(
                            AnalysisEventOrResult.Error.serializer(),
                            AnalysisEventOrResult.Error(
                                message = e.message ?: "Unknown error",
                                stackTrace = e.stackTraceToString()
                            )
                        ),
                        event = "analysis-error"
                    )
                }
            }

            // Code Modification Agent endpoint
            post("/modify-code") {
                try {
                    val request = call.receive<ru.andvl.chatter.koog.model.codemod.CodeModificationRequest>()
                    log.info("Received code modification request for repository: ${request.githubRepo}")

                    val koogService = KoogServiceFactory.createFromEnv()

                    // Get PromptExecutor from Koog plugin
                    val promptExecutor = requireNotNull(call.application.pluginOrNull(Koog)) {
                        "Plugin Koog is not configured"
                    }.promptExecutor

                    // Use OPENROUTER as default provider (no provider field in request)
                    val provider = Provider.OPENROUTER

                    val response = koogService.modifyCode(request, promptExecutor, provider)

                    log.info("Code modification completed: ${if (response.success) "SUCCESS" else "FAILED"}")
                    log.info("  PR URL: ${response.prUrl}")
                    log.info("  Files modified: ${response.filesModified.size}")
                    log.info("  Iterations used: ${response.iterationsUsed}")

                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    log.error("Error processing code modification request", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ru.andvl.chatter.koog.model.codemod.CodeModificationResponse(
                            success = false,
                            prUrl = null,
                            prNumber = null,
                            diff = null,
                            commitSha = null,
                            branchName = "",
                            filesModified = emptyList(),
                            verificationStatus = ru.andvl.chatter.koog.model.codemod.VerificationStatus.FAILED_SETUP,
                            iterationsUsed = 0,
                            errorMessage = "Error processing request: ${e.message}",
                            message = "Code modification failed with exception"
                        )
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

            // SSE endpoint for random numbers
            sse("/random-numbers") {
                var messageCount = 0
                val maxMessages = 7

                log.info("SSE client connected to /random-numbers")

                try {
                    while (messageCount < maxMessages) {
                        val randomNumber = Random.nextInt(1, 1000)

                        send(
                            data = Json.encodeToString(
                                kotlinx.serialization.json.JsonObject.serializer(),
                                kotlinx.serialization.json.buildJsonObject {
                                    put("number", kotlinx.serialization.json.JsonPrimitive(randomNumber))
                                    put("message", kotlinx.serialization.json.JsonPrimitive(messageCount + 1))
                                    put("timestamp", kotlinx.serialization.json.JsonPrimitive(System.currentTimeMillis()))
                                }
                            ),
                            event = "random-number",
                            id = (messageCount + 1).toString()
                        )

                        messageCount++
                        log.info("Sent SSE message $messageCount/$maxMessages: $randomNumber")

                        if (messageCount < maxMessages) {
                            delay(1000) // Wait 3 seconds before next message
                        }
                    }

                    // Send completion message
                    send(
                        data = Json.encodeToString(
                            kotlinx.serialization.json.JsonObject.serializer(),
                            kotlinx.serialization.json.buildJsonObject {
                                put("status", kotlinx.serialization.json.JsonPrimitive("completed"))
                                put("total_messages", kotlinx.serialization.json.JsonPrimitive(maxMessages))
                            }
                        ),
                        event = "completed"
                    )

                    log.info("SSE stream completed after $maxMessages messages")
                } catch (e: Exception) {
                    log.error("Error in SSE stream", e)
                    send(
                        data = """{"error": "${e.message}"}""",
                        event = "error"
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
