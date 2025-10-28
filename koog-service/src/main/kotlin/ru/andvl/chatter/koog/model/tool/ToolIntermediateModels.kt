package ru.andvl.chatter.koog.model.tool

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@LLMDescription("Initial user request analysis")
@Serializable
@SerialName("InitialPromptAnalysisModel. May be Success or Failure")
internal sealed interface InitialPromptAnalysisModel {
    @LLMDescription("Success model of users request. Contains user request (user_analysis_request) and link to github repo (github_repo)")
    @SerialName("SuccessAnalysisModel")
    @Serializable
    data class SuccessAnalysisModel(
        @property:LLMDescription("github repository url from users request. Field named github_repo")
        @SerialName("github_repo")
        val githubRepo: String,
        @property:LLMDescription("User request about provided repository. Field named user_analysis_request")
        @SerialName("user_analysis_request")
        val userRequest: String,
    ) : InitialPromptAnalysisModel

    @LLMDescription("Model to be sent if needed info can't be found from user request. Contains failure reason (failure_reason)")
    @SerialName("FailedAnalysisModel")
    @Serializable
    data class FailedAnalysisModel(
        @property:LLMDescription("Human-readable reason of why request has failed. Field named failure_reason")
        @SerialName("failure_reason")
        val reason: String,
    ) : InitialPromptAnalysisModel
}

@LLMDescription("Github repository analysis")
@Serializable
@SerialName("GithubRepositoryAnalysisModel")
internal sealed interface GithubRepositoryAnalysisModel {

    override fun toString(): String

    @LLMDescription("Result of github repository analysis. Contains free_form_github_analysis")
    @SerialName("SuccessAnalysisModel")
    @Serializable
    data class SuccessAnalysisModel(
        @property:LLMDescription("Free-form github analysis result. Include as much structure as possible. Field named free_form_github_analysis")
        @SerialName("free_form_github_analysis")
        val freeFormAnswer: String
    ) : GithubRepositoryAnalysisModel {
        override fun toString(): String {
            return freeFormAnswer
        }
    }

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
