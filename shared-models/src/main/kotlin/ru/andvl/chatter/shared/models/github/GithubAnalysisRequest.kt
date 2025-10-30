package ru.andvl.chatter.shared.models.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubAnalysisRequest(
    @SerialName("user_message")
    val userMessage: String
)
