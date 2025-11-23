package ru.andvl.chatter.koog.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import io.github.cdimascio.dotenv.Dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import ru.andvl.chatter.koog.agents.codemod.getCodeModificationStrategy
import ru.andvl.chatter.koog.agents.conversation.getConversationAgentStrategy
import ru.andvl.chatter.koog.agents.mcp.GithubAnalysisNodes
import ru.andvl.chatter.koog.agents.mcp.getGithubAnalysisStrategy
import ru.andvl.chatter.koog.agents.mcp.getToolAgentPrompt
import ru.andvl.chatter.koog.agents.memory.githubMemoryProvider
import ru.andvl.chatter.koog.agents.structured.getStructuredAgentPrompt
import ru.andvl.chatter.koog.agents.structured.getStructuredAgentStrategy
import ru.andvl.chatter.koog.agents.utils.createFixingModel
import ru.andvl.chatter.koog.embeddings.model.EmbeddingConfig
import ru.andvl.chatter.koog.mcp.McpProvider
import ru.andvl.chatter.koog.model.codemod.CodeModificationRequest
import ru.andvl.chatter.koog.model.codemod.CodeModificationResponse
import ru.andvl.chatter.koog.model.common.TokenUsage
import ru.andvl.chatter.koog.model.conversation.ConversationRequest
import ru.andvl.chatter.koog.model.conversation.ConversationResponse
import ru.andvl.chatter.koog.model.structured.ChatRequest
import ru.andvl.chatter.koog.model.structured.ChatResponse
import ru.andvl.chatter.koog.model.structured.StructuredResponse
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.tools.CurrentTimeToolSet
import ru.andvl.chatter.koog.tools.DockerToolSet
import ru.andvl.chatter.koog.tools.FileOperationsToolSet
import ru.andvl.chatter.koog.tools.RagToolSet
import ru.andvl.chatter.shared.models.ChatHistory
import ru.andvl.chatter.shared.models.github.*
import java.io.File

/**
 * Koog service - independent service for LLM interaction with context support
 */
class KoogService {

    private val logger = KotlinLogging.logger(KoogService::class.java.name)

    companion object {
        private val dotenv: Dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load()
    }

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
                            val outputPath = Path("./logs/koog_chat_trace.log")
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

    /**
     * Conversation method for simple text-based chat
     *
     * This is a simplified conversation method that:
     * - Takes text message and history
     * - Returns text response
     * - Maintains conversation context
     * - Can be extended with additional features in the future
     *
     * @param request Conversation request with message and history
     * @param promptExecutor Prompt executor for LLM calls
     * @param provider LLM provider to use
     * @return ConversationResponse with text and metadata
     */
    suspend fun conversation(
        request: ConversationRequest,
        promptExecutor: PromptExecutor,
        provider: Provider? = null,
    ): ConversationResponse {
        val model: LLModel = when (provider) {
            Provider.GOOGLE -> GoogleModels.Gemini2_5Flash
            Provider.OPENROUTER -> OpenRouterModels.Gemini2_5Flash
            else -> OpenRouterModels.Gemini2_5Flash
        }

        return withContext(Dispatchers.IO) {
            // Let the strategy nodes build their own prompts with personalization
            // This empty prompt is just a placeholder for the AIAgentConfig
            val emptyPrompt = prompt(
                Prompt(emptyList(), "conversation", params = LLMParams())
            ) {}

            try {
                val strategy = getConversationAgentStrategy(mainSystemPrompt = null)
                val agentConfig = AIAgentConfig(
                    prompt = emptyPrompt,
                    model = model,
                    maxAgentIterations = 10,
                )

                val agent = AIAgent(
                    promptExecutor = promptExecutor,
                    toolRegistry = ToolRegistry {},
                    strategy = strategy,
                    agentConfig = agentConfig,
                    id = "conversation_agent",
                    installFeatures = {
                        install(Tracing) {
                            val outputPath = Path("./logs/koog_conversation_trace.log")
                            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
                            addMessageProcessor(TraceFeatureMessageFileWriter(
                                outputPath,
                                { path: Path -> SystemFileSystem.sink(path).buffered() }
                            ))
                        }
                    }
                )

                val result = agent.run(request)
                val assistantMessage = result.getOrNull()

                ConversationResponse(
                    text = assistantMessage?.content ?: "Sorry, I couldn't generate a response.",
                    originalMessage = assistantMessage,
                    model = model.id,
                    usage = assistantMessage?.metaInfo?.let {
                        TokenUsage(
                            promptTokens = it.inputTokensCount ?: 0,
                            completionTokens = it.outputTokensCount ?: 0,
                            totalTokens = it.totalTokensCount ?: 0,
                        )
                    }
                )
            } catch (e: Exception) {
                logger.error(e) { "Conversation failed" }
                throw Exception("Conversation failed: ${e.message}", e)
            }
        }
    }

