package ru.andvl.chatter.koog.service

import ai.koog.ktor.llm
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import ru.andvl.chatter.koog.model.ChatRequest
import ru.andvl.chatter.koog.model.ChatResponse
import ru.andvl.chatter.koog.model.StructuredResponse

/**
 * Koog service - independent service for LLM interaction with context support
 */
class KoogService {

    private val defaultSystemPrompt =
        "You are a helpful AI assistant. Provide clear, accurate, and thoughtful responses." +
                "Always answer in given format"

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
    suspend fun chat(
        request: ChatRequest,
        routingContext: RoutingContext,
        provider: Provider? = null,
    ): ChatResponse {
        val model: LLModel = when (provider) {
            Provider.GOOGLE -> GoogleModels.Gemini2_5Flash
            Provider.OPENROUTER -> OpenRouterModels.Gemini2_5Flash
            else -> OpenRouterModels.Gemini2_5Flash
        }
        return withContext(Dispatchers.IO) {
            val prompt = prompt(Prompt(emptyList(), "structured")) {
                system {
                    request.systemPrompt ?: defaultSystemPrompt
                }
                messages(request.history)
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
                        model = model,
                        // Optional: provide a fixing parser for error correction
                        fixingParser = StructureFixingParser(
                            fixingModel = OpenRouterModels.GPT5Nano,
                            retries = 3
                        )
                    )

                ChatResponse(
                    response = response.getOrNull()!!.structure,
                    originalMessage = response.getOrNull()?.message,
                    model = model.id
                )
            } catch (e: Exception) {
                // Fallback to GPTNano
                try {
                    val response = with(routingContext) {
                        routingContext.llm()
                            .executeStructured<StructuredResponse>(
                                prompt = prompt,
                                model = OpenRouterModels.GPT5Nano,
                                // Optional: provide a fixing parser for error correction
                                fixingParser = StructureFixingParser(
                                    fixingModel = OpenRouterModels.GPT5Nano,
                                    retries = 3
                                )
                            )
                    }

                    ChatResponse(
                        response = response.getOrNull()!!.structure,
                        model = GoogleModels.Gemini2_5Flash.id,
                        originalMessage = response.getOrNull()?.message
                    )
                } catch (e2: Exception) {
                    throw Exception("Both providers failed: ${e2.message}", e2)
                }
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