package ru.andvl.chatter.shared.models.codeagent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type of analysis to perform on repository
 */
@Serializable
enum class AnalysisType {
    @SerialName("STRUCTURE")
    STRUCTURE,      // Analyze file structure only

    @SerialName("DEPENDENCIES")
    DEPENDENCIES,   // Analyze dependencies and build config

    @SerialName("FULL")
    FULL            // Full analysis including code quality
}

/**
 * Request for repository analysis
 */
@Serializable
data class RepositoryAnalysisRequest(
    @SerialName("github_url")
    val githubUrl: String,

    @SerialName("analysis_type")
    val analysisType: AnalysisType = AnalysisType.STRUCTURE,

    @SerialName("enable_embeddings")
    val enableEmbeddings: Boolean = false
)

/**
 * Result of repository analysis
 */
@Serializable
data class RepositoryAnalysisResult(
    @SerialName("repository_path")
    val repositoryPath: String,

    @SerialName("repository_name")
    val repositoryName: String,

    @SerialName("summary")
    val summary: String,

    @SerialName("file_count")
    val fileCount: Int,

    @SerialName("main_languages")
    val mainLanguages: List<String>,

    @SerialName("structure_tree")
    val structureTree: String,

    @SerialName("dependencies")
    val dependencies: List<String> = emptyList(),

    @SerialName("build_tool")
    val buildTool: String? = null,

    @SerialName("error_message")
    val errorMessage: String? = null
)

/**
 * Request for code QA (questions about code)
 */
@Serializable
data class CodeQARequest(
    @SerialName("session_id")
    val sessionId: String,

    @SerialName("question")
    val question: String,

    @SerialName("history")
    val history: List<CodeQAMessage> = emptyList(),

    @SerialName("max_history_length")
    val maxHistoryLength: Int = 10
)

/**
 * Message in Code QA conversation
 */
@Serializable
data class CodeQAMessage(
    @SerialName("role")
    val role: MessageRole,

    @SerialName("content")
    val content: String,

    @SerialName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerialName("code_references")
    val codeReferences: List<CodeReference> = emptyList()
)

/**
 * Message role in conversation
 */
@Serializable
enum class MessageRole {
    @SerialName("USER")
    USER,

    @SerialName("ASSISTANT")
    ASSISTANT
}

/**
 * Reference to code in response
 */
@Serializable
data class CodeReference(
    @SerialName("file_path")
    val filePath: String,

    @SerialName("line_start")
    val lineStart: Int,

    @SerialName("line_end")
    val lineEnd: Int,

    @SerialName("code_snippet")
    val codeSnippet: String
)

/**
 * Response for Code QA
 */
@Serializable
data class CodeQAResponse(
    @SerialName("answer")
    val answer: String,

    @SerialName("code_references")
    val codeReferences: List<CodeReference> = emptyList(),

    @SerialName("confidence")
    val confidence: Float = 1.0f,

    @SerialName("model")
    val model: String,

    @SerialName("usage")
    val usage: TokenUsage? = null
)

/**
 * Token usage information
 */
@Serializable
data class TokenUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,

    @SerialName("completion_tokens")
    val completionTokens: Int,

    @SerialName("total_tokens")
    val totalTokens: Int
)

/**
 * Request for code modification with checklist
 */
@Serializable
data class CodeModificationRequest(
    @SerialName("session_id")
    val sessionId: String,

    @SerialName("modification_request")
    val modificationRequest: String,

    @SerialName("create_branch")
    val createBranch: Boolean = true,

    @SerialName("branch_name")
    val branchName: String? = null
)

/**
 * Response for code modification
 */
@Serializable
data class CodeModificationResponse(
    @SerialName("success")
    val success: Boolean,

    @SerialName("checklist")
    val checklist: ModificationChecklist,

    @SerialName("files_modified")
    val filesModified: List<String>,

    @SerialName("branch_name")
    val branchName: String? = null,

    @SerialName("commit_sha")
    val commitSha: String? = null,

    @SerialName("error_message")
    val errorMessage: String? = null,

    @SerialName("model")
    val model: String,

    @SerialName("usage")
    val usage: TokenUsage? = null
)

/**
 * Modification checklist with tasks
 */
@Serializable
data class ModificationChecklist(
    @SerialName("tasks")
    val tasks: List<ChecklistTask>,

    @SerialName("completed_count")
    val completedCount: Int,

    @SerialName("total_count")
    val totalCount: Int
)

/**
 * Individual checklist task
 */
@Serializable
data class ChecklistTask(
    @SerialName("id")
    val id: String,

    @SerialName("description")
    val description: String,

    @SerialName("status")
    val status: TaskStatus,

    @SerialName("file_path")
    val filePath: String? = null,

    @SerialName("verification_result")
    val verificationResult: String? = null
)

/**
 * Task status in checklist
 */
@Serializable
enum class TaskStatus {
    @SerialName("PENDING")
    PENDING,

    @SerialName("IN_PROGRESS")
    IN_PROGRESS,

    @SerialName("COMPLETED")
    COMPLETED,

    @SerialName("FAILED")
    FAILED
}

/**
 * Session data for persistence
 */
@Serializable
data class SessionData(
    @SerialName("session_id")
    val sessionId: String,

    @SerialName("repository_url")
    val repositoryUrl: String,

    @SerialName("repository_path")
    val repositoryPath: String,

    @SerialName("created_at")
    val createdAt: Long,

    @SerialName("updated_at")
    val updatedAt: Long,

    @SerialName("analysis_result")
    val analysisResult: RepositoryAnalysisResult? = null,

    @SerialName("qa_history")
    val qaHistory: List<CodeQAMessage> = emptyList(),

    @SerialName("modifications")
    val modifications: List<ModificationRecord> = emptyList()
)

/**
 * Record of modification made in session
 */
@Serializable
data class ModificationRecord(
    @SerialName("timestamp")
    val timestamp: Long,

    @SerialName("request")
    val request: String,

    @SerialName("files_modified")
    val filesModified: List<String>,

    @SerialName("commit_sha")
    val commitSha: String? = null,

    @SerialName("branch_name")
    val branchName: String? = null
)
