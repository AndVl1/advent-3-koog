package ru.andvl.chatter.koog.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.ktor.llm
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import ru.andvl.chatter.koog.agents.mcp.getGithubAnalysisStrategy
import ru.andvl.chatter.koog.agents.mcp.getToolAgentPrompt
import ru.andvl.chatter.koog.agents.structured.getStructuredAgentPrompt
import ru.andvl.chatter.koog.agents.structured.getStructuredAgentStrategy
import ru.andvl.chatter.koog.mcp.McpProvider
import ru.andvl.chatter.koog.model.common.TokenUsage
import ru.andvl.chatter.koog.model.structured.ChatRequest
import ru.andvl.chatter.koog.model.structured.ChatResponse
import ru.andvl.chatter.koog.model.structured.StructuredResponse
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.tools.CurrentTimeToolSet
import ru.andvl.chatter.koog.tools.DockerToolSet
import ru.andvl.chatter.shared.models.ChatHistory
import ru.andvl.chatter.shared.models.github.*

/**
 * Koog service - independent service for LLM interaction with context support
 */
class KoogService {

    private val logger = KotlinLogging.logger(KoogService::class.java.name)

    private fun buildGithubSystemPrompt(): String {
        return """
            You are a specialized GitHub repository analyzer AI assistant.
            
            Your primary task is to analyze GitHub repositories and provide comprehensive, structured information based on user requests.
            
            **Core Responsibilities:**
            1. Extract and validate GitHub repository URLs from user requests
            2. Understand user's specific analysis needs and questions
            3. Use available MCP tools to gather repository information
            4. Provide structured, detailed analysis with clear sections
            
            **Analysis Process:**
            1. **Initial Request Analysis**: Parse user request to extract:
               - GitHub repository URL
               - Specific information the user wants to know
               - Type of analysis requested (overview, technical details, code quality, etc.)
            
            2. **Repository Information Gathering**: Use MCP tools to collect:
               - Basic repository metadata (name, description, stars, forks, license)
               - README content and documentation
               - File structure and project organization
               - Dependencies and build configuration
               - Recent activity and commit history
               - Code samples and architecture insights
            
            3. **Structured Response**: Present findings in organized sections:
               - Repository Overview
               - Technical Stack & Dependencies
               - Project Structure
               - Key Features & Functionality
               - Code Quality & Best Practices
               - Recent Activity & Maintenance
               - Recommendations or specific answers to user questions
            
            **Response Guidelines:**
            - Be comprehensive but focused on user's specific questions
            - Use clear, professional language
            - Include relevant code snippets or examples when helpful
            - Provide actionable insights and recommendations
            - If information is not available, clearly state what couldn't be accessed
            
            **Error Handling:**
            - If repository URL is invalid or inaccessible, explain the issue clearly
            - If user request is unclear, ask for clarification
            - If tools fail, explain what information could not be retrieved
            
            Always maintain a helpful, analytical tone and provide value to developers seeking to understand GitHub repositories.
        """.trimIndent()
    }

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
            Provider.OPENROUTER -> LLModel(
                provider = LLMProvider.OpenRouter,
                id = "qwen/qwen3-coder", //"openai/gpt-5-nano", // "qwen/qwen3-coder"
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Completion,
                ),
                contextLength = 16_000, //
            )

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
                    installFeatures = {
                        install(Tracing) {
                            val outputPath = Path("/logs/koog_trace.log")
                            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
                            addMessageProcessor(TraceFeatureMessageFileWriter(
                                outputPath,
                                { path: Path -> SystemFileSystem.sink(path).buffered() }
                            ))
                        }
                    }
                )
                val resp = agent.run(request)
                val structuredResponse = resp.getOrNull()

                ChatResponse(
                    response = structuredResponse?.structure!!,
                    originalMessage = structuredResponse.message,
                    model = model.id,
                    usage = structuredResponse.message.metaInfo.let {
                        TokenUsage(
                            promptTokens = it.inputTokensCount ?: 0,
                            completionTokens = it.outputTokensCount ?: 0,
                            totalTokens = it.totalTokensCount ?: 0,
                        )
                    }
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

    suspend fun analyseGithub(
        routingContext: RoutingContext,
        request: GithubAnalysisRequest,
    ): GithubAnalysisResponse {
        return withContext(Dispatchers.IO) {
            val githubMcpClient = McpProvider.getGithubClient()
            val googleDocsMcpClient = McpProvider.getGoogleDocsClient()
            val toolRegistry = McpToolRegistryProvider.fromClient(githubMcpClient)
                .plus(McpToolRegistryProvider.fromClient(googleDocsMcpClient))
                .plus(ToolRegistry {
                    tools(CurrentTimeToolSet())
                    tools(DockerToolSet())
                })
            val strategy = getGithubAnalysisStrategy()
            val model = LLModel(
                provider = LLMProvider.OpenRouter,
                id = "z-ai/glm-4.6",
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Completion,
                    LLMCapability.Tools,
                ),
                contextLength = 128_000,
            )

            val systemPrompt = buildGithubSystemPrompt()
            val prompt = getToolAgentPrompt(
                systemPrompt = systemPrompt,
                request = ChatRequest(message = request.userMessage),
                temperature = 0.3
            )

            val agentConfig = AIAgentConfig(
                prompt = prompt,
                model = model,
                maxAgentIterations = 200,
            )

            val agent = AIAgent(
                promptExecutor = routingContext.llm(),
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = toolRegistry,
                id = "github-analyzer",
                installFeatures = {}
            )

            val githubRequest = GithubChatRequest(
                message = request.userMessage,
                systemPrompt = systemPrompt,
                history = ChatHistory()
            )

            try {
                val result = agent.run(githubRequest)

                // Map repository review if available
                val repositoryReview = result.repositoryReview?.let { reviewModel ->
                    RepositoryReviewDto(
                        generalConditionsReview = RequirementReviewCommentDto(
                            commentType = reviewModel.generalConditionsReview.commentType,
                            comment = reviewModel.generalConditionsReview.comment,
                            fileReference = reviewModel.generalConditionsReview.fileReference,
                            codeQuote = reviewModel.generalConditionsReview.codeQuote
                        ),
                        constraintsReview = reviewModel.constraintsReview.map { constraint ->
                            RequirementReviewCommentDto(
                                commentType = constraint.commentType,
                                comment = constraint.comment,
                                fileReference = constraint.fileReference,
                                codeQuote = constraint.codeQuote
                            )
                        },
                        advantagesReview = reviewModel.advantagesReview.map { advantage ->
                            RequirementReviewCommentDto(
                                commentType = advantage.commentType,
                                comment = advantage.comment,
                                fileReference = advantage.fileReference,
                                codeQuote = advantage.codeQuote
                            )
                        },
                        attentionPointsReview = reviewModel.attentionPointsReview.map { attention ->
                            RequirementReviewCommentDto(
                                commentType = attention.commentType,
                                comment = attention.comment,
                                fileReference = attention.fileReference,
                                codeQuote = attention.codeQuote
                            )
                        }
                    )
                }

                // Map requirements if available
                val requirementsDto = result.requirements?.let { req ->
                    RequirementsAnalysisDto(
                        generalConditions = req.generalConditions,
                        importantConstraints = req.importantConstraints,
                        additionalAdvantages = req.additionalAdvantages,
                        attentionPoints = req.attentionPoints
                    )
                }

                // Map Docker info if available
                val dockerInfoDto = result.dockerInfo?.let { dockerInfo ->
                    DockerInfoDto(
                        dockerEnv = DockerEnvDto(
                            baseImage = dockerInfo.dockerEnv.baseImage,
                            buildCommand = dockerInfo.dockerEnv.buildCommand,
                            runCommand = dockerInfo.dockerEnv.runCommand,
                            port = dockerInfo.dockerEnv.port,
                            additionalNotes = dockerInfo.dockerEnv.additionalNotes
                        ),
                        buildResult = DockerBuildResultDto(
                            buildStatus = dockerInfo.buildResult.buildStatus,
                            buildLogs = dockerInfo.buildResult.buildLogs,
                            imageSize = dockerInfo.buildResult.imageSize,
                            buildDurationSeconds = dockerInfo.buildResult.buildDurationSeconds,
                            errorMessage = dockerInfo.buildResult.errorMessage
                        ),
                        dockerfileGenerated = dockerInfo.dockerfileGenerated
                    )
                }

                GithubAnalysisResponse(
                    analysis = result.response,
                    tldr = result.shortSummary,
                    toolCalls = result.toolCalls,
                    model = null,
                    usage = result.tokenUsage?.let { usage ->
                        TokenUsageDto(
                            promptTokens = usage.promptTokens,
                            completionTokens = usage.completionTokens,
                            totalTokens = usage.totalTokens
                        )
                    },
                    repositoryReview = repositoryReview,
                    requirements = requirementsDto,
                    dockerInfo = dockerInfoDto
                )
            } catch (e: Exception) {
                GithubAnalysisResponse(
                    analysis = "Analysis failed with exception: ${e.message}",
                    tldr = "Analysis failed",
                    toolCalls = emptyList(),
                    model = null,
                    usage = TokenUsageDto(0, 0, 0),
                    repositoryReview = null,
                    requirements = null,
                    dockerInfo = null
                )
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