    /**
     * Code modification method for automated code changes
     *
     * This method:
     * - Clones/reuses GitHub repository
     * - Analyzes codebase and creates modification plan
     * - Applies code changes
     * - Runs Docker verification (LLM chooses command)
     * - Retries on failure (max 5 iterations)
     * - Commits and pushes changes or saves diff
     * - Creates PR if successful
     *
     * @param request Code modification request with repository URL and user's request
     * @param promptExecutor Prompt executor for LLM calls
     * @param provider LLM provider to use
     * @return CodeModificationResponse with PR URL or diff
     */
    suspend fun modifyCode(
        request: CodeModificationRequest,
        promptExecutor: PromptExecutor,
        provider: Provider? = null,
    ): CodeModificationResponse {
        val model: LLModel = when (provider) {
            Provider.GOOGLE -> GoogleModels.Gemini2_5Flash
            Provider.OPENROUTER -> LLModel(
                provider = LLMProvider.OpenRouter,
                id = "qwen/qwen3-coder", //"openai/gpt-5-nano", // "qwen/qwen3-coder"
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Completion,
                    LLMCapability.Tools,
                    LLMCapability.OpenAIEndpoint.Completions,
                    LLMCapability.MultipleChoices,
                ),
                contextLength = 200_000, //
            )
            else -> OpenRouterModels.Gemini2_5Flash
        }

