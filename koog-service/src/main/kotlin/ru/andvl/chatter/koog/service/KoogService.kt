package ru.andvl.chatter.koog.service

import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.ktor.aiAgent
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Koog service - independent service for LLM interaction
 */
class KoogService {

    /**
     * Send a message to AI - requires RoutingContext for aiAgent
     */
    suspend fun chat(message: String, routingContext: RoutingContext): String {
        return withContext(Dispatchers.IO) {
            try {
                // Use OpenRouter with Gemini 2.5 Flash as default
                with(routingContext) {
                    aiAgent(singleRunStrategy(), model = OpenRouterModels.Gemini2_5Flash) {
                        prompt(existing = Prompt(emptyList<Message>(), "")) {
                            system {
                                text("")
                            }
                        }
                        it.run(message)
                    }
                }
            } catch (e: Exception) {
                // Fallback to Google AI if OpenRouter fails
                try {
                    with(routingContext) {
                        aiAgent(singleRunStrategy(), model = GoogleModels.Gemini2_5Flash) {
                            prompt(existing = Prompt(emptyList<Message>(), "")) {
                                system {
                                    text("")
                                }
                            }
                            it.run(message)
                        }
                    }
                } catch (e2: Exception) {
                    // Both providers failed, rethrow the exception
                    throw Exception("Both OpenRouter and Google AI providers failed: ${e2.message}", e2)
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
     * Get health status
     */
    fun getHealthStatus(): Map<String, Any> {
        return try {
            // Note: Can't test aiAgent here without routing context
            mapOf(
                "status" to "healthy",
                "message" to "Koog AI service is configured",
                "providers" to listOf("google", "openrouter")
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