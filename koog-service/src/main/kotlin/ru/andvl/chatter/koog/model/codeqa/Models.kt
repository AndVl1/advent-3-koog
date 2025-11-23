package ru.andvl.chatter.koog.model.codeqa

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.andvl.chatter.shared.models.codeagent.CodeReference
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisResult

/**
 * Internal models for Code QA Agent
 *
 * These models are used internally within the agent and are NOT exposed as part of the public API.
 * Public API uses models from ru.andvl.chatter.shared.models.codeagent package.
 */

/**
 * Result of session validation
 */
@Serializable
internal data class SessionValidationResult(
    @SerialName("session_id")
    val sessionId: String,

    @SerialName("session_exists")
    val sessionExists: Boolean,

    @SerialName("repository_path")
    val repositoryPath: String?,

    @SerialName("repository_name")
    val repositoryName: String?,

    @SerialName("rag_available")
    val ragAvailable: Boolean,

    @SerialName("analysis_result")
    val analysisResult: RepositoryAnalysisResult?,

    @SerialName("error_message")
    val errorMessage: String? = null
)

/**
 * Type of question intent
 */
@Serializable
internal enum class QuestionIntent {
    @SerialName("CODE_LOOKUP")
    CODE_LOOKUP,        // User wants to find specific code

    @SerialName("EXPLANATION")
    EXPLANATION,        // User wants to understand how something works

    @SerialName("MODIFICATION")
    MODIFICATION,       // User wants to modify code (should suggest code mod agent)

    @SerialName("GENERAL")
    GENERAL             // General question about the repository
}

/**
 * Result of question analysis by LLM
 */
@Serializable
internal data class QuestionAnalysisResult(
    @SerialName("intent")
    val intent: QuestionIntent,

    @SerialName("search_query")
    val searchQuery: String,

    @SerialName("keywords")
    val keywords: List<String>,

    @SerialName("requires_code_search")
    val requiresCodeSearch: Boolean,

    @SerialName("suggested_files")
    val suggestedFiles: List<String> = emptyList(),

    @SerialName("analysis_explanation")
    val analysisExplanation: String
)

/**
 * Internal code reference with metadata
 */
@Serializable
internal data class CodeReferenceInternal(
    @SerialName("file_path")
    val filePath: String,

    @SerialName("line_start")
    val lineStart: Int,

    @SerialName("line_end")
    val lineEnd: Int,

    @SerialName("code_snippet")
    val codeSnippet: String,

    @SerialName("relevance_score")
    val relevanceScore: Float = 1.0f,

    @SerialName("source")
    val source: String // "RAG" or "GitHub MCP"
)

/**
 * Result of code search (RAG + GitHub MCP combined)
 */
@Serializable
internal data class CodeSearchResult(
    @SerialName("references")
    val references: List<CodeReferenceInternal>,

    @SerialName("total_found")
    val totalFound: Int,

    @SerialName("rag_results_count")
    val ragResultsCount: Int,

    @SerialName("mcp_results_count")
    val mcpResultsCount: Int,

    @SerialName("search_method")
    val searchMethod: String, // "RAG", "GitHub MCP", or "Hybrid"

    @SerialName("search_successful")
    val searchSuccessful: Boolean
)

/**
 * Code reference with explanation for answer
 */
@Serializable
internal data class CodeReferenceWithExplanation(
    @SerialName("reference")
    val reference: CodeReferenceInternal,

    @SerialName("explanation")
    val explanation: String,

    @SerialName("relevance_to_question")
    val relevanceToQuestion: String
)

/**
 * Result of answer generation
 */
@Serializable
internal data class AnswerGenerationResult(
    @SerialName("answer")
    val answer: String,

    @SerialName("code_references_with_explanations")
    val codeReferencesWithExplanations: List<CodeReferenceWithExplanation>,

    @SerialName("confidence")
    val confidence: Float,

    @SerialName("follow_up_suggestions")
    val followUpSuggestions: List<String> = emptyList(),

    @SerialName("suggests_code_modification")
    val suggestsCodeModification: Boolean = false
)
