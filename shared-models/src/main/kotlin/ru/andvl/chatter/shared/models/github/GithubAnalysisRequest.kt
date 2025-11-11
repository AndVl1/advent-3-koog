package ru.andvl.chatter.shared.models.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubAnalysisRequest(
    @SerialName("user_message")
    val userMessage: String,
    @SerialName("google_sheets_url")
    val googleSheetsUrl: String? = null,
    @SerialName("force_skip_docker")
    val forceSkipDocker: Boolean = true,
    @SerialName("enable_embeddings")
    val enableEmbeddings: Boolean = false
)
