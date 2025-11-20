package ru.andvl.chatter.koog.model.codemod

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.andvl.chatter.koog.model.docker.DockerEnvModel

/**
 * Request model for code modification agent
 */
@Serializable
data class CodeModificationRequest(
    @SerialName("github_repo")
    val githubRepo: String,
    @SerialName("user_request")
    val userRequest: String,
    @SerialName("docker_env")
    val dockerEnv: DockerEnvModel? = null,
    @SerialName("enable_embeddings")
    val enableEmbeddings: Boolean = false
)

/**
 * Response model for code modification agent
 */
@Serializable
data class CodeModificationResponse(
    @SerialName("success")
    val success: Boolean,
    @SerialName("pr_url")
    val prUrl: String? = null,
    @SerialName("pr_number")
    val prNumber: Int? = null,
    @SerialName("diff")
    val diff: String? = null,
    @SerialName("commit_sha")
    val commitSha: String? = null,
    @SerialName("branch_name")
    val branchName: String,
    @SerialName("files_modified")
    val filesModified: List<String>,
    @SerialName("verification_status")
    val verificationStatus: VerificationStatus,
    @SerialName("iterations_used")
    val iterationsUsed: Int,
    @SerialName("error_message")
    val errorMessage: String? = null,
    @SerialName("message")
    val message: String
)

/**
 * Verification status enum
 */
@Serializable
enum class VerificationStatus {
    @SerialName("SUCCESS")
    SUCCESS,
    @SerialName("FAILED_VERIFICATION")
    FAILED_VERIFICATION,
    @SerialName("FAILED_PUSH")
    FAILED_PUSH,
    @SerialName("FAILED_SETUP")
    FAILED_SETUP,
    @SerialName("FAILED_ANALYSIS")
    FAILED_ANALYSIS,
    @SerialName("FAILED_MODIFICATION")
    FAILED_MODIFICATION
}

/**
 * Result of repository setup subgraph
 */
@Serializable
data class SetupResult(
    @SerialName("repository_path")
    val repositoryPath: String,
    @SerialName("default_branch")
    val defaultBranch: String,
    @SerialName("feature_branch")
    val featureBranch: String,
    @SerialName("owner")
    val owner: String,
    @SerialName("repo")
    val repo: String
)

/**
 * Result of code analysis subgraph
 */
@Serializable
data class AnalysisResult(
    @SerialName("modification_plan")
    val modificationPlan: String,
    @SerialName("files_to_modify")
    val filesToModify: List<String>,
    @SerialName("dependencies_identified")
    val dependenciesIdentified: List<String>,
    @SerialName("docker_env")
    val dockerEnv: DockerEnvModel? = null
)

/**
 * Result of code modification subgraph
 */
@Serializable
data class ModificationResult(
    @SerialName("files_modified")
    val filesModified: List<String>,
    @SerialName("patches_applied")
    val patchesApplied: Int,
    @SerialName("files_created")
    val filesCreated: List<String>,
    @SerialName("files_deleted")
    val filesDeleted: List<String>
)

/**
 * Result of Docker verification subgraph
 */
@Serializable
data class VerificationResult(
    @SerialName("success")
    val success: Boolean,
    @SerialName("command_executed")
    val commandExecuted: String,
    @SerialName("exit_code")
    val exitCode: Int,
    @SerialName("logs")
    val logs: List<String>,
    @SerialName("error_message")
    val errorMessage: String? = null
)

/**
 * Result of Git operations subgraph
 */
@Serializable
data class GitResult(
    @SerialName("commit_sha")
    val commitSha: String,
    @SerialName("pushed")
    val pushed: Boolean,
    @SerialName("branch_name")
    val branchName: String,
    @SerialName("push_rejected")
    val pushRejected: Boolean = false,
    @SerialName("message")
    val message: String
)
