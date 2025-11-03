package ru.andvl.chatter.cli.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.andvl.chatter.shared.models.ConversationState
import ru.andvl.chatter.shared.models.SharedCheckListItem
import ru.andvl.chatter.shared.models.SharedStructuredResponse

typealias StructuredResponse = SharedStructuredResponse
typealias CheckListItem = SharedCheckListItem

@Serializable
data class ChatContextRequest(
    @SerialName("message")
    val message: String,
    @SerialName("conversation_state")
    val conversationState: ConversationState? = null, // Include conversation state for checklist persistence
    @SerialName("max_history_length")
    val maxHistoryLength: Int = 20,
    @SerialName("provider")
    val provider: String? = null,
    @SerialName("system_prompt")
    val systemPrompt: String? = null
)

/**
 * Response DTO for chat - matches server's ChatResponseDto
 */
@Serializable
data class ChatResponseDto(
    @SerialName("response")
    val response: SharedStructuredResponse,
    @SerialName("model")
    val model: String? = null,
    @SerialName("usage")
    val usage: TokenUsageDto? = null
)

/**
 * Token usage DTO
 */
@Serializable
data class TokenUsageDto(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

/**
 * GitHub analysis request DTO
 */
@Serializable
data class GithubAnalysisRequest(
    @SerialName("user_message")
    val userMessage: String
)

/**
 * GitHub analysis response DTO
 */
@Serializable
data class GithubAnalysisResponse(
    @SerialName("analysis")
    val analysis: String,
    @SerialName("tldr")
    val tldr: String,
    @SerialName("tool_calls")
    val toolCalls: List<String>,
    @SerialName("model")
    val model: String? = null,
    @SerialName("usage")
    val usage: GithubTokenUsageDto? = null,
    @SerialName("repository_review")
    val repositoryReview: RepositoryReviewDto? = null,
    @SerialName("requirements")
    val requirements: RequirementsAnalysisDto? = null,
    @SerialName("docker_info")
    val dockerInfo: DockerInfoDto? = null
)

/**
 * GitHub Token usage DTO
 */
@Serializable
data class GithubTokenUsageDto(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

/**
 * Requirements analysis DTO
 */
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

/**
 * Requirement review comment DTO
 */
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

/**
 * Repository review DTO
 */
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

/**
 * Docker environment DTO
 */
@Serializable
data class DockerEnvDto(
    @SerialName("base_image")
    val baseImage: String,
    @SerialName("build_command")
    val buildCommand: String,
    @SerialName("run_command")
    val runCommand: String,
    @SerialName("port")
    val port: Int? = null,
    @SerialName("additional_notes")
    val additionalNotes: String? = null
)

/**
 * Docker build result DTO
 */
@Serializable
data class DockerBuildResultDto(
    @SerialName("build_status")
    val buildStatus: String,
    @SerialName("build_logs")
    val buildLogs: List<String> = emptyList(),
    @SerialName("image_size")
    val imageSize: String? = null,
    @SerialName("build_duration_seconds")
    val buildDurationSeconds: Int? = null,
    @SerialName("error_message")
    val errorMessage: String? = null
)

/**
 * Docker info DTO
 */
@Serializable
data class DockerInfoDto(
    @SerialName("docker_env")
    val dockerEnv: DockerEnvDto,
    @SerialName("build_result")
    val buildResult: DockerBuildResultDto,
    @SerialName("dockerfile_generated")
    val dockerfileGenerated: Boolean = false
)
