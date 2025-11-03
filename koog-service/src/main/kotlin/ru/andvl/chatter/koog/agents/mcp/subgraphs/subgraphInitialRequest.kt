package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.mcp.toolCallsKey
import ru.andvl.chatter.koog.agents.utils.FixingModelHolder
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.InitialPromptAnalysisModel
import ru.andvl.chatter.koog.model.tool.RequirementsAnalysisModel
import ru.andvl.chatter.koog.model.tool.ToolChatResponse

private val initialAnalysisKey = createStorageKey<InitialPromptAnalysisModel>("initial-analysis")

private val logger = LoggerFactory.getLogger("mcp")

internal fun AIAgentGraphStrategyBuilder<GithubChatRequest, ToolChatResponse>.subgraphGithubLLMRequest():
        AIAgentSubgraphDelegate<GithubChatRequest, InitialPromptAnalysisModel.SuccessAnalysisModel> =
    subgraph<GithubChatRequest, InitialPromptAnalysisModel.SuccessAnalysisModel>("initial-analysis") {
        val nodeInitialAnalysis by nodeInitialAnalysis()
        val nodeRequirementsCollection by nodeRequirementsCollection()
        val nodeProcessFinalRequirements by nodeProcessFinalRequirements()
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult("send-tool")

        edge(nodeStart forwardTo nodeInitialAnalysis)
        edge(nodeInitialAnalysis forwardTo nodeRequirementsCollection)
        edge(nodeRequirementsCollection forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeRequirementsCollection forwardTo nodeProcessFinalRequirements onAssistantMessage { true })
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeSendToolResult forwardTo nodeProcessFinalRequirements onAssistantMessage { true })
        edge(nodeProcessFinalRequirements forwardTo nodeFinish)
    }

// Node 1: Initial analysis of user request to extract GitHub URL and basic requirements
private fun AIAgentSubgraphBuilderBase<GithubChatRequest, InitialPromptAnalysisModel.SuccessAnalysisModel>.nodeInitialAnalysis() =
    node<GithubChatRequest, InitialPromptAnalysisModel>("initial-analysis") { request ->
        llm.writeSession {
            appendPrompt {
                system("""
                    You are an expert at analyzing user requests for GitHub repository information.

                    Your task is to parse the user's message and extract:
                    1. GitHub repository URL (must be a valid GitHub repository link)
                    2. What specific information the user wants to know about the repository
                    3. Google Docs URL if provided (for external requirements)
                    4. Basic requirements from user message (if any)

                    **IMPORTANT LANGUAGE REQUIREMENT:**
                    - Detect the language of the original user request
                    - All extracted requirements and descriptions should be in the SAME language as the original request
                    - If user writes in Russian, extract requirements in Russian
                    - If user writes in English, extract requirements in English
                    - Preserve the original language throughout all analysis

                    **Instructions:**
                    - If you can find a valid GitHub repository URL, return a SuccessAnalysisModel
                    - If the user provides a Google Docs link, include it in googleDocsUrl field
                    - Extract basic requirements from user message into RequirementsAnalysisModel if present
                    - If requirements need to be read from Google Docs, set requirements to null for now
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
                    )
                )
            }

            val response = requestLLMStructured<InitialPromptAnalysisModel>(
                examples = listOf(
                    InitialPromptAnalysisModel.SuccessAnalysisModel(
                        githubRepo = "openai/openai-python",
                        userRequest = "Analyze the repository - comprehensive analysis",
                        requirements = RequirementsAnalysisModel(
                            generalConditions = "Comprehensive analysis of OpenAI Python library",
                            importantConstraints = listOf("Focus on API usage patterns"),
                            additionalAdvantages = listOf("Well-documented code"),
                            attentionPoints = listOf("Check for deprecated methods")
                        ),
                        googleDocsUrl = null
                    ),
                    InitialPromptAnalysisModel.SuccessAnalysisModel(
                        githubRepo = "https://github.com/openai/openai-python",
                        userRequest = "Review according to requirements in Google Docs",
                        requirements = null, // Will be collected from Google Docs
                        googleDocsUrl = "https://docs.google.com/document/d/ABC123/edit"
                    ),
                    InitialPromptAnalysisModel.FailedAnalysisModel(
                        reason = "Repository was not provided"
                    )
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = FixingModelHolder.get(),
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
//                                    LLMCapability.ToolChoice,
                                )
                            )
                        )

                        appendPrompt {
                            system("""
                                You are an expert at extracting structured requirements from Google Docs documents.

                                Your task is to:
                                1. Use Google Docs MCP tools to read the document content
                                2. Extract and provide structured requirements based on the document content
                                3. Structure the requirements for GitHub repository analysis

                                **IMPORTANT LANGUAGE REQUIREMENT:**
                                - Detect the language of the document content
                                - Extract requirements in the SAME language as the document
                                - If document is in Russian, provide requirements in Russian
                                - If document is in English, provide requirements in English
                                - Preserve the original language and terminology from the document

                                After reading the document, provide a structured analysis of requirements you found.
                                Focus on extracting:
                                - General conditions (main task/assignment description)
                                - Important constraints (critical limitations to follow)
                                - Additional advantages (positive aspects for evaluation) 
                                - Attention points (things requiring careful human review)
                            """.trimIndent())

                            user("Please read the Google Docs document at ${initialAnalysis.googleDocsUrl} and extract structured requirements for analyzing GitHub repository")
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
private fun AIAgentSubgraphBuilderBase<GithubChatRequest, InitialPromptAnalysisModel.SuccessAnalysisModel>.nodeProcessFinalRequirements() =
    node<String, InitialPromptAnalysisModel.SuccessAnalysisModel>("process-final-requirements") { rawRequirementsData ->
        val initialAnalysis = storage.get(initialAnalysisKey) as InitialPromptAnalysisModel.SuccessAnalysisModel

        if (initialAnalysis.googleDocsUrl != null && initialAnalysis.requirements == null) {
            // Need to extract requirements from the LLM response about Google Docs
            llm.writeSession {
                appendPrompt {
                    system("""
                        Extract structured requirements from the analysis below and return a complete SuccessAnalysisModel.
                        
                        **IMPORTANT LANGUAGE REQUIREMENT:**
                        - Extract requirements in the SAME language as the original user request and Google Docs content
                        - Preserve the original terminology and language style
                        - Do not translate or change the language of requirements
                        
                        You need to create RequirementsAnalysisModel with:
                        - General conditions (main task/assignment description)
                        - Important constraints (critical limitations to follow)
                        - Additional advantages (positive aspects for evaluation)
                        - Attention points (things requiring careful human review)
                        
                        Use the original GitHub repository and user request, but update the requirements based on the Google Docs analysis.
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
                    fixingModel = FixingModelHolder.get(),
                    retries = 3
                )
                )

                response.getOrThrow().structure
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
