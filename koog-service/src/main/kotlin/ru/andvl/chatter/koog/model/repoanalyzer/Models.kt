package ru.andvl.chatter.koog.model.repoanalyzer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result of input validation subgraph
 *
 * Contains validated GitHub repository information extracted from URL
 */
@Serializable
data class ValidatedRequest(
    @SerialName("github_url")
    val githubUrl: String,
    @SerialName("owner")
    val owner: String,
    @SerialName("repo")
    val repo: String,
    @SerialName("enable_embeddings")
    val enableEmbeddings: Boolean
)

/**
 * Result of repository setup subgraph
 *
 * Contains paths and metadata about the cloned repository
 */
@Serializable
data class SetupResult(
    @SerialName("repository_path")
    val repositoryPath: String,
    @SerialName("default_branch")
    val defaultBranch: String,
    @SerialName("owner")
    val owner: String,
    @SerialName("repo")
    val repo: String,
    @SerialName("already_existed")
    val alreadyExisted: Boolean
)

/**
 * Result of structure analysis subgraph
 *
 * Contains information about repository structure, file tree, and languages
 */
@Serializable
data class StructureResult(
    @SerialName("file_tree")
    val fileTree: String,
    @SerialName("languages")
    val languages: Map<String, Int>,
    @SerialName("total_files")
    val totalFiles: Int,
    @SerialName("total_lines")
    val totalLines: Int
)

/**
 * Result of dependency analysis subgraph
 *
 * Contains detected dependencies, build tools, and frameworks
 */
@Serializable
data class DependencyResult(
    @SerialName("build_tools")
    val buildTools: List<String>,
    @SerialName("dependencies")
    val dependencies: List<DependencyInfo>,
    @SerialName("frameworks")
    val frameworks: List<String>
)

/**
 * Information about a single dependency
 */
@Serializable
data class DependencyInfo(
    @SerialName("name")
    val name: String,
    @SerialName("version")
    val version: String? = null,
    @SerialName("type")
    val type: String
)

/**
 * Result of summary generation subgraph
 *
 * Contains LLM-generated summary of the repository
 */
@Serializable
data class SummaryResult(
    @SerialName("summary")
    val summary: String,
    @SerialName("key_features")
    val keyFeatures: List<String>,
    @SerialName("architecture_notes")
    val architectureNotes: String
)

/**
 * Result of embeddings generation subgraph
 *
 * Contains embedding metadata and status
 */
@Serializable
data class EmbeddingsResult(
    @SerialName("total_chunks")
    val totalChunks: Int,
    @SerialName("files_processed")
    val filesProcessed: Int,
    @SerialName("embedding_model")
    val embeddingModel: String,
    @SerialName("status")
    val status: EmbeddingStatus
)

/**
 * Status of embeddings generation
 */
@Serializable
enum class EmbeddingStatus {
    @SerialName("SUCCESS")
    SUCCESS,
    @SerialName("PARTIAL")
    PARTIAL,
    @SerialName("FAILED")
    FAILED,
    @SerialName("SKIPPED")
    SKIPPED
}
