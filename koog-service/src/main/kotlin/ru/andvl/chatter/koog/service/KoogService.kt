package ru.andvl.chatter.koog.service

import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.ktor.aiAgent
import ai.koog.ktor.llm
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessageMetaInfo
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import ru.andvl.chatter.koog.model.ChatRequest
import ru.andvl.chatter.koog.model.ChatResponse
import ru.andvl.chatter.koog.model.SimpleMessage
import ru.andvl.chatter.koog.model.StructuredResponse
import ru.andvl.chatter.koog.model.TokenUsage

/**
 * Koog service - independent service for LLM interaction with context support
 */
class KoogService {

    private val defaultSystemPrompt =
        "You are a helpful AI assistant. Provide clear, accurate, and thoughtful responses."

    /**
     * Chat with simple message (backward compatibility)
     */
    suspend fun chat(message: String, routingContext: RoutingContext): StructuredResponse {
        val request = ChatRequest(message = message)
        return chat(request, routingContext).response
    }

    /**
     * Chat with full context support
     */
    suspend fun chat(request: ChatRequest, routingContext: RoutingContext): ChatResponse {
        return withContext(Dispatchers.IO) {
            val prompt = prompt(Prompt(emptyList(), "structured")) {
                system {
                    request.systemPrompt ?: defaultSystemPrompt
                }
                message(
                    Message.User(
                        content = request.message,
                        metaInfo = RequestMetaInfo(
                            timestamp = Clock.System.now()
                        )
                    )
                )
            }
            try {
                // Execute with OpenRouter using prompt builder
                val response = routingContext.llm()
                    .executeStructured<StructuredResponse>(
                        prompt = prompt,
                        model = OpenRouterModels.Gemini2_5Flash,
                        // Optional: provide a fixing parser for error correction
                        fixingParser = StructureFixingParser(
                            fixingModel = OpenRouterModels.GPT5Nano,
                            retries = 3
                        )
                    )
//                    .execute(prompt, OpenRouterModels.Gemini2_5Flash)

                ChatResponse(
                    response = response.getOrNull()!!.structure,
                    model = OpenRouterModels.Gemini2_5Flash.id
                )
            } catch (e: Exception) {
                // Fallback to Google AI
                try {
                    val response = with(routingContext) {
                        routingContext.llm()
                            .executeStructured<StructuredResponse>(
                                prompt = prompt,
                                model = OpenRouterModels.Gemini2_5Flash,
                                // Optional: provide a fixing parser for error correction
                                fixingParser = StructureFixingParser(
                                    fixingModel = OpenRouterModels.GPT5Nano,
                                    retries = 3
                                )
                            )
                    }

                    ChatResponse(
                        response = response.getOrNull()!!.structure,
                        model = GoogleModels.Gemini2_5Flash.id
                    )
                } catch (e2: Exception) {
                    throw Exception("Both providers failed: ${e2.message}", e2)
                }
            }
        }
    }

    /**
     * Send message with specific model - requires RoutingContext for aiAgent
     */
    suspend fun chat(message: String, model: LLModel, routingContext: RoutingContext): String {
        return withContext(Dispatchers.IO) {
            try {
                with(routingContext) {
                    aiAgent(message, model = model)
                }
            } catch (e: Exception) {
                throw Exception("Failed to process request with model $model: ${e.message}", e)
            }
        }
    }

    /**
     * Send message with preferred provider
     */
    suspend fun chatWithProvider(
        message: String,
        provider: Provider,
        routingContext: RoutingContext
    ): String {
        val model: LLModel = when (provider) {
            Provider.GOOGLE -> GoogleModels.Gemini2_5Flash
            Provider.OPENROUTER -> OpenRouterModels.Gemini2_5Flash
        }

        return withContext(Dispatchers.IO) {
            try {
                with(routingContext) {
                    aiAgent(message, model = model)
                }
            } catch (e: Exception) {
                throw Exception("Failed to process request with provider $provider: ${e.message}", e)
            }
        }
    }

    /**
     * Chat with context and specific provider
     */
    suspend fun chatWithContext(
        request: ChatRequest,
        provider: Provider,
        routingContext: RoutingContext
    ): ChatResponse {
        val model: LLModel = when (provider) {
            Provider.GOOGLE -> GoogleModels.Gemini2_5Flash
            Provider.OPENROUTER -> OpenRouterModels.Gemini2_5Flash
        }

        return withContext(Dispatchers.IO) {
            try {
                // Execute with specified provider
                val response = with(routingContext) {
                    aiAgent(singleRunStrategy(), model = model) {
                        val promptText = buildString {
                            // Add system prompt if provided
                            val systemPrompt = request.systemPrompt ?: defaultSystemPrompt
                            if (systemPrompt.isNotEmpty()) {
                                append("System: $systemPrompt\n\n")
                            }

                            // Add history messages
                            request.history.takeLast(request.maxHistoryLength).forEach { msg ->
                                when (msg.role.lowercase()) {
                                    "user" -> append("User: ${msg.content}\n")
                                    "assistant" -> append("Assistant: ${msg.content}\n")
                                    "system" -> append("System: ${msg.content}\n")
                                    "tool" -> append("Tool: ${msg.content}\n")
                                }
                            }

                            // Add current user message
                            append("User: ${request.message}")
                        }

                        it.run(promptText)
                    }
                }

                ChatResponse(
                    response = StructuredResponse("", response),
                    model = when (provider) {
                        Provider.GOOGLE -> "google-gemini-2.5-flash"
                        Provider.OPENROUTER -> "openrouter-gemini-2.5-flash"
                    }
                )
            } catch (e: Exception) {
                throw Exception("Failed to process request with provider $provider: ${e.message}", e)
            }
        }
    }

    /**
     * Get health status
     */
    fun getHealthStatus(): Map<String, Any> {
        return try {
            mapOf(
                "status" to "healthy",
                "message" to "Koog AI service is configured",
                "providers" to listOf("google", "openrouter"),
                "features" to mapOf(
                    "system_prompt" to true,
                    "chat_history" to true,
                    "context_limit" to 10
                )
            )
        } catch (e: Exception) {
            mapOf(
                "status" to "unhealthy",
                "message" to (e.message ?: "Unknown error")
            )
        }
    }
}

/**
 * AI Provider enum
 */
enum class Provider {
    GOOGLE,
    OPENROUTER
}

/**
 * Factory for creating KoogService
 */
object KoogServiceFactory {

    /**
     * Create KoogService from environment variables
     * This should be called after Koog is configured in the application
     */
    fun createFromEnv(): KoogService {
        return KoogService()
    }
}