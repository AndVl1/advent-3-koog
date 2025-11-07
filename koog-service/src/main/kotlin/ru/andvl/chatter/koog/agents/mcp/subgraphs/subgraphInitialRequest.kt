package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.feature.withMemory
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.mcp.toolCallsKey
import ru.andvl.chatter.koog.agents.memory.*
import ru.andvl.chatter.koog.mcp.McpProvider
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.InitialPromptAnalysisModel
import ru.andvl.chatter.koog.model.tool.RequirementsAnalysisModel
import ru.andvl.chatter.koog.model.tool.ToolChatResponse

private val initialAnalysisKey = createStorageKey<InitialPromptAnalysisModel>("initial-analysis")
internal val initialGithubRequestKey = createStorageKey<GithubChatRequest>("initial-github-request")

private val logger = LoggerFactory.getLogger("mcp")

internal suspend fun AIAgentGraphStrategyBuilder<GithubChatRequest, ToolChatResponse>.subgraphGithubLLMRequest(
    fixingModel: LLModel
): AIAgentSubgraphDelegate<GithubChatRequest, InitialPromptAnalysisModel.SuccessAnalysisModel> =
    subgraph<GithubChatRequest, InitialPromptAnalysisModel.SuccessAnalysisModel>(
        "initial-analysis",
        tools = McpProvider.getGoogleDocsToolsRegistry().tools
    ) {
        val saveRequirements by nodeSaveRequirementsFromLastMessage(
            name = "save-requirements",
            subject = MemorySubjects.HomeworkRequirements,
            scope = MemoryScopeType.AGENT
        )

        val load by node<InitialPromptAnalysisModel, InitialPromptAnalysisModel>("load-memory") { initial ->
            if (initial !is InitialPromptAnalysisModel.SuccessAnalysisModel) return@node initial
            withMemory {
                val concepts = listOf(
                    getGeneralConditionsConcept(initial.googleDocsUrl),
                    getImportantConstraintsConcept(initial.googleDocsUrl),
                    getAdditionalAdvantagesConcept(initial.googleDocsUrl),
                    getAttentionPointsConcept(initial.googleDocsUrl),
                )
                concepts.forEach {
                    loadFactsToAgent(
                        llm, it, listOf(MemoryScopeType.AGENT), subjects = listOf(MemorySubjects.HomeworkRequirements),
                    )
                }
            }
            initial
        }

        val nodeInitialAnalysis by nodeInitialAnalysis(fixingModel)
        val nodeRequirementsCollection by nodeRequirementsCollection()
        val nodeProcessFinalRequirements by nodeProcessFinalRequirements(fixingModel)
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult("send-tool")

        edge(nodeStart forwardTo nodeInitialAnalysis)
        edge(nodeInitialAnalysis forwardTo load)
        edge(load forwardTo nodeRequirementsCollection)

        edge(nodeRequirementsCollection forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeRequirementsCollection forwardTo nodeProcessFinalRequirements onAssistantMessage { true })
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeSendToolResult forwardTo nodeProcessFinalRequirements onAssistantMessage { true })

        edge(nodeProcessFinalRequirements forwardTo saveRequirements)
        edge(saveRequirements forwardTo nodeFinish)
    }

