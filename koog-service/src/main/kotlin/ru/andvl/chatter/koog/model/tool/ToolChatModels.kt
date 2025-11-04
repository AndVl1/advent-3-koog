package ru.andvl.chatter.koog.model.tool

import ai.koog.prompt.message.Message
import ru.andvl.chatter.koog.model.common.TokenUsage
import ru.andvl.chatter.koog.model.docker.DockerInfoModel
import ru.andvl.chatter.shared.models.ChatHistory

internal class GithubChatRequest(
    val message: String,
    val systemPrompt: String? = null,
    val history: ChatHistory,
)

internal class ToolChatResponse(
    val response: String,
    val shortSummary: String,
    val toolCalls: List<String>,
    val originalMessage: Message.Assistant?,
    val tokenUsage: TokenUsage?,
    val repositoryReview: RepositoryReviewModel? = null,
    val requirements: RequirementsAnalysisModel? = null,
    val userRequestAnalysis: String? = null,
    val dockerInfo: DockerInfoModel? = null,
)
