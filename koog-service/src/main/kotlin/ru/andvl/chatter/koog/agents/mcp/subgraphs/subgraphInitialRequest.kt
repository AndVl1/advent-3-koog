package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import ru.andvl.chatter.koog.agents.utils.MAX_CONTEXT_LENGTH
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.InitialPromptAnalysisModel
import ru.andvl.chatter.koog.model.tool.ToolChatResponse

internal fun AIAgentGraphStrategyBuilder<GithubChatRequest, ToolChatResponse>.subgraphGithubLLMRequest():
        AIAgentSubgraphDelegate<GithubChatRequest, Pair<InitialPromptAnalysisModel, Message.Assistant?>> =
    subgraph<GithubChatRequest, Pair<InitialPromptAnalysisModel, Message.Assistant?>>("initial-analysis") {
        val githubNode by nodeGithubLLMRequest()
        edge(nodeStart forwardTo githubNode)
        edge(githubNode forwardTo nodeFinish)
    }

private fun AIAgentSubgraphBuilderBase<GithubChatRequest, Pair<InitialPromptAnalysisModel, Message.Assistant?>>.nodeGithubLLMRequest() =
    node<GithubChatRequest, Pair<InitialPromptAnalysisModel, Message.Assistant?>>("initial-analysis") { request ->
        llm.writeSession {
            updatePrompt {
                system("""
                    You are an expert at analyzing user requests for GitHub repository information.
                    
                    Your task is to parse the user's message and extract:
                    1. GitHub repository URL (must be a valid GitHub repository link)
                    2. What specific information the user wants to know about the repository
                    
                    **Instructions:**
                    - If you can find a valid GitHub repository URL and understand what the user wants, return a SuccessAnalysisModel
                    - If the GitHub URL is missing, invalid, or the request is unclear, return a FailedAnalysisModel with a helpful explanation
                    
                    **Valid GitHub URL formats:**
                    - https://github.com/owner/repository
                    - github.com/owner/repository
                    - owner/repository (if clearly referring to GitHub)
                    
                    **Examples of user requests to analyze:**
                    - "Analyze https://github.com/microsoft/vscode - I want to know about its architecture"
                    - "Tell me about the dependencies in facebook/react repository"
                    - "What's the project structure of https://github.com/tensorflow/tensorflow?"
                    
                    Be strict about requiring a valid GitHub repository reference and clear user intent.
                """.trimIndent())

                user("User request: ${request.message}")

                model = model.copy(
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
                        userRequest = "Analyze the repository - the user wants a comprehensive analysis of the OpenAI Python library repository"
                    ),
                    InitialPromptAnalysisModel.SuccessAnalysisModel(
                        githubRepo = "https://github.com/openai/openai-python",
                        userRequest = "Analyze the repository - the user wants a comprehensive analysis of the OpenAI Python library repository"
                    ),
                    InitialPromptAnalysisModel.SuccessAnalysisModel(
                        githubRepo = "github.com/openai/openai-python",
                        userRequest = "Analyze the repository - the user wants a comprehensive analysis of the OpenAI Python library repository"
                    ),
                    InitialPromptAnalysisModel.FailedAnalysisModel(
                        reason = "Repository was not provided"
                    )
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
                response.getOrThrow().let {
                    it.structure to it.message
                }
            } else {
                InitialPromptAnalysisModel.FailedAnalysisModel(
                    response.exceptionOrNull().toString()
                ) to null
            }
        }
}
