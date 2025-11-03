package ru.andvl.chatter.koog.model.tool

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.andvl.chatter.koog.model.docker.DockerEnvModel

@LLMDescription("Requirements extracted from user request or external documents")
@Serializable
data class RequirementsAnalysisModel(
    @property:LLMDescription("General task conditions and description. Must be provided at least empty. Field name: general_conditions")
    @SerialName("general_conditions")
    val generalConditions: String,
    @property:LLMDescription("Important constraints and limitations to pay special attention to. Must be provided at least empty. Field name: important_constraints")
    @SerialName("important_constraints")
    val importantConstraints: List<String>,
    @property:LLMDescription("Additional advantages and positive aspects for evaluation. Must be provided at least empty. Field name: additional_advantages")
    @SerialName("additional_advantages")
    val additionalAdvantages: List<String>,
    @property:LLMDescription("Things that require human attention for careful review. Must be provided at least empty. Field name: attention_points")
    @SerialName("attention_points")
    val attentionPoints: List<String>
)

@LLMDescription("Initial user request analysis")
@Serializable
@SerialName("InitialPromptAnalysisModel. May be Success or Failure")
internal sealed interface InitialPromptAnalysisModel {
    @LLMDescription("Success model of users request. Contains github repo, user request, and structured requirements")
    @SerialName("success_analysis_model")
    @Serializable
    data class SuccessAnalysisModel(
        @property:LLMDescription("github repository url from users request. Field named github_repo")
        @SerialName("github_repo")
        val githubRepo: String,
        @property:LLMDescription("User request about provided repository. Field named user_analysis_request")
        @SerialName("user_analysis_request")
        val userRequest: String,
        @property:LLMDescription("Structured requirements analysis extracted from user input or external documents. Field named requirements")
        @SerialName("requirements")
        val requirements: RequirementsAnalysisModel?,
        @property:LLMDescription("Google Docs URL if provided in the request. Must be provided at least null (if not present). Field named google_docs_url")
        @SerialName("google_docs_url")
        val googleDocsUrl: String?
    ) : InitialPromptAnalysisModel

    @LLMDescription("Model to be sent if needed info can't be found from user request. Contains failure reason (failure_reason)")
    @SerialName("failed_analysis_model")
    @Serializable
    data class FailedAnalysisModel(
        @property:LLMDescription("Human-readable reason of why request has failed. Field named failure_reason")
        @SerialName("failure_reason")
        val reason: String,
    ) : InitialPromptAnalysisModel
}

@LLMDescription("Review comment for specific requirement")
@Serializable
data class RequirementReviewComment(
    @property:LLMDescription("Type of comment: PROBLEM, ADVANTAGE, or OK")
    @SerialName("comment_type")
    val commentType: String, // PROBLEM, ADVANTAGE, OK
    @property:LLMDescription("Detailed comment about the requirement")
    @SerialName("comment")
    val comment: String,
    @property:LLMDescription("File path and line reference if applicable (format: path/to/file.ext:123)")
    @SerialName("file_reference")
    val fileReference: String?,
    @property:LLMDescription("Code snippet or quote if applicable")
    @SerialName("code_quote")
    val codeQuote: String?
)

@LLMDescription("Repository review based on structured requirements")
@Serializable
data class RepositoryReviewModel(
    @property:LLMDescription("Comments on general conditions compliance")
    @SerialName("general_conditions_review")
    val generalConditionsReview: RequirementReviewComment,
    @property:LLMDescription("Comments for each important constraint")
    @SerialName("constraints_review")
    val constraintsReview: List<RequirementReviewComment>,
    @property:LLMDescription("Comments for each additional advantage")
    @SerialName("advantages_review")
    val advantagesReview: List<RequirementReviewComment>,
    @property:LLMDescription("Comments for each attention point")
    @SerialName("attention_points_review")
    val attentionPointsReview: List<RequirementReviewComment>
)

@LLMDescription("Github repository analysis")
@Serializable
@SerialName("GithubRepositoryAnalysisModel")
internal sealed interface GithubRepositoryAnalysisModel {

    override fun toString(): String

    @LLMDescription("Result of github repository analysis. Contains structured review and free-form analysis")
    @SerialName("SuccessAnalysisModel")
    @Serializable
    data class SuccessAnalysisModel(
        @property:LLMDescription("Free-form github analysis result. Include as much structure as possible. Field named free_form_github_analysis")
        @SerialName("free_form_github_analysis")
        val freeFormAnswer: String,
        @property:LLMDescription("TLDR of full answer")
        @SerialName("tldr")
        val shortSummary: String,
        @property:LLMDescription("Structured repository review based on requirements if available")
        @SerialName("repository_review")
        val repositoryReview: RepositoryReviewModel?,
        @property:LLMDescription("Docker environment configuration if project can be containerized. Set to null if Docker is not applicable. Field name: docker_env")
        @SerialName("docker_env")
        val dockerEnv: DockerEnvModel? = null
    ) : GithubRepositoryAnalysisModel

    @LLMDescription("Model to be sent if needed info can't be found from user request")
    @SerialName("FailedAnalysisModel")
    @Serializable
    data class FailedAnalysisModel(
        @property:LLMDescription("Human-readable reason of why request has failed. Field named failure_reason")
        @SerialName("failure_reason")
        val reason: String,
    ) : GithubRepositoryAnalysisModel {
        override fun toString(): String {
            return "Request returned error: $reason"
        }
    }
}
