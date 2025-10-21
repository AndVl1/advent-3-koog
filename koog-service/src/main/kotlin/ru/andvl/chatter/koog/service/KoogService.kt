package ru.andvl.chatter.koog.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.ktor.llm
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.andvl.chatter.koog.agents.getStructuredAgentPrompt
import ru.andvl.chatter.koog.agents.getStructuredAgentStrategy
import ru.andvl.chatter.koog.model.ChatRequest
import ru.andvl.chatter.koog.model.ChatResponse
import ru.andvl.chatter.koog.model.StructuredResponse

/**
 * Koog service - independent service for LLM interaction with context support
 */
class KoogService {

    private fun buildSystemPrompt(request: ChatRequest): String {
        val basePrompt = """ BASE PROMPT:
            
            You are a helpful AI assistant that helps users create and build things.

When users ask to CREATE, BUILD, or DEVELOP something, structure your response as follows:

1. MESSAGE: Write a brief, focused message with 1-3 specific questions about their needs
2. CHECKLIST: Update and maintain the complete checklist of ALL items you need to know for the project
3. CHECKLIST FINALISATION: After you've collected everything you need from user, collect in into single human-readable message with steps to 
implement user's project and send it into MESSAGE field

Key guidelines:
- Keep the message concise and conversational  
- The checklist should include all requirements you need to gather
- Users will respond to your questions, and you'll update checklist items with resolution text when answered
- Always answer in user's language
- MAINTAIN CHECKLIST CONTINUITY: When updating the checklist, preserve all existing items and only update/add items as needed

Always provide both a focused message AND a complete updated checklist in your responses.

${request.systemPrompt?.let { "USER PROMPT:\n$it" } ?: ""}
"""
        val currentChecklist = request.currentChecklist

        return if (currentChecklist.isNotEmpty()) {
            val checklistStatus = currentChecklist.joinToString("\n") { item ->
                val status = if (item.resolution != null) "✅ RESOLVED" else "❓ PENDING"
                "- ${item.point} [$status]${if (item.resolution != null) ": ${item.resolution}" else ""}"
            }

            "$basePrompt\n\nCURRENT CHECKLIST STATUS:\n$checklistStatus\n\nUpdate this checklist based on the user's response. Preserve resolved items and update pending ones as needed."
        } else {
            basePrompt
        }
    }

    /**
     * Chat with simple message (backward compatibility)
     */
    suspend fun chat(message: String, routingContext: RoutingContext): StructuredResponse {
        val request = ChatRequest(message = message, currentChecklist = emptyList())
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
            Provider.OPENROUTER -> OpenRouterModels.Gemini2_5FlashLite
            else -> OpenRouterModels.Gemini2_5Flash
        }
        return withContext(Dispatchers.IO) {
            val systemPrompt = buildSystemPrompt(request)

            val prompt = getStructuredAgentPrompt(
                systemPrompt = systemPrompt,
                request = request,
                temperature = null
            )
            try {
                val executor = routingContext.llm()
                val strategy = getStructuredAgentStrategy(systemPrompt)
                val agentConfig = AIAgentConfig(
                    prompt = prompt,
                    model = model,
                    maxAgentIterations = 50,
                )
                val agent = AIAgent(
                    promptExecutor = executor,
                    toolRegistry = ToolRegistry {},
                    strategy = strategy,
                    agentConfig = agentConfig,
                    id = "structured_agent",
                    installFeatures = {}
                )
                val resp = agent.run(request)

                ChatResponse(
                    response = resp.getOrNull()?.structure!!,
                    originalMessage = resp.getOrNull()?.message,
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
