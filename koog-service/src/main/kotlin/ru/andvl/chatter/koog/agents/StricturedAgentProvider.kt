package ru.andvl.chatter.koog.agents

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
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
                    model = LLModel(
                        provider = LLMProvider.OpenRouter,
                        id = "z-ai/glm-4.6", // "z-ai/glm-4.6", //"mistralai/mistral-tiny",
                        capabilities = listOf(
                            LLMCapability.Temperature,
                            LLMCapability.Completion,
                        ),
                        contextLength = 16_000, //
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
                            contextLength = 16_000, //
                        ),
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
                    model = LLModel(
                        provider = LLMProvider.OpenRouter,
                        id = "qwen/qwen3-8b",
                        capabilities = listOf(
                            LLMCapability.Temperature,
                            LLMCapability.Speculation,
                            LLMCapability.Completion,
                        ),
                        contextLength = 16_000, //
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
                            contextLength = 16_000, //
                        ),
                        retries = 3
                    )
                )
            }
        }
        edge(nodeStart forwardTo generateDirectAnswer)
        edge(generateDirectAnswer forwardTo nodeFinish)
    }

    val infoCollection by subgraph<Pair<IntentAnalysis, ChatRequest>, Pair<Response, ChatRequest>>("info-collection") {
        val createOrUpdateChecklist by node<Pair<IntentAnalysis, ChatRequest>, Pair<Response, ChatRequest>>("checklist-management") { request ->
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
                            contextLength = 16_000, //
                        ),
                        retries = 3
                    )
                )
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