// Node 1: Initial analysis of user request to extract GitHub URL and basic requirements
private fun AIAgentSubgraphBuilderBase<GithubChatRequest, InitialPromptAnalysisModel.SuccessAnalysisModel>.nodeInitialAnalysis(
    fixingModel: LLModel
) =
    node<GithubChatRequest, InitialPromptAnalysisModel>("initial-analysis") { request ->
        // Store the original request for use in later subgraphs (like Google Sheets)
        storage.set(initialGithubRequestKey, request)

        llm.writeSession {
            appendPrompt {
                system("""
                    You are an expert at analyzing user requests for GitHub repository information.

                    **YOUR TASK:**
                    Parse the user's message and extract ONLY:
                    1. GitHub repository URL (must be a valid GitHub repository link)
                    2. What specific information the user wants to know about the repository
                    3. Google Docs URL if provided (for external requirements)

                    **IMPORTANT:**
                    - DO NOT extract or analyze requirements at this stage
                    - Requirements will be collected later using MCP tools
                    - ALWAYS set requirements field to null
                    - This step is ONLY for URL extraction

                    **LANGUAGE REQUIREMENT:**
                    - Detect the language of the original user request
                    - Extract user request description in the SAME language as the original request
                    - If user writes in Russian, describe request in Russian
                    - If user writes in English, describe request in English

                    **Instructions:**
                    - If you can find a valid GitHub repository URL, return a SuccessAnalysisModel
                    - If the user provides a Google Docs link, include it in googleDocsUrl field
                    - ALWAYS set requirements to null (requirements collection happens in next steps)
                    - If the GitHub URL is missing or invalid, return a FailedAnalysisModel
                    - Use JSON for return structured result

                    **Valid GitHub URL formats:**
                    - https://github.com/owner/repository
                    - github.com/owner/repository
                    - owner/repository (if clearly referring to GitHub)

                    **Valid Google Docs URL formats:**
                    - https://docs.google.com/document/d/DOCUMENT_ID/edit
                    - https://docs.google.com/document/d/DOCUMENT_ID
                """.trimIndent())

                user("User request: ${request.message}")

                model = model.copy(
//                    id = "qwen/qwen3-coder", //"openai/gpt-5-nano", // "qwen/qwen3-coder"
                    capabilities = listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Completion,
                        LLMCapability.Tools,
                        LLMCapability.OpenAIEndpoint.Completions
                    )
                )
            }

            val response = requestLLMStructured<InitialPromptAnalysisModel>(
                examples = listOf(
                    InitialPromptAnalysisModel.SuccessAnalysisModel(
                        githubRepo = "openai/openai-python",
                        userRequest = "Analyze the repository - comprehensive analysis",
                        requirements = null, // Requirements are NOT collected at this stage
                        googleDocsUrl = null
                    ),
                    InitialPromptAnalysisModel.SuccessAnalysisModel(
                        githubRepo = "https://github.com/openai/openai-python",
                        userRequest = "Review according to requirements in Google Docs",
                        requirements = null, // Requirements will be collected in next steps
                        googleDocsUrl = "https://docs.google.com/document/d/ABC123/edit"
                    ),
                    InitialPromptAnalysisModel.FailedAnalysisModel(
                        reason = "Repository was not provided"
                    )
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = fixingModel,
                    retries = 3
                )
            )

            val result = if (response.isSuccess) {
                response.getOrThrow().structure
            } else {
                InitialPromptAnalysisModel.FailedAnalysisModel(
                    response.exceptionOrNull()?.message ?: "Unknown error"
                )
            }

            storage.set(initialAnalysisKey, result)
            result
        }
    }

// Node 2: Requirements collection (from Google Docs if needed)
private fun AIAgentSubgraphBuilderBase<GithubChatRequest, InitialPromptAnalysisModel.SuccessAnalysisModel>.nodeRequirementsCollection() =
    node<InitialPromptAnalysisModel, Message.Response>("requirements-collection") { initialAnalysis ->
        when (initialAnalysis) {
            is InitialPromptAnalysisModel.FailedAnalysisModel -> {
                throw IllegalStateException("Cannot proceed with failed analysis: ${initialAnalysis.reason}")
            }
            is InitialPromptAnalysisModel.SuccessAnalysisModel -> {
                if (initialAnalysis.googleDocsUrl != null && initialAnalysis.requirements == null) {
                    // Need to read requirements from Google Docs
                    llm.writeSession {
                        changeModel(
                            newModel = model.copy(
//                                id = "qwen/qwen3-coder", //"openai/gpt-5-nano", // "qwen/qwen3-coder"
                                capabilities = listOf(
                                    LLMCapability.Temperature,
                                    LLMCapability.Completion,
                                    LLMCapability.Tools,
                                    LLMCapability.OpenAIEndpoint.Completions
//                                    LLMCapability.ToolChoice,
                                )
                            )
                        )

                        appendPrompt {
                            system("""
                                You are an expert at extracting structured requirements from Google Docs documents.

                                **MEMORY CHECK FIRST:**
                                Before using any tools, check if the conversation context already contains structured requirements for this Google Doc URL.
                                - Look for RequirementsAnalysisModel with general conditions, constraints, advantages, and attention points
                                - If complete requirements are already present in the context, DO NOT use MCP tools
                                - Instead, proceed directly to finalizing the analysis with existing data

                                **YOUR SOLE TASK:**
                                Only if requirements are NOT found in context:
                                Read and extract requirements from the provided Google Docs document. DO NOT analyze any code or search for solutions at this stage.

                                **PROCESS:**
                                1. First: Check context for existing requirements
                                2. If found: Skip Google Docs reading, use existing requirements
                                3. If not found: Use Google Docs MCP tools to read the document
                                4. Extract requirements exactly as written in the document
                                5. Structure the requirements for later GitHub repository analysis
                                6. DO NOT use any tools to search GitHub or analyze code
                                7. DO NOT attempt to find if requirements are met in the repository

                                **IMPORTANT:**
                                - Check context FIRST before making any tool calls
                                - If requirements are already loaded from memory, DO NOT call Google Docs MCP tools
                                - Your tools should be used ONLY for reading the requirements document when needed
                                - DO NOT look for code examples or implementations
                                - DO NOT search for files or code that satisfy requirements
                                - Focus ONLY on understanding what the requirements are
                                - The actual verification will happen in a separate analysis stage

                                **LANGUAGE REQUIREMENT:**
                                - Detect the language of the document content or existing requirements
                                - Extract requirements in the SAME language as the document
                                - If document is in Russian, provide requirements in Russian
                                - If document is in English, provide requirements in English
                                - Preserve the original language and terminology from the document

                                **WHAT TO EXTRACT:**
                                - General conditions (main task/assignment description)
                                - Important constraints (critical limitations to follow)
                                - Additional advantages (positive aspects for evaluation)
                                - Attention points (things requiring careful human review)

                                Remember: Check memory context first, collect requirements only if not already available.
                            """.trimIndent())

                            user("Please read the Google Docs document at ${initialAnalysis.googleDocsUrl} and extract all requirements. Do NOT analyze any code - just collect the requirements as written in the document.")
                        }

                        // This will trigger tool calls to read Google Docs
                        requestLLM()
                    }
                } else {
                    // Requirements already present or no Google Docs - return immediately
                    llm.writeSession {
                        appendPrompt {
                            system("Return the final requirements analysis based on the already collected data.")
                            user("Requirements collection complete.")
                        }
                        requestLLM()
                    }
                }
            }
        }
    }

