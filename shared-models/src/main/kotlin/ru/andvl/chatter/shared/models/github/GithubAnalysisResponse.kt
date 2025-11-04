package ru.andvl.chatter.shared.models.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequirementReviewCommentDto(
    @SerialName("comment_type")
    val commentType: String, // PROBLEM, ADVANTAGE, OK
    @SerialName("comment")
    val comment: String,
    @SerialName("file_reference")
    val fileReference: String?,
    @SerialName("code_quote")
    val codeQuote: String?
)

@Serializable
data class RepositoryReviewDto(
    @SerialName("general_conditions_review")
    val generalConditionsReview: RequirementReviewCommentDto,
    @SerialName("constraints_review")
    val constraintsReview: List<RequirementReviewCommentDto>,
    @SerialName("advantages_review")
    val advantagesReview: List<RequirementReviewCommentDto>,
    @SerialName("attention_points_review")
    val attentionPointsReview: List<RequirementReviewCommentDto>
)

@Serializable
data class RequirementsAnalysisDto(
    @SerialName("general_conditions")
    val generalConditions: String,
    @SerialName("important_constraints")
    val importantConstraints: List<String>,
    @SerialName("additional_advantages")
    val additionalAdvantages: List<String>,
    @SerialName("attention_points")
    val attentionPoints: List<String>
)

@Serializable
data class GithubAnalysisResponse(
    @SerialName("analysis")
    val analysis: String,
    @SerialName("tldr")
    val tldr: String,
    @SerialName("tool_calls")
    val toolCalls: List<String> = emptyList(),
    @SerialName("model")
    val model: String? = null,
    @SerialName("usage")
    val usage: TokenUsageDto? = null,
    @SerialName("repository_review")
    val repositoryReview: RepositoryReviewDto? = null,
    @SerialName("requirements")
    val requirements: RequirementsAnalysisDto? = null,
    @SerialName("user_request_analysis")
    val userRequestAnalysis: String? = null,
    @SerialName("docker_info")
    val dockerInfo: DockerInfoDto? = null
)

@Serializable
data class TokenUsageDto(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)
