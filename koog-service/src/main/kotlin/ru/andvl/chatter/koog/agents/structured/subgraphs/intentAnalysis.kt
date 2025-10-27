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

internal fun AIAgentGraphStrategyBuilder<ChatRequest, Response>.intentAnalysisSubgraph(): AIAgentSubgraphDelegate<ChatRequest, Pair<IntentAnalysis, ChatRequest>> =
    subgraph<ChatRequest, Pair<IntentAnalysis, ChatRequest>>("intent-analysis") {
        val analyzeIntent by nodeIntentAnalysis()
        edge(nodeStart forwardTo analyzeIntent)
        edge(analyzeIntent forwardTo nodeFinish)
    }

internal inline fun AIAgentSubgraphBuilderBase<ChatRequest, Pair<IntentAnalysis, ChatRequest>>.nodeIntentAnalysis(): AIAgentNodeDelegate<ChatRequest, Pair<IntentAnalysis, ChatRequest>> =
    node<ChatRequest, Pair<IntentAnalysis, ChatRequest>> { request ->
        llm.writeSession {
            updatePrompt {
                model = LLModel(
                    provider = LLMProvider.OpenRouter,
                    id = "z-ai/glm-4.6", // "z-ai/glm-4.6", //"mistralai/mistral-tiny",
                    capabilities = listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Completion,
                    ),
                    contextLength = MAX_CONTEXT_LENGTH,
                )
                prompt = prompt.copy(
                    params = prompt.params.copy(
                        temperature = 0.0
                    )
                )
                system(
                    """
                Analyze the user's request and determine:
                1. Can it be answered directly (DIRECT_ANSWER) 
                2. Do we need to collect additional information through a checklist (COLLECT_INFO)
                
                Criteria for COLLECT_INFO:
                - User asks to create/build something complex
                - Request contains words like: "create", "build", "develop", "help make", "design", "implement"
                - Not enough details for a complete answer
                - Requires gathering requirements or specifications
                
                Criteria for DIRECT_ANSWER:
                - Simple questions
                - Requests for explanation or information
                - Sufficient information provided in the request
                - General knowledge questions
                """
                )
                user("${request.message}\n\nCurrent checklist: ${request.currentChecklist}")
            }
            requestLLMStructured<IntentAnalysis>(
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
                .getOrNull()!!
                .structure to request
        }.also {
            println("TOKENS USAGE: ${getLatestTokenUsage()}")
        }
    }
