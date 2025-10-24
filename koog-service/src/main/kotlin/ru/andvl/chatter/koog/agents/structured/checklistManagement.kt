package ru.andvl.chatter.koog.agents.structured

import ai.koog.agents.core.dsl.builder.*
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import ru.andvl.chatter.koog.agents.Response
import ru.andvl.chatter.koog.model.ChatRequest
import ru.andvl.chatter.koog.model.IntentAnalysis
import ru.andvl.chatter.koog.model.StructuredResponse

internal fun AIAgentGraphStrategyBuilder<ChatRequest, Response>.infoCollectionSubgraph(
    mainSystemPrompt: String?
): AIAgentSubgraphDelegate<Pair<IntentAnalysis, ChatRequest>, Pair<Response, ChatRequest>> =
    subgraph<Pair<IntentAnalysis, ChatRequest>, Pair<Response, ChatRequest>>("info-collection") {
        val createOrUpdateChecklist by checklistManagementGraph(mainSystemPrompt)
        edge(nodeStart forwardTo createOrUpdateChecklist)
        edge(createOrUpdateChecklist forwardTo nodeFinish)
    }

internal fun AIAgentSubgraphBuilderBase<Pair<IntentAnalysis, ChatRequest>, Pair<Response, ChatRequest>>.checklistManagementGraph(
    mainSystemPrompt: String?,
): AIAgentNodeDelegate<Pair<IntentAnalysis, ChatRequest>, Pair<Response, ChatRequest>> {
    return node<Pair<IntentAnalysis, ChatRequest>, Pair<Response, ChatRequest>>("checklist-management") { request ->
        llm.writeSession {
            updatePrompt {
                prompt = prompt.copy(
                    params = prompt.params.copy(
                        temperature = null
                    )
                )
                model = model.copy(
                    capabilities = listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Speculation,
                        LLMCapability.Completion,
                    )
                )
                system(mainSystemPrompt ?: "") // Используем существующий системный промпт
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
                        contextLength = 16_000, //
                    ),
                    retries = 3
                )
            ) to request.second
        }
    }
}
