package ru.andvl.chatter.koog.model.structured

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable


@Serializable
@LLMDescription("Analysis of user request and determination of response type")
internal data class IntentAnalysis(
    @property:LLMDescription("How to respond to the user")
    val intentType: IntentType, // "DIRECT_ANSWER", "COLLECT_INFO
)

@Serializable
@LLMDescription("How to respond to the user")
internal enum class IntentType {
    @LLMDescription("Simple question or sufficient information available for explanation")
    DIRECT_ANSWER,
    @LLMDescription("Need to create/build something complex or insufficient details provided")
    COLLECT_INFO
}

@Serializable
data class CompletionStatus(
    val isComplete: Boolean,
    val requiresMoreInfo: Boolean,
    val readyForFinalAnswer: Boolean
)
