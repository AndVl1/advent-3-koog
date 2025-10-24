package ru.andvl.chatter.koog.agents

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams
import ru.andvl.chatter.koog.agents.structured.*
import ru.andvl.chatter.koog.model.*
import ai.koog.prompt.structure.StructuredResponse as StructuredResponseKoog

internal typealias Response = Result<StructuredResponseKoog<StructuredResponse>>

internal fun getStructuredAgentPrompt(
    systemPrompt: String,
    request: ChatRequest,
    temperature: Double? = null,
): Prompt {
    return prompt(
        Prompt(
            emptyList(),
            "structured",
            params = LLMParams(
                temperature = temperature
            )
        )
    ) {
        system {
            text(systemPrompt)
        }
        messages(request.history)
    }
}

internal fun getStructuredAgentStrategy(
    mainSystemPrompt: String? = null,
): AIAgentGraphStrategy<ChatRequest, Response> = strategy("structured") {
    val intentAnalysis: AIAgentSubgraph<ChatRequest, Pair<IntentAnalysis, ChatRequest>> by intentAnalysisSubgraph()
    val directAnswer: AIAgentSubgraph<Pair<IntentAnalysis, ChatRequest>, Response> by directAnswerSubgraph()
    val infoCollection: AIAgentSubgraph<Pair<IntentAnalysis, ChatRequest>, Pair<Response, ChatRequest>> by infoCollectionSubgraph(mainSystemPrompt)
    val completionCheck: AIAgentSubgraph<Pair<Response, ChatRequest>, Triple<CompletionStatus, ChatRequest, Response>> by completionCheckSubgraph()
    val finalAnswer: AIAgentSubgraph<Triple<CompletionStatus, ChatRequest, Response>, Response> by finalAnswerSubgraph()

    // Стартуем с анализа намерения
    edge(nodeStart forwardTo intentAnalysis)

    // Условное ветвление на основе анализа
    edge(intentAnalysis forwardTo directAnswer onCondition { intent ->
        intent.first.intentType == IntentType.DIRECT_ANSWER
    })

    edge(intentAnalysis forwardTo infoCollection onCondition  { intent ->
        intent.first.intentType == IntentType.COLLECT_INFO
    })

    // Проверяем завершенность после сбора информации
    edge(infoCollection forwardTo completionCheck)

    // Если чеклист не заполнен - возвращаем вопросы
    edge(completionCheck forwardTo nodeFinish onCondition  { status ->
        status.first.requiresMoreInfo
    } transformed { it.third })

    // Если все готово - генерируем финальный ответ
    edge(completionCheck forwardTo finalAnswer onCondition  { status ->
        status.first.readyForFinalAnswer
    } transformed { Triple(it.first, it.second, it.third) })

    // Прямые ответы и финальные ответы идут сразу на выход
    edge(directAnswer forwardTo nodeFinish)
    edge(finalAnswer forwardTo nodeFinish)
}