// Node 4: Process final requirements and return complete analysis
private fun AIAgentSubgraphBuilderBase<GithubChatRequest, InitialPromptAnalysisModel.SuccessAnalysisModel>.nodeProcessFinalRequirements(
    fixingModel: LLModel
) =
    node<String, InitialPromptAnalysisModel.SuccessAnalysisModel>("process-final-requirements") { rawRequirementsData ->
        val initialAnalysis = storage.get(initialAnalysisKey) as InitialPromptAnalysisModel.SuccessAnalysisModel

        if (initialAnalysis.googleDocsUrl != null && initialAnalysis.requirements == null) {
            // Need to extract requirements from the LLM response about Google Docs
            llm.writeSession {
                appendPrompt {
                    system("""
                        Extract structured requirements from the Google Docs analysis below and return a complete SuccessAnalysisModel.

                        **YOUR TASK:**
                        You are finalizing the requirements collection phase. Structure the extracted requirements without analyzing any code.

                        **IMPORTANT:**
                        - This is ONLY requirements extraction, NOT code analysis
                        - DO NOT mention code files or implementations
                        - DO NOT verify if requirements are met
                        - Just organize the requirements that were read from the document

                        **LANGUAGE REQUIREMENT:**
                        - Extract requirements in the SAME language as the original user request and Google Docs content
                        - Preserve the original terminology and language style
                        - Do not translate or change the language of requirements

                        **CREATE RequirementsAnalysisModel WITH:**
                        - General conditions (main task/assignment description)
                        - Important constraints (critical limitations to follow)
                        - Additional advantages (positive aspects for evaluation)
                        - Attention points (things requiring careful human review)

                        Use the original GitHub repository and user request, but structure the requirements based on the Google Docs content.
                    """.trimIndent())

                    user("""
                        Original analysis:
                        - GitHub Repository: ${initialAnalysis.githubRepo}
                        - User Request: ${initialAnalysis.userRequest}
                        - Google Docs URL: ${initialAnalysis.googleDocsUrl}
                        
                        Requirements analysis from Google Docs:
                        $rawRequirementsData
                        
                        Please return a complete SuccessAnalysisModel with structured requirements.
                    """.trimIndent())
                }

                val response = requestLLMStructured<InitialPromptAnalysisModel.SuccessAnalysisModel>(
                    examples = listOf(
                        InitialPromptAnalysisModel.SuccessAnalysisModel(
                            githubRepo = "openai/openai-python",
                            userRequest = "Analyze according to requirements",
                            requirements = RequirementsAnalysisModel(
                                generalConditions = "Security audit of the library",
                                importantConstraints = listOf("No hardcoded secrets", "Proper input validation"),
                                additionalAdvantages = listOf("Security-focused documentation"),
                                attentionPoints = listOf("Authentication handling", "Data sanitization")
                            ),
                            googleDocsUrl = "https://docs.google.com/document/d/ABC123/edit"
                        )
                    ),
                    fixingParser = StructureFixingParser(
                        fixingModel = fixingModel,
                        retries = 3
                    )
                )

                val structure = response.getOrThrow().structure

                withMemory {

                }

                structure
            }
        } else {
            // Requirements already collected
            initialAnalysis
        }
    }

// Node 3: Execute tool calls
private fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
    name: String? = null
): AIAgentNodeDelegate<Message.Tool.Call, ReceivedToolResult> =
    node(name) { toolCall ->
        val currentCalls = storage.get(toolCallsKey) ?: emptyList()
        storage.set(toolCallsKey, currentCalls + "${toolCall.tool} ${toolCall.content}")

        environment.executeTool(toolCall)
    }
