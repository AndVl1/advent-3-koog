package ru.andvl.chatter.koog.agents.structured.subgraphs

import ai.koog.agents.core.dsl.builder.*
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import ru.andvl.chatter.koog.agents.structured.Response
import ru.andvl.chatter.koog.agents.utils.MAX_CONTEXT_LENGTH
import ru.andvl.chatter.koog.agents.utils.getLatestTokenUsage
import ru.andvl.chatter.koog.model.structured.ChatRequest
import ru.andvl.chatter.koog.model.structured.CompletionStatus
import ru.andvl.chatter.koog.model.structured.StructuredResponse

internal fun AIAgentGraphStrategyBuilder<ChatRequest, Response>.finalAnswerSubgraph(): AIAgentSubgraphDelegate<Triple<CompletionStatus, ChatRequest, Response>, Response> =
    subgraph<Triple<CompletionStatus, ChatRequest, Response>, Response>("final-answer") {
        val generateFinalAnswer by finalAgentNode()
        edge(nodeStart forwardTo generateFinalAnswer)
        edge(generateFinalAnswer forwardTo nodeFinish)
    }

internal fun AIAgentSubgraphBuilderBase<Triple<CompletionStatus, ChatRequest, Response>, Response>.finalAgentNode(): AIAgentNodeDelegate<Triple<CompletionStatus, ChatRequest, Response>, Response> {
    return node<Triple<CompletionStatus, ChatRequest, Response>, Response>("final-answer") { (_, request, resolvedChecklist) ->
        llm.writeSession {
            updatePrompt {
                system(
                    """
                Now you have all the necessary information from the checklist.
                Create a final, detailed answer with specific steps and recommendations.
                Do not create a new checklist - provide complete instructions.
                
                IMPORTANT: Always respond in the same language as the user's original request.
                """
                )
                user(
                    """
                Original request: ${request.message}
                Collected information: ${resolvedChecklist.getOrNull()!!.structure.checkList.joinToString("\n") { "- ${it.point}: ${it.resolution}" }}
                """
                )
            }
            requestLLMStructured<StructuredResponse>(
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
        }.also {
            println("TOKENS USAGE: ${getLatestTokenUsage()}")
        }
    }
}
