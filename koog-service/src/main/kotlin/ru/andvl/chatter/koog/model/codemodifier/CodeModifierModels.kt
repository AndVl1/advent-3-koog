package ru.andvl.chatter.koog.model.codemodifier

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Input request for code modification
 *
 * Contains the session information, modification instructions,
 * and scope of files to modify.
 */
@Serializable
data class CodeModificationRequest(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("instructions")
    val instructions: String,
    @SerialName("file_scope")
    val fileScope: List<String>? = null,
    @SerialName("enable_validation")
    val enableValidation: Boolean = true,
    @SerialName("max_changes")
    val maxChanges: Int = 50
)

/**
 * Output result of code modification
 *
 * Contains the proposed changes, validation status,
 * and metadata about the modification.
 */
@Serializable
data class CodeModificationResult(
    @SerialName("success")
    val success: Boolean,
    @SerialName("modification_plan")
    val modificationPlan: ModificationPlan? = null,
    @SerialName("validation_passed")
    val validationPassed: Boolean = false,
    @SerialName("breaking_changes_detected")
    val breakingChangesDetected: Boolean = false,
    @SerialName("error_message")
    val errorMessage: String? = null,
    @SerialName("total_files_affected")
    val totalFilesAffected: Int = 0,
    @SerialName("total_changes")
    val totalChanges: Int = 0,
    @SerialName("complexity")
    val complexity: Complexity = Complexity.SIMPLE
)

/**
 * Modification plan with ordered changes
 */
@Serializable
data class ModificationPlan(
    @SerialName("changes")
    val changes: List<ProposedChange>,
    @SerialName("rationale")
    val rationale: String,
    @SerialName("estimated_complexity")
    val estimatedComplexity: Complexity,
    @SerialName("dependencies_sorted")
    val dependenciesSorted: Boolean = false
)

/**
 * A single proposed change to a file
 */
@Serializable
data class ProposedChange(
    @SerialName("change_id")
    val changeId: String,
    @SerialName("file_path")
    val filePath: String,
    @SerialName("change_type")
    val changeType: ChangeType,
    @SerialName("description")
    val description: String,
    @SerialName("start_line")
    val startLine: Int? = null,
    @SerialName("end_line")
    val endLine: Int? = null,
    @SerialName("new_content")
    val newContent: String,
    @SerialName("old_content")
    val oldContent: String? = null,
    @SerialName("depends_on")
    val dependsOn: List<String> = emptyList(),
    @SerialName("validation_notes")
    val validationNotes: String? = null
)

/**
 * Type of code modification
 */
@Serializable
enum class ChangeType {
    @SerialName("CREATE")
    CREATE,
    @SerialName("MODIFY")
    MODIFY,
    @SerialName("DELETE")
    DELETE,
    @SerialName("RENAME")
    RENAME,
    @SerialName("REFACTOR")
    REFACTOR
}

/**
 * Complexity estimate for modifications
 */
@Serializable
enum class Complexity {
    @SerialName("SIMPLE")
    SIMPLE,
    @SerialName("MODERATE")
    MODERATE,
    @SerialName("COMPLEX")
    COMPLEX,
    @SerialName("CRITICAL")
    CRITICAL
}

// Internal models (used only within the agent)

/**
 * Result of request validation subgraph
 */
@Serializable
internal data class ValidationResult(
    @SerialName("is_valid")
    val isValid: Boolean,
    @SerialName("session_path")
    val sessionPath: String? = null,
    @SerialName("normalized_file_scope")
    val normalizedFileScope: List<String> = emptyList(),
    @SerialName("error_message")
    val errorMessage: String? = null
)

/**
 * Result of code analysis subgraph
 */
@Serializable
internal data class CodeAnalysisResult(
    @SerialName("relevant_files")
    val relevantFiles: List<String>,
    @SerialName("file_contexts")
    val fileContexts: List<FileContext>,
    @SerialName("detected_patterns")
    val detectedPatterns: CodePatterns
)

/**
 * Context information about a file
 */
@Serializable
internal data class FileContext(
    @SerialName("file_path")
    val filePath: String,
    @SerialName("content")
    val content: String,
    @SerialName("language")
    val language: String,
    @SerialName("imports")
    val imports: List<String> = emptyList(),
    @SerialName("classes")
    val classes: List<String> = emptyList(),
    @SerialName("functions")
    val functions: List<String> = emptyList(),
    @SerialName("total_lines")
    val totalLines: Int
)

/**
 * Detected code patterns and style
 */
@Serializable
internal data class CodePatterns(
    @SerialName("indentation")
    val indentation: String = "4 spaces",
    @SerialName("naming_convention")
    val namingConvention: String = "camelCase",
    @SerialName("code_style")
    val codeStyle: String = "standard",
    @SerialName("common_patterns")
    val commonPatterns: List<String> = emptyList()
)

/**
 * Result of modification planning subgraph
 */
@Serializable
internal data class PlanningResult(
    @SerialName("plan")
    val plan: ModificationPlan,
    @SerialName("plan_valid")
    val planValid: Boolean = true,
    @SerialName("validation_error")
    val validationError: String? = null
)

/**
 * Result of syntax validation check
 */
@Serializable
internal data class ValidationCheckResult(
    @SerialName("syntax_valid")
    val syntaxValid: Boolean,
    @SerialName("breaking_changes")
    val breakingChanges: List<String> = emptyList(),
    @SerialName("validation_notes")
    val validationNotes: String? = null,
    @SerialName("errors")
    val errors: List<String> = emptyList()
)
