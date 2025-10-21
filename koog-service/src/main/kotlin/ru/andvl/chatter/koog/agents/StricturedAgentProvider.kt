package ru.andvl.chatter.koog.agents

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructureFixingParser
import ru.andvl.chatter.koog.model.*
import ai.koog.prompt.structure.StructuredResponse as StructuredResponseKoog

private typealias Response = Result<StructuredResponseKoog<StructuredResponse>>

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
    val intentAnalysis by subgraph<ChatRequest, Pair<IntentAnalysis, ChatRequest>>("intent-analysis") {
        val analyzeIntent by node<ChatRequest, Pair<IntentAnalysis, ChatRequest>>("analyze-intent") { request ->
            llm.writeSession {
                updatePrompt {
                    system(
                        """
                Проанализируй запрос пользователя и определи:
                1. Можно ли ответить сразу (DIRECT_ANSWER) 
                2. Нужно ли собрать дополнительную информацию через чеклист (COLLECT_INFO)
                
                Критерии для COLLECT_INFO:
                - Пользователь просит создать/построить что-то сложное
                - Запрос содержит слова: "создать", "построить", "разработать", "помочь сделать"
                - Недостаточно деталей для полного ответа
                
                Критерии для DIRECT_ANSWER:
                - Простые вопросы
                - Запросы на объяснение
                - Достаточно информации в запросе
                """
                    )
                    user("${request.message}\n\nТекущий чеклист: ${request.currentChecklist}")
                }
                requestLLMStructured<IntentAnalysis>(
                    fixingParser = StructureFixingParser(
                        fixingModel = OpenRouterModels.GPT5Nano,
                        retries = 3
                    )
                )
                    .getOrNull()!!
                    .structure to request
            }
        }
        edge(nodeStart forwardTo analyzeIntent)
        edge(analyzeIntent forwardTo nodeFinish)
    }

    val directAnswer by subgraph<Pair<IntentAnalysis, ChatRequest>, Response>("direct-answer") {
        val generateDirectAnswer by node<Pair<IntentAnalysis, ChatRequest>, Response>("generate-answer") { request ->
            llm.writeSession {
                updatePrompt {
                    system("""
                Дай полный и развернутый ответ на вопрос пользователя.
                Используй структурированный формат с заголовком и сообщением.
                Чеклист оставь пустым, так как дополнительная информация не нужна.
                """)
                    user(request.second.message)
                }
                requestLLMStructured<StructuredResponse>()
            }
        }
        edge(nodeStart forwardTo generateDirectAnswer)
        edge(generateDirectAnswer forwardTo nodeFinish)
    }

    val infoCollection by subgraph<Pair<IntentAnalysis, ChatRequest>, Pair<Response, ChatRequest>>("info-collection") {
        val createOrUpdateChecklist by node<Pair<IntentAnalysis, ChatRequest>, Pair<Response, ChatRequest>>("checklist-management") { request ->
            llm.writeSession {
                updatePrompt {
                    system(mainSystemPrompt ?: "") // Используем существующий системный промпт
                    user(request.second.message)
                }
                requestLLMStructured<StructuredResponse>() to request.second
            }
        }
        edge(nodeStart forwardTo createOrUpdateChecklist)
        edge(createOrUpdateChecklist forwardTo nodeFinish)
    }

    val completionCheck by subgraph<Pair<Response, ChatRequest>, Triple<CompletionStatus, ChatRequest, Response>>("completion-check") {
        val checkCompletion by node<Pair<Response, ChatRequest>, Triple<CompletionStatus, ChatRequest, Response>>("check-done") { response ->
            val allResolved = response.first.getOrNull()!!.structure.checkList.all { it.resolution != null }
            val hasChecklist = response.first.getOrNull()!!.structure.checkList.isNotEmpty()

            Triple(CompletionStatus(
                isComplete = !hasChecklist || allResolved,
                requiresMoreInfo = hasChecklist && !allResolved,
                readyForFinalAnswer = allResolved && hasChecklist
            ), response.second, response.first)
        }
        edge(nodeStart forwardTo checkCompletion)
        edge(checkCompletion forwardTo nodeFinish)
    }

    val finalAnswer by subgraph<Triple<CompletionStatus, ChatRequest, Response>, Response>("final-answer") {
        val generateFinalAnswer by node<Triple<CompletionStatus, ChatRequest, Response>, Response>("final-response") { (_, request, resolvedChecklist) ->
            llm.writeSession {
                updatePrompt {
                    system(
                        """
                Теперь у тебя есть вся необходимая информация из чеклиста.
                Создай финальный, подробный ответ с конкретными шагами и рекомендациями.
                Не создавай новый чеклист - дай полную инструкцию.
                """
                    )
                    user(
                        """
                Изначальный запрос: ${request.message}
                Собранная информация: ${resolvedChecklist.getOrNull()!!.structure.checkList.joinToString("\n") { "- ${it.point}: ${it.resolution}" }}
                """
                    )
                }
                requestLLMStructured<StructuredResponse>()
            }
        }
        edge(nodeStart forwardTo generateFinalAnswer)
        edge(generateFinalAnswer forwardTo nodeFinish)
    }

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
