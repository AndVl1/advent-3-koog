package ru.andvl.chatter.shared.models.github

import kotlinx.serialization.Serializable

@Serializable
data class GithubAnalysisRequest(
    val userMessage: String
)
