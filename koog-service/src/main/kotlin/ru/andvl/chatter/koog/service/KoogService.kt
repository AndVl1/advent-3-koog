package ru.andvl.chatter.koog.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import ru.andvl.chatter.koog.agents.mcp.getGithubAnalysisStrategy
import ru.andvl.chatter.koog.agents.mcp.getToolAgentPrompt
import ru.andvl.chatter.koog.agents.memory.githubMemoryProvider
import ru.andvl.chatter.koog.agents.structured.getStructuredAgentPrompt
import ru.andvl.chatter.koog.agents.structured.getStructuredAgentStrategy
import ru.andvl.chatter.koog.agents.utils.createFixingModel
import ru.andvl.chatter.koog.mcp.McpProvider
import ru.andvl.chatter.koog.model.common.TokenUsage
import ru.andvl.chatter.koog.model.structured.ChatRequest
import ru.andvl.chatter.koog.model.structured.ChatResponse
import ru.andvl.chatter.koog.model.structured.StructuredResponse
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
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

            Your role is to help users analyze GitHub repositories by:
            - Understanding user requests and extracting necessary information (repository URLs, requirements documents)
            - Using available MCP tools to gather comprehensive repository data
            - Analyzing code, structure, dependencies, and compliance with requirements
            - Providing detailed, structured analysis reports
            - Evaluating Docker containerization possibilities

            **Key Principles:**
            - Follow multi-stage analysis workflow (URL extraction → requirements collection → repository analysis → Docker build)
            - Use MCP tools systematically to gather factual information
            - Maintain language consistency with user's original request (Russian/English)
            - Provide specific evidence (file references, code quotes) when evaluating requirements
            - Be thorough but focused on user's specific needs

            **Available Tool Categories:**
            - GitHub MCP tools: repository metadata, files, content, commits, issues
            - Google Docs MCP tools: read requirements documents
            - Docker tools: build and verify containerization
            - Current time tools: timestamp information

            Work systematically through each stage of analysis, using appropriate tools and providing comprehensive, actionable insights.
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
    suspend fun chat(message: String, promptExecutor: PromptExecutor): StructuredResponse {
        val request = ChatRequest(message = message, currentChecklist = emptyList())
        return chat(request, promptExecutor).response
    }

    /**
     * Chat with full context support
     */
    suspend fun chat(
        request: ChatRequest,
        promptExecutor: PromptExecutor,
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
                val executor = promptExecutor
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
                    val response = with(promptExecutor) {
                        executeStructured<StructuredResponse>(
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
        promptExecutor: PromptExecutor,
        request: GithubAnalysisRequest,
        llmConfig: LLMConfig,
    ): GithubAnalysisResponse {
        return withContext(Dispatchers.IO) {
            val toolRegistry = McpProvider.getGithubToolsRegistry()
                .plus(McpProvider.getGoogleDocsToolsRegistry())
                .plus(McpProvider.getDockerToolsRegistry())
                .plus(McpProvider.getUtilsToolsRegistry())
            //  Map provider string to LLMProvider enum
            val llmProvider = when (llmConfig.provider.uppercase()) {
                "OPEN_ROUTER", "OPENROUTER" -> LLMProvider.OpenRouter
                "OPENAI" -> LLMProvider.OpenAI
                "ANTHROPIC" -> LLMProvider.Anthropic
                "CUSTOM" -> object : LLMProvider(llmConfig.baseUrl.toString(), llmConfig.provider) {}  // Use OpenRouter-compatible for custom providers
                else -> LLMProvider.OpenRouter
            }

            val model = LLModel(
                provider = llmProvider,
                id = llmConfig.model,
                capabilities = listOfNotNull(
                    LLMCapability.Temperature,
                    LLMCapability.Completion,
                    LLMCapability.Tools,
                    LLMCapability.OpenAIEndpoint.Completions
                ),
                contextLength = 100_000,
            )

            val systemPrompt = buildGithubSystemPrompt()
            val prompt = getToolAgentPrompt(
                systemPrompt = "", //systemPrompt,
                request = ChatRequest(message = request.userMessage),
                temperature = 0.3
            )

            val agentConfig = AIAgentConfig(
                prompt = prompt,
                model = model,
                maxAgentIterations = 600,
            )

            // Create fixing model for error correction
            val fixingModel = createFixingModel(
                provider = llmConfig.provider,
                modelId = llmConfig.fixingModel ?: llmConfig.model
            )

            val strategy = getGithubAnalysisStrategy(fixingModel)

            val agent = AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = toolRegistry,
                id = "github-analyzer",
                installFeatures = {
                    install(Tracing) {
                        val outputPath = Path("./logs/koog_trace.log")
                        addMessageProcessor(TraceFeatureMessageLogWriter(logger) { it.toString().take(200) })
                        addMessageProcessor(TraceFeatureMessageFileWriter(
                            outputPath,
                            { path: Path -> SystemFileSystem.sink(path).buffered() }
                        ))
                    }

                    install(AgentMemory) {
                        agentName = "github-helper"
                        memoryProvider = githubMemoryProvider
                    }
                }
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
                    userRequestAnalysis = result.userRequestAnalysis,
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
                    userRequestAnalysis = null,
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
