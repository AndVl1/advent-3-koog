package ru.andvl.chatter.shared.models.github

import kotlinx.serialization.Serializable

@Serializable
data class GithubAnalysisResponse(
    val analysis: String,
    val toolCalls: List<String> = emptyList(),
    val model: String? = null,
    val usage: TokenUsageDto? = null
)

@Serializable
data class TokenUsageDto(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)