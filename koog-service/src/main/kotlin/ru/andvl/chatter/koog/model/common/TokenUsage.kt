package ru.andvl.chatter.koog.model.common

/**
 * Token usage information
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
