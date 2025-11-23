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
 * Docker validation results, and metadata about the modification.
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
    val complexity: Complexity = Complexity.SIMPLE,
    @SerialName("docker_validation_result")
    val dockerValidationResult: DockerValidationResult? = null
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

/**
 * Docker validation result
 */
@Serializable
data class DockerValidationResult(
    @SerialName("validated")
    val validated: Boolean,
    @SerialName("docker_available")
    val dockerAvailable: Boolean,
    @SerialName("build_passed")
    val buildPassed: Boolean? = null,
    @SerialName("tests_passed")
    val testsPassed: Boolean? = null,
    @SerialName("build_logs")
    val buildLogs: List<String> = emptyList(),
    @SerialName("test_logs")
    val testLogs: List<String> = emptyList(),
    @SerialName("error_message")
    val errorMessage: String? = null,
    @SerialName("duration_seconds")
    val durationSeconds: Int = 0
)

/**
 * Project type detection for Docker validation
 */
internal enum class ProjectType(
    val baseImage: String,
    val buildCommand: String,
    val testCommand: String?,
    val detectionFiles: List<String>
) {
    KOTLIN_GRADLE(
        baseImage = "gradle:8-jdk17",
        buildCommand = "./gradlew build -x test",
        testCommand = "./gradlew test",
        detectionFiles = listOf("build.gradle.kts", "build.gradle", "gradlew")
    ),
    JAVA_MAVEN(
        baseImage = "maven:3-openjdk-17",
        buildCommand = "mvn compile",
        testCommand = "mvn test",
        detectionFiles = listOf("pom.xml", "mvnw")
    ),
    NODE_NPM(
        baseImage = "node:18-alpine",
        buildCommand = "npm install && npm run build",
        testCommand = "npm test",
        detectionFiles = listOf("package.json", "package-lock.json")
    ),
    PYTHON_PIP(
        baseImage = "python:3.9-slim",
        buildCommand = "pip install -r requirements.txt",
        testCommand = "pytest",
        detectionFiles = listOf("requirements.txt", "setup.py")
    ),
    UNKNOWN(
        baseImage = "ubuntu:latest",
        buildCommand = "echo 'Unknown project type, skipping build'",
        testCommand = null,
        detectionFiles = emptyList()
    )
}

// LLM-based Docker Validation Models

/**
 * LLM-generated validation strategy
 */
@Serializable
internal data class ValidationStrategy(
    @SerialName("approach_description")
    val approachDescription: String,
    @SerialName("project_type_analysis")
    val projectTypeAnalysis: String,
    @SerialName("dockerfile_content")
    val dockerfileContent: String,
    @SerialName("build_commands")
    val buildCommands: List<String>,
    @SerialName("test_commands")
    val testCommands: List<String>,
    @SerialName("expected_outcomes")
    val expectedOutcomes: String
)

/**
 * Result of executing a Docker command
 */
@Serializable
internal data class CommandExecutionResult(
    @SerialName("command")
    val command: String,
    @SerialName("success")
    val success: Boolean,
    @SerialName("exit_code")
    val exitCode: Int,
    @SerialName("stdout")
    val stdout: List<String>,
    @SerialName("stderr")
    val stderr: List<String>,
    @SerialName("duration_seconds")
    val durationSeconds: Int
)

/**
 * LLM analysis of validation results
 */
@Serializable
internal data class ValidationAnalysis(
    @SerialName("overall_status")
    val overallStatus: ValidationStatus,
    @SerialName("build_analysis")
    val buildAnalysis: String,
    @SerialName("test_analysis")
    val testAnalysis: String?,
    @SerialName("error_diagnosis")
    val errorDiagnosis: String?,
    @SerialName("fix_suggestions")
    val fixSuggestions: List<FixSuggestion>,
    @SerialName("should_retry")
    val shouldRetry: Boolean,
    @SerialName("retry_reason")
    val retryReason: String?
)

/**
 * Status of validation attempt
 */
@Serializable
enum class ValidationStatus {
    @SerialName("SUCCESS")
    SUCCESS,
    @SerialName("RETRY_NEEDED")
    RETRY_NEEDED,
    @SerialName("FAILED")
    FAILED
}

/**
 * LLM-suggested fix for validation failure
 */
@Serializable
internal data class FixSuggestion(
    @SerialName("description")
    val description: String,
    @SerialName("fix_type")
    val fixType: FixType,
    @SerialName("updated_dockerfile")
    val updatedDockerfile: String?,
    @SerialName("updated_build_commands")
    val updatedBuildCommands: List<String>?,
    @SerialName("updated_test_commands")
    val updatedTestCommands: List<String>?
)

/**
 * Type of fix to apply
 */
@Serializable
enum class FixType {
    @SerialName("DOCKERFILE_MODIFICATION")
    DOCKERFILE_MODIFICATION,
    @SerialName("BUILD_COMMAND_CHANGE")
    BUILD_COMMAND_CHANGE,
    @SerialName("TEST_COMMAND_CHANGE")
    TEST_COMMAND_CHANGE,
    @SerialName("DEPENDENCY_FIX")
    DEPENDENCY_FIX,
    @SerialName("CONFIGURATION_CHANGE")
    CONFIGURATION_CHANGE
}

/**
 * Final validation report from LLM
 */
@Serializable
internal data class FinalValidationReport(
    @SerialName("summary")
    val summary: String,
    @SerialName("build_status")
    val buildStatus: String,
    @SerialName("test_status")
    val testStatus: String?,
    @SerialName("recommendations")
    val recommendations: List<String>,
    @SerialName("total_attempts")
    val totalAttempts: Int,
    @SerialName("verdict")
    val verdict: String
)
