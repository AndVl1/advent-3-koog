package ru.andvl.chatter.koog.model.github

import ru.andvl.chatter.koog.model.common.TokenUsage

/**
 * Public response model for GitHub analysis
 */
data class GithubAnalysisResponse(
    val analysis: String,
    val toolCalls: List<String> = emptyList(),
    val model: String? = null,
    val usage: TokenUsage? = null
)