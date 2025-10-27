package ru.andvl.chatter.koog.agents.structured.subgraphs

import ai.koog.agents.core.dsl.builder.*
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import ru.andvl.chatter.koog.agents.structured.Response
import ru.andvl.chatter.koog.agents.utils.MAX_CONTEXT_LENGTH
import ru.andvl.chatter.koog.agents.utils.getLatestTokenUsage
import ru.andvl.chatter.koog.model.ChatRequest
import ru.andvl.chatter.koog.model.IntentAnalysis
import ru.andvl.chatter.koog.model.StructuredResponse

internal fun AIAgentGraphStrategyBuilder<ChatRequest, Response>.directAnswerSubgraph(): AIAgentSubgraphDelegate<Pair<IntentAnalysis, ChatRequest>, Response> =
    subgraph<Pair<IntentAnalysis, ChatRequest>, Response>("direct-answer") {
        val generateDirectAnswer by directSubgraph()
        edge(nodeStart forwardTo generateDirectAnswer)
        edge(generateDirectAnswer forwardTo nodeFinish)
    }

internal inline fun AIAgentSubgraphBuilderBase<Pair<IntentAnalysis, ChatRequest>, Response>.directSubgraph(): AIAgentNodeDelegate<Pair<IntentAnalysis, ChatRequest>, Response> {
    return node<Pair<IntentAnalysis, ChatRequest>, Response>("generate-answer") { request ->
        llm.writeSession {
            updatePrompt {
                model = LLModel(
                    provider = LLMProvider.OpenRouter,
                    id = "qwen/qwen3-8b",
                    capabilities = listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Speculation,
                        LLMCapability.Completion,
                    ),
                    contextLength = MAX_CONTEXT_LENGTH,
                )
                prompt = prompt.copy(
                    params = prompt.params.copy(
                        temperature = 0.0
                    )
                )
                system("""
                Provide a complete and comprehensive answer to the user's question.
                Use a structured format with title and message.
                Leave the checklist empty since no additional information is needed.
                
                IMPORTANT: Always respond in the same language as the user's request.
                """)
                user(request.second.message)
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