        val fixingModel = when (provider) {
            Provider.GOOGLE -> GoogleModels.Gemini2_5Flash
            Provider.OPENROUTER -> LLModel(
                provider = LLMProvider.OpenRouter,
                id = "z-ai/glm-4.5-air", // "qwen/qwen3-coder", //"openai/gpt-5-nano", // "qwen/qwen3-coder"
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Completion,
                ),
                contextLength = 16_000, //
            )
            else -> OpenRouterModels.GPT5Nano
        }

        return withContext(Dispatchers.IO) {
            val emptyPrompt = prompt(
                Prompt(emptyList(), "code-modification", params = LLMParams())
            ) {}

            try {
                val strategy = getCodeModificationStrategy(
                    model = model,
                    fixingModel = fixingModel
                )

                val agentConfig = AIAgentConfig(
                    prompt = emptyPrompt,
                    model = model,
                    maxAgentIterations = 150,
                )
                val githubTools = McpProvider.getGithubToolsRegistry()

                val agent = AIAgent(
                    promptExecutor = promptExecutor,
                    toolRegistry = ToolRegistry {
                        // File operations tools (used in all subgraphs)
                        tools(FileOperationsToolSet())

                        // Docker tools (used in docker-verification subgraph)
                        tools(DockerToolSet())

                        // Utility tools
                        tools(CurrentTimeToolSet())

                        tools(githubTools.tools)

                        // RAG tools (used in code-analysis subgraph, if embeddings enabled)
                        if (request.enableEmbeddings) {
                            tools(RagToolSet())
                        }
                    },
                    strategy = strategy,
                    agentConfig = agentConfig,
                    id = "code-modification-agent",
                    installFeatures = {
                        install(Tracing) {
                            val outputPath = Path("./logs/koog_code_modification_trace.log")
                            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
                            addMessageProcessor(TraceFeatureMessageFileWriter(
                                outputPath,
                                { path: Path -> SystemFileSystem.sink(path).buffered() }
                            ))
                        }
                    }
                )

                val result = agent.run(request)
                val response = result

                logger.info { "Code modification completed: ${if (response.success) "SUCCESS" else "FAILED"}" }
                logger.info { "  PR URL: ${response.prUrl}" }
                logger.info { "  Files modified: ${response.filesModified.size}" }
                logger.info { "  Iterations used: ${response.iterationsUsed}" }

                response
            } catch (e: Exception) {
                logger.error(e) { "Code modification failed" }
                CodeModificationResponse(
                    success = false,
                    prUrl = null,
                    prNumber = null,
                    diff = null,
                    commitSha = null,
                    branchName = "",
                    filesModified = emptyList(),
                    verificationStatus = ru.andvl.chatter.koog.model.codemod.VerificationStatus.FAILED_SETUP,
                    iterationsUsed = 0,
                    errorMessage = "Code modification failed: ${e.message}",
                    message = "Code modification failed with exception"
                )
            }
        }
    }

    /**
     * Базовая функция для анализа GitHub репозитория с опциональной поддержкой событий
     *
     * @param promptExecutor Исполнитель промптов
     * @param request Запрос на анализ
     * @param llmConfig Конфигурация LLM
     * @param eventsChannel Опциональный канал для отправки промежуточных событий
     */
    private suspend fun analyseGithubBase(
        promptExecutor: PromptExecutor,
        request: GithubAnalysisRequest,
        llmConfig: LLMConfig,
        eventsChannel: Channel<AnalysisEvent>? = null
    ): GithubAnalysisResponse {
        return withContext(Dispatchers.IO) {
            val toolRegistry = McpProvider.getGithubToolsRegistry()
                .plus(McpProvider.getGoogleDocsToolsRegistry())
                .plus(McpProvider.getDockerToolsRegistry())
                .plus(McpProvider.getUtilsToolsRegistry())
                .plus(McpProvider.getRagToolsRegistry())
            //  Map provider string to LLMProvider enum
            val llmProvider = when (llmConfig.provider.uppercase()) {
                "OPEN_ROUTER", "OPENROUTER" -> LLMProvider.OpenRouter
                "OPENAI" -> LLMProvider.OpenAI
                "ANTHROPIC" -> LLMProvider.Anthropic
                "OLLAMA" -> LLMProvider.Ollama
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
                contextLength = llmConfig.maxContextTokens,
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
                modelId = llmConfig.fixingModel ?: llmConfig.model,
                fixingMaxContextTokens = llmConfig.fixingMaxContextTokens
            )

            // Configure embeddings
            val embeddingConfig = EmbeddingConfig(
                enabled = request.enableEmbeddings,
                ollamaBaseUrl = "http://localhost:11434",
                modelName = "zylonai/multilingual-e5-large"
            )
            val embeddingStorageDir = File("./rag/embeddings")

            val strategy = getGithubAnalysisStrategy(fixingModel, embeddingConfig, embeddingStorageDir)

            val agent = AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = toolRegistry,
                id = "github-analyzer",
                installFeatures = {
                    // Добавить EventHandler если передан eventsChannel
                    if (eventsChannel != null) {
                        handleEvents {
                            // Определяем основные шаги анализа (подграфы)
                            var currentStepNumber = 0
                            val totalSteps = 6 // Всего основных шагов
                            val completedStages = mutableSetOf<AnalysisStage>()

                            onStrategyStarting { _ ->
                                eventsChannel.send(AnalysisEvent.Started("Начинаем анализ репозитория..."))
                                currentStepNumber = 0
                                eventsChannel.send(
                                    AnalysisEvent.Progress(
                                        currentStep = 0,
                                        totalSteps = totalSteps,
                                        stepName = "Инициализация"
                                    )
                                )
                            }

                            onNodeExecutionStarting { eventContext ->
                                val nodeName = eventContext.node.name
                                logger.info { "📌 Node starting: $nodeName" }

                                // Определяем описание и стадию для важных нод
                                val (description, stage) = when (nodeName) {
                                    // Сбор требований
                                    GithubAnalysisNodes.InitialAnalysis.REQUIREMENTS_COLLECTION ->
                                        "Сбор требований" to AnalysisStage.COLLECTING_REQUIREMENTS

                                    // RAG индексация
                                    GithubAnalysisNodes.RAGIndexing.CLONE_AND_INDEX ->
                                        "Индексация кода" to AnalysisStage.RAG_INDEXING

                                    // Анализ GitHub - основной узел запроса
                                    GithubAnalysisNodes.GithubAnalysis.GITHUB_PROCESS_USER_REQUEST ->
                                        "Анализ репозитория" to AnalysisStage.ANALYZING_REPOSITORY

                                    // Docker анализ
                                    GithubAnalysisNodes.DockerBuild.DOCKER_REQUEST ->
                                        "Анализ Docker" to AnalysisStage.DOCKER_ANALYSIS

                                    // Google Sheets
                                    GithubAnalysisNodes.GoogleSheets.CHECK_GOOGLE_SHEETS ->
                                        "Google Sheets" to AnalysisStage.GOOGLE_SHEETS_INTEGRATION

                                    // Остальные узлы не создают новый шаг прогресса
                                    else -> null to null
                                }

                                // Отправляем Progress событие только для важных стадий
                                if (stage != null && description != null) {
                                    // Если встретили новую стадию, увеличиваем номер шага
                                    if (!completedStages.contains(stage) && stage != AnalysisStage.FINALIZING) {
                                        completedStages.add(stage)
                                        currentStepNumber = completedStages.size
                                        eventsChannel.send(
                                            AnalysisEvent.Progress(
                                                currentStep = currentStepNumber,
                                                totalSteps = totalSteps,
                                                stepName = description
                                            )
                                        )
                                    }
                                    eventsChannel.send(AnalysisEvent.StageUpdate(stage, description))
                                }

                                // Отправляем NodeStarted для ВСЕХ нод (с описанием или без)
                                eventsChannel.send(
                                    AnalysisEvent.NodeStarted(
                                        nodeName = nodeName,
                                        description = description
                                    )
                                )
                            }

                            onNodeExecutionCompleted { eventContext ->
                                eventsChannel.send(
                                    AnalysisEvent.NodeCompleted(
                                        eventContext.node.name,
                                        null
                                    )
                                )
                            }

                            onToolCallStarting { eventContext ->
                                val toolName = eventContext.tool.name
                                val description = when {
                                    toolName.contains("search") -> "Поиск в коде..."
                                    toolName.contains("github") -> "Получение данных из GitHub..."
                                    toolName.contains("docker") -> "Работа с Docker..."
                                    toolName.contains("sheet") || toolName.contains("google") -> "Работа с Google Sheets..."
                                    else -> "Выполняется $toolName..."
                                }
                                eventsChannel.send(
                                    AnalysisEvent.ToolExecution(toolName, description)
                                )
                            }

                            onStrategyCompleted { _ ->
                                eventsChannel.send(AnalysisEvent.StageUpdate(AnalysisStage.FINALIZING, "Завершение анализа"))
                                eventsChannel.send(
                                    AnalysisEvent.Progress(
                                        currentStep = totalSteps,
                                        totalSteps = totalSteps,
                                        stepName = "Формирование результатов"
                                    )
                                )
                                eventsChannel.send(AnalysisEvent.Completed("Анализ завершён успешно"))
                            }
                        }
                    }

                    install(Tracing) {
                        val outputPath = Path("./logs/koog_trace.log")
                        addMessageProcessor(TraceFeatureMessageLogWriter(logger) { it.toString().take(200) })
                        addMessageProcessor(TraceFeatureMessageFileWriter(
                            outputPath,
                            { path: Path -> SystemFileSystem.sink(path).buffered() }
                        ))
                    }

                    install(OpenTelemetry) {
                        // Always add console logging exporter for debugging
                        addSpanExporter(LoggingSpanExporter.create())

                        // Optionally add Jaeger exporter if OTEL_EXPORTER_OTLP_ENDPOINT is set
                        dotenv["OTEL_EXPORTER_OTLP_ENDPOINT"]?.let { endpoint ->
                            logger.info { "OpenTelemetry: Jaeger exporter enabled at $endpoint" }
                            addSpanExporter(
                                OtlpGrpcSpanExporter.builder()
                                    .setEndpoint(endpoint)
                                    .build()
                            )
                        } ?: logger.info { "OpenTelemetry: Jaeger exporter disabled (OTEL_EXPORTER_OTLP_ENDPOINT not set)" }
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
                history = ChatHistory(),
                googleSheetsUrl = request.googleSheetsUrl,
                forceSkipDocker = request.forceSkipDocker
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
     * Analyze GitHub repository (existing API without streaming)
     *
     * @param promptExecutor Prompt executor
     * @param request Analysis request
     * @param llmConfig LLM configuration
     * @return Analysis response
     */
    suspend fun analyseGithub(
        promptExecutor: PromptExecutor,
        request: GithubAnalysisRequest,
        llmConfig: LLMConfig,
    ): GithubAnalysisResponse {
        return analyseGithubBase(promptExecutor, request, llmConfig, eventsChannel = null)
    }

    /**
     * Analyze GitHub repository with streaming events
     *
     * @param promptExecutor Prompt executor
     * @param request Analysis request
     * @param llmConfig LLM configuration
     * @return Flow of events and final result
     */
    fun analyseGithubWithEvents(
        promptExecutor: PromptExecutor,
        request: GithubAnalysisRequest,
        llmConfig: LLMConfig,
    ): Flow<AnalysisEventOrResult> = flow {
        val eventsChannel = Channel<AnalysisEvent>(Channel.BUFFERED)

        // Запускаем анализ в отдельной coroutine
        coroutineScope {
            val analysisJob = async(Dispatchers.IO) {
                try {
                    analyseGithubBase(promptExecutor, request, llmConfig, eventsChannel)
                } catch (e: Exception) {
                    logger.error(e) { "Analysis failed with exception" }
                    eventsChannel.send(AnalysisEvent.Error(e.message ?: "Unknown error", false))
                    null
                } finally {
                    eventsChannel.close()
                }
            }

            // Пересылаем события из channel в Flow
            for (event in eventsChannel) {
                emit(AnalysisEventOrResult.Event(event))
            }

            // Получаем результат анализа
            val result = analysisJob.await()
            if (result != null) {
                emit(AnalysisEventOrResult.Result(result))
            } else {
                emit(AnalysisEventOrResult.Error("Analysis failed", null))
            }
        }
    }

    /**
     * Analyze repository structure and content
     *
     * This method:
     * - Clones GitHub repository
     * - Analyzes file structure, dependencies, build tools
     * - Generates summary of the codebase
     * - Optionally creates embeddings for RAG
     *
     * @param request Repository analysis request
     * @param promptExecutor Prompt executor for LLM calls (currently unused, agent is LLM-free)
     * @param provider LLM provider to use (currently unused, agent is LLM-free)
     * @return RepositoryAnalysisResult with analysis data
     */
    suspend fun analyzeRepository(
        request: ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisRequest,
        promptExecutor: PromptExecutor,
        provider: Provider? = null,
    ): ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisResult {
        return withContext(Dispatchers.IO) {
            logger.info { "Starting repository analysis for: ${request.githubUrl}" }
            logger.info { "  Analysis type: ${request.analysisType}" }
            logger.info { "  Enable embeddings: ${request.enableEmbeddings}" }

            // This agent is LLM-free, so we don't need a model or prompt
            // It uses rule-based analysis only
            val emptyPrompt = prompt(
                Prompt(emptyList(), "repository-analyzer", params = LLMParams())
            ) {}

            try {
                val strategy = ru.andvl.chatter.koog.agents.repoanalyzer.getRepositoryAnalyzerStrategy()

                // We don't need an actual LLM model since this agent doesn't use LLM
                // However, AIAgentConfig requires a model, so we provide a dummy one
                val dummyModel = OpenRouterModels.Gemini2_5Flash

                val agentConfig = AIAgentConfig(
                    prompt = emptyPrompt,
                    model = dummyModel,
                    maxAgentIterations = 50,
                )

                val agent = AIAgent(
                    promptExecutor = promptExecutor,
                    toolRegistry = ToolRegistry {},
                    strategy = strategy,
                    agentConfig = agentConfig,
                    id = "repository-analyzer",
                    installFeatures = {
                        install(Tracing) {
                            val outputPath = Path("./logs/koog_repository_analyzer_trace.log")
                            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
                            addMessageProcessor(TraceFeatureMessageFileWriter(
                                outputPath,
                                { path: Path -> SystemFileSystem.sink(path).buffered() }
                            ))
                        }
                    }
                )

                val result = agent.run(request)

                logger.info { "Repository analysis completed successfully" }
                logger.info { "  Repository: ${result.repositoryName}" }
                logger.info { "  Files: ${result.fileCount}" }
                logger.info { "  Languages: ${result.mainLanguages.joinToString(", ")}" }

                result
            } catch (e: Exception) {
                logger.error(e) { "Repository analysis failed" }
                ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisResult(
                    repositoryPath = "",
                    repositoryName = request.githubUrl,
                    summary = "Analysis failed",
                    fileCount = 0,
                    mainLanguages = emptyList(),
                    structureTree = "",
                    dependencies = emptyList(),
                    buildTool = null,
                    errorMessage = "Repository analysis failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Ask question about code in repository
     *
     * This method:
     * - Uses RAG to find relevant code
     * - Generates answer with code references
     * - Maintains conversation history
     *
     * @param request Code QA request with question and history
     * @param promptExecutor Prompt executor for LLM calls
     * @param provider LLM provider to use
     * @return CodeQAResponse with answer and code references
     */
    suspend fun askCodeQuestion(
        request: ru.andvl.chatter.shared.models.codeagent.CodeQARequest,
        promptExecutor: PromptExecutor,
        provider: Provider? = null,
    ): ru.andvl.chatter.shared.models.codeagent.CodeQAResponse {
        return withContext(Dispatchers.IO) {
            logger.info { "Starting Code QA for session: ${request.sessionId}" }
            logger.info { "  Question: ${request.question}" }
            logger.info { "  History size: ${request.history.size}" }

            // Select model based on provider (declare outside try-catch for error handling)
            val model = when (provider) {
                Provider.GOOGLE -> GoogleModels.Gemini2_5Flash
                else -> OpenRouterModels.Gemini2_5Flash
            }
            val modelName = model.id

            // Create prompt for Code QA agent
            val codeQaPrompt = prompt(
                Prompt(emptyList(), "code-qa-agent", params = LLMParams())
            ) {}

            try {
                val strategy = ru.andvl.chatter.koog.agents.codeqa.getCodeQaStrategy(model)

                val agentConfig = AIAgentConfig(
                    prompt = codeQaPrompt,
                    model = model,
                    maxAgentIterations = 30,
                )

                // Setup tools: RAG + GitHub MCP (if available)
                val toolRegistry = ToolRegistry {
                    // RAG tools for semantic search
                    tools(RagToolSet())
                    // File operations for fallback search
                    tools(FileOperationsToolSet())
                }

                // Configure RAG if session has indexed repository
                // Note: RagToolContext would be set up here in production
                // For now, it will be configured by the caller if needed

                val agent = AIAgent(
                    promptExecutor = promptExecutor,
                    toolRegistry = toolRegistry,
                    strategy = strategy,
                    agentConfig = agentConfig,
                    id = "code-qa-agent",
                    installFeatures = {
                        install(Tracing) {
                            val outputPath = Path("./logs/koog_code_qa_trace.log")
                            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
                            addMessageProcessor(TraceFeatureMessageFileWriter(
                                outputPath,
                                { path: Path -> SystemFileSystem.sink(path).buffered() }
                            ))
                        }
                    }
                )

                val result = agent.run(request)

                logger.info { "Code QA completed successfully" }
                logger.info { "  Answer length: ${result.answer.length} characters" }
                logger.info { "  Code references: ${result.codeReferences.size}" }
                logger.info { "  Confidence: ${result.confidence}" }

                result
            } catch (e: IllegalArgumentException) {
                // Session validation errors
                logger.error(e) { "Code QA validation failed" }
                ru.andvl.chatter.shared.models.codeagent.CodeQAResponse(
                    answer = "Error: ${e.message}",
                    codeReferences = emptyList(),
                    confidence = 0.0f,
                    model = modelName
                )
            } catch (e: Exception) {
                logger.error(e) { "Code QA failed with exception" }
                ru.andvl.chatter.shared.models.codeagent.CodeQAResponse(
                    answer = "I encountered an error while processing your question: ${e.message}. Please try rephrasing your question or check that the repository session is valid.",
                    codeReferences = emptyList(),
                    confidence = 0.0f,
                    model = modelName
                )
            }
        }
    }

    /**
     * Modify code using the Code Modifier Agent
     *
     * This method:
     * - Validates session and file scope
     * - Analyzes code context
     * - Generates detailed modification plan
     * - Validates syntax and detects breaking changes
     * - Returns proposed changes (does NOT apply them automatically)
     *
     * @param request Code modification request
     * @param promptExecutor Prompt executor for LLM calls
     * @param provider LLM provider to use
     * @return CodeModificationResponse with proposed changes
     */
    suspend fun modifyCode(
        request: ru.andvl.chatter.shared.models.codeagent.CodeModificationRequest,
        promptExecutor: PromptExecutor,
        provider: Provider? = null,
    ): ru.andvl.chatter.shared.models.codeagent.CodeModificationResponse {
        val model: LLModel = when (provider) {
            Provider.GOOGLE -> GoogleModels.Gemini2_5Flash
            Provider.OPENROUTER -> OpenRouterModels.Gemini2_5Flash
            else -> OpenRouterModels.Gemini2_5Flash
        }

        return withContext(Dispatchers.IO) {
            logger.info { "Starting code modification for session: ${request.sessionId}" }
            logger.info { "  Instructions: ${request.instructions.take(100)}..." }
            logger.info { "  File scope: ${request.fileScope?.size ?: 0} files" }

            val emptyPrompt = prompt(
                Prompt(emptyList(), "code-modifier-agent", params = LLMParams())
            ) {}

            try {
                val strategy = ru.andvl.chatter.koog.agents.codemodifier.getCodeModifierStrategy(model)

                val agentConfig = AIAgentConfig(
                    prompt = emptyPrompt,
                    model = model,
                    maxAgentIterations = 100,
                )

                val toolRegistry = ToolRegistry {
                    tools(FileOperationsToolSet())
                    tools(RagToolSet())
                }

                val agent = AIAgent(
                    promptExecutor = promptExecutor,
                    toolRegistry = toolRegistry,
                    strategy = strategy,
                    agentConfig = agentConfig,
                    id = "code-modifier-agent",
                    installFeatures = {
                        install(Tracing) {
                            val outputPath = Path("./logs/koog_code_modifier_trace.log")
                            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
                            addMessageProcessor(TraceFeatureMessageFileWriter(
                                outputPath,
                                { path: Path -> SystemFileSystem.sink(path).buffered() }
                            ))
                        }
                    }
                )

                // Convert shared model to internal model
                val internalRequest = ru.andvl.chatter.koog.model.codemodifier.CodeModificationRequest(
                    sessionId = request.sessionId,
                    instructions = request.instructions,
                    fileScope = request.fileScope,
                    enableValidation = request.enableValidation,
                    maxChanges = request.maxChanges
                )

                val result = agent.run(internalRequest)

                logger.info { "Code modification completed: ${if (result.success) "SUCCESS" else "FAILED"}" }
                logger.info { "  Files affected: ${result.totalFilesAffected}" }
                logger.info { "  Total changes: ${result.totalChanges}" }
                logger.info { "  Complexity: ${result.complexity}" }
                logger.info { "  Breaking changes: ${result.breakingChangesDetected}" }

                // Convert internal result to shared model
                ru.andvl.chatter.shared.models.codeagent.CodeModificationResponse(
                    success = result.success,
                    modificationPlan = result.modificationPlan?.let { plan ->
                        ru.andvl.chatter.shared.models.codeagent.ModificationPlan(
                            changes = plan.changes.map { change ->
                                ru.andvl.chatter.shared.models.codeagent.ProposedChange(
                                    changeId = change.changeId,
                                    filePath = change.filePath,
                                    changeType = change.changeType.name,
                                    description = change.description,
                                    startLine = change.startLine,
                                    endLine = change.endLine,
                                    newContent = change.newContent,
                                    oldContent = change.oldContent,
                                    dependsOn = change.dependsOn,
                                    validationNotes = change.validationNotes
                                )
                            },
                            rationale = plan.rationale,
                            estimatedComplexity = plan.estimatedComplexity.name,
                            dependenciesSorted = plan.dependenciesSorted
                        )
                    },
                    validationPassed = result.validationPassed,
                    breakingChangesDetected = result.breakingChangesDetected,
                    errorMessage = result.errorMessage,
                    totalFilesAffected = result.totalFilesAffected,
                    totalChanges = result.totalChanges,
                    complexity = result.complexity.name,
                    model = model.id
                )
            } catch (e: IllegalArgumentException) {
                logger.error(e) { "Code modification validation failed" }
                ru.andvl.chatter.shared.models.codeagent.CodeModificationResponse(
                    success = false,
                    modificationPlan = null,
                    validationPassed = false,
                    breakingChangesDetected = false,
                    errorMessage = "Validation error: ${e.message}",
                    totalFilesAffected = 0,
                    totalChanges = 0,
                    complexity = "SIMPLE",
                    model = model.id
                )
            } catch (e: Exception) {
                logger.error(e) { "Code modification failed with exception" }
                ru.andvl.chatter.shared.models.codeagent.CodeModificationResponse(
                    success = false,
                    modificationPlan = null,
                    validationPassed = false,
                    breakingChangesDetected = false,
                    errorMessage = "Code modification failed: ${e.message}",
                    totalFilesAffected = 0,
                    totalChanges = 0,
                    complexity = "SIMPLE",
                    model = model.id
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
