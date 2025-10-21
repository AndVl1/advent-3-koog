package ru.andvl.chatter.koog.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable


@Serializable
@LLMDescription("Анализ запроса пользователя и определение типа ответа")
internal data class IntentAnalysis(
    @property:LLMDescription("Способ ответа пользователю")
    val intentType: IntentType, // "DIRECT_ANSWER", "COLLECT_INFO

)

@Serializable
@LLMDescription("Способ ответа пользователю")
internal enum class IntentType {
    @LLMDescription("Простой вопрос или достаточно информации для объяснения")
    DIRECT_ANSWER,
    @LLMDescription("Необходимо создать/построить что-то сложное или деталей не хватает")
    COLLECT_INFO
}

@Serializable
data class CompletionStatus(
    val isComplete: Boolean,
    val requiresMoreInfo: Boolean,
    val readyForFinalAnswer: Boolean
)
