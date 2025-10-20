package ru.andvl.chatter.koog.agents

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructureFixingParser
import ru.andvl.chatter.koog.model.ChatRequest
import ru.andvl.chatter.koog.model.StructuredResponse
import ai.koog.prompt.structure.StructuredResponse as StructuredResponseKoog

internal fun getStructuredAgentPrompt(
    systemPrompt: String,
    request: ChatRequest,
    temperature: Double? = null,
): Prompt {
    return prompt(Prompt(
        emptyList(),
        "structured",
        params = LLMParams(
            temperature = temperature
        )
    )) {
        system {
            text(systemPrompt)
        }
        messages(request.history)
    }
}

internal fun getStructuredAgentStrategy() = strategy("structured") {
    val setup by nodeLLMRequest()
    val getStructuredResponse by node<Message.Response, Result<StructuredResponseKoog<StructuredResponse>>>(
        name = "structured",
    ) { _ ->
        val response = llm.writeSession {
            requestLLMStructured<StructuredResponse>(
                fixingParser = StructureFixingParser(
                    fixingModel = OpenRouterModels.GPT5Nano,
                    retries = 3
                )
            )
        }
        response
    }
    edge(nodeStart forwardTo setup)
    edge(setup forwardTo getStructuredResponse)
    edge(getStructuredResponse forwardTo nodeFinish)
}