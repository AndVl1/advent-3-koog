package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import ru.andvl.chatter.koog.agents.utils.MAX_CONTEXT_LENGTH
import ru.andvl.chatter.koog.model.common.TokenUsage
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.GithubRepositoryAnalysisModel
import ru.andvl.chatter.koog.model.tool.InitialPromptAnalysisModel
import ru.andvl.chatter.koog.model.tool.ToolChatResponse

private val toolCallsKey = createStorageKey<List<String>>("tool-calls")

internal fun AIAgentGraphStrategyBuilder<GithubChatRequest, ToolChatResponse>.subgraphGithubAnalyze():
        AIAgentSubgraphDelegate<InitialPromptAnalysisModel.SuccessAnalysisModel, ToolChatResponse> =
    subgraph("github-analysis") {
        val nodeGithubRequest by nodeGithubRequest()
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult("send-tool")
        val nodeProcessResult by nodeProcessResult()

        edge(nodeStart forwardTo nodeGithubRequest)
        edge(nodeGithubRequest forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeGithubRequest forwardTo nodeProcessResult onAssistantMessage { true })
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeSendToolResult forwardTo nodeProcessResult onAssistantMessage { true })
        edge(nodeProcessResult forwardTo nodeFinish)

    }

private fun AIAgentSubgraphBuilderBase<InitialPromptAnalysisModel.SuccessAnalysisModel, ToolChatResponse>.nodeGithubRequest() =
    node<InitialPromptAnalysisModel.SuccessAnalysisModel, Message.Response>("process-user-request") { request ->
        llm.writeSession {
            updatePrompt {
                system("""
                    You are a GitHub repository analysis expert with access to GitHub API tools.
                    
                    Your task is to thoroughly analyze the requested GitHub repository and gather comprehensive information to answer the user's specific questions.
                    
                    **Available Tools:**
                    Use the GitHub MCP tools to collect information about:
                    - Repository metadata (name, description, stars, forks, topics, license)
                    - README and documentation files
                    - File structure and directory contents
                    - Dependencies (package.json, requirements.txt, pom.xml, etc.)
                    - Recent commits and activity
                    - Issues and pull requests (if relevant)
                    - Code samples from key files
 
                    **Analysis Strategy:**
                    1. Start with basic repository information
                    2. Examine the README for project overview
                    3. Analyze the file structure to understand project organization
                    4. Check dependencies and build configuration
                    5. Look at recent activity and development status
                    6. Focus on specific aspects mentioned in the user's request

                    **Important:**
                    - Use multiple tool calls to gather comprehensive information
                    - Be systematic in your approach
                    - Collect relevant code snippets when analyzing technical aspects
                    - Pay attention to the user's specific questions and interests
                    - Try to use no more then 10 tool calls

                    Proceed with gathering information about the repository systematically.
                """.trimIndent())

                user("""
                    Please analyze the GitHub repository: ${request.githubRepo}
                    
                    User's specific request: ${request.userRequest}
                    
                    Use the available GitHub tools to collect comprehensive information and focus on answering the user's specific questions.
                """.trimIndent())

                model = model.copy(
                    capabilities = listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Completion,
                        LLMCapability.Tools,
                        LLMCapability.ToolChoice,
                    )
                )
            }

            requestLLM()
        }
    }

private fun AIAgentSubgraphBuilderBase<InitialPromptAnalysisModel.SuccessAnalysisModel, ToolChatResponse>.nodeProcessResult() =
    node<String, ToolChatResponse>("process-llm-result") { request ->
        llm.writeSession {
            updatePrompt {
                system("""
                    You are an expert at synthesizing GitHub repository analysis results into clear, structured reports.
                    
                    Your task is to process the raw analysis data and create a comprehensive, well-organized response.
                    
                    **Structure your response with these sections:**
                    
                    # Repository Overview
                    - Name, description, and basic metrics
                    - Primary programming language and tech stack
                    - License and maintenance status
                    
                    # Project Structure & Architecture
                    - Directory organization
                    - Key files and their purposes
                    - Architecture patterns identified
                    
                    # Dependencies & Build System
                    - Main dependencies and frameworks
                    - Build tools and configuration
                    - Development and runtime requirements
                    
                    # Key Features & Functionality
                    - Main features and capabilities
                    - Notable code patterns or implementations
                    - API structure (if applicable)
                    
                    # Development Activity
                    - Recent commits and changes
                    - Contributor activity
                    - Issue and PR status
                    
                    # Analysis Summary
                    - Code quality indicators
                    - Documentation quality
                    - Recommendations or insights
                    - Direct answers to user's specific questions

                    **Guidelines:**
                    - Use clear, professional language
                    - Include specific details and examples where relevant
                    - Highlight interesting or notable aspects
                    - Be concise but comprehensive
                    - Focus on information that would be valuable to developers
                """.trimIndent())

                user("""
                    Based on the GitHub repository analysis below, please create a structured, comprehensive report.
                    
                    Raw analysis data: $request
                """.trimIndent())
            }
            val totalToolCalls = storage.get(toolCallsKey).orEmpty()

            val response = requestLLMStructured<GithubRepositoryAnalysisModel>(
                examples = listOf(
                    GithubRepositoryAnalysisModel.SuccessAnalysisModel("The repository userName/repoName is about something"),
                    GithubRepositoryAnalysisModel.FailedAnalysisModel("Request was failed because of ...")
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = LLModel(
                        provider = LLMProvider.OpenRouter,
                        id = "z-ai/glm-4.6", // z-ai/glm-4.6 mistralai/mistral-7b-instruct google/gemma-3n-e4b-it
                        capabilities = listOf(
                            LLMCapability.Temperature,
                            LLMCapability.Completion,
                        ),
                        contextLength = MAX_CONTEXT_LENGTH,
                    ),
                    retries = 3
                )
            )
            if (response.isSuccess) {
                val resp = response.getOrThrow()
                ToolChatResponse(
                    response = resp.structure.toString(),
                    toolCalls = totalToolCalls,
                    originalMessage = resp.message,
                    tokenUsage = TokenUsage(
                        promptTokens = resp.message.metaInfo.inputTokensCount ?: 0,
                        completionTokens = resp.message.metaInfo.outputTokensCount ?: 0,
                        totalTokens = resp.message.metaInfo.totalTokensCount ?: 0
                    )

                )
            } else {
                ToolChatResponse(
                    response = "Request finished with error: ${response.exceptionOrNull()?.message}",
                    toolCalls = totalToolCalls,
                    originalMessage = null,
                    tokenUsage = TokenUsage(0, 0, 0)
                )
            }
        }
    }

private fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
    name: String? = null
): AIAgentNodeDelegate<Message.Tool.Call, ReceivedToolResult> =
    node(name) { toolCall ->
        val currentCalls = storage.get(toolCallsKey) ?: emptyList()
        storage.set(toolCallsKey, currentCalls + "${toolCall.tool} ${toolCall.content}")

        environment.executeTool(toolCall)
    }
