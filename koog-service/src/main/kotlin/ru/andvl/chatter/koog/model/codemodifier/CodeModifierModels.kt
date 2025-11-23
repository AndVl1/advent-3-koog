package ru.andvl.chatter.koog.model.codemodifier

import ai.koog.agents.core.tools.annotations.LLMDescription
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
    val maxChanges: Int = 50,
    @SerialName("force_skip_docker")
    val forceSkipDocker: Boolean = true
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
@LLMDescription("Complete modification plan with all proposed changes and analysis")
@Serializable
data class ModificationPlan(
    @property:LLMDescription("List of proposed code changes ordered by dependencies. Field name: changes")
    @SerialName("changes")
    val changes: List<ProposedChange>,
    @property:LLMDescription("Detailed explanation of why these changes are needed and how they achieve the goal. Field name: rationale")
    @SerialName("rationale")
    val rationale: String,
    @property:LLMDescription("Overall complexity level of this modification plan (SIMPLE, MODERATE, COMPLEX, CRITICAL). Field name: estimated_complexity")
    @SerialName("estimated_complexity")
    val estimatedComplexity: Complexity,
    @property:LLMDescription("Whether changes are sorted by dependency order to avoid conflicts. Field name: dependencies_sorted")
    @SerialName("dependencies_sorted")
    val dependenciesSorted: Boolean = false
)

/**
 * A single proposed change to a file
 */
@LLMDescription("A single proposed code change to be applied to a file")
@Serializable
data class ProposedChange(
    @property:LLMDescription("Unique identifier for this change. Auto-generated if not provided. Field name: change_id")
    @SerialName("change_id")
    val changeId: String = "",
    @property:LLMDescription("Full path to the file to be modified. Field name: file_path")
    @SerialName("file_path")
    val filePath: String,
    @property:LLMDescription("Type of change: CREATE, MODIFY, DELETE, RENAME, or REFACTOR. Field name: change_type")
    @SerialName("change_type")
    val changeType: ChangeType,
    @property:LLMDescription("Clear description of what this change does and why. Field name: description")
    @SerialName("description")
    val description: String,
    @property:LLMDescription("Starting line number for modification (1-based). Optional for CREATE. Field name: start_line")
    @SerialName("start_line")
    val startLine: Int? = null,
    @property:LLMDescription("Ending line number for modification (1-based). Optional for CREATE. Field name: end_line")
    @SerialName("end_line")
    val endLine: Int? = null,
    @property:LLMDescription("The new code content to insert or replace with. Field name: new_content")
    @SerialName("new_content")
    val newContent: String,
    @property:LLMDescription("Original code content being replaced. Optional. Field name: old_content")
    @SerialName("old_content")
    val oldContent: String? = null,
    @property:LLMDescription("List of change_ids that must be applied before this one. Field name: depends_on")
    @SerialName("depends_on")
    val dependsOn: List<String> = emptyList(),
    @property:LLMDescription("Additional notes about validation or potential issues. Optional. Field name: validation_notes")
    @SerialName("validation_notes")
    val validationNotes: String? = null
)

/**
 * Type of code modification
 */
@LLMDescription("Type of code modification operation: CREATE (new file), MODIFY (edit existing), DELETE (remove), RENAME (move/rename), REFACTOR (restructure)")
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
@LLMDescription("Complexity level: SIMPLE (trivial changes), MODERATE (standard refactoring), COMPLEX (significant restructuring), CRITICAL (risky/breaking changes)")
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
@LLMDescription("LLM-generated strategy for validating code changes using Docker")
@Serializable
internal data class ValidationStrategy(
    @property:LLMDescription("Detailed description of the validation approach and methodology. Field name: approach_description")
    @SerialName("approach_description")
    val approachDescription: String,
    @property:LLMDescription("Analysis of the project type and technologies detected. Field name: project_type_analysis")
    @SerialName("project_type_analysis")
    val projectTypeAnalysis: String,
    @property:LLMDescription("Complete Dockerfile content to be used for validation. Field name: dockerfile_content")
    @SerialName("dockerfile_content")
    val dockerfileContent: String,
    @property:LLMDescription("List of shell commands to build the project. Field name: build_commands")
    @SerialName("build_commands")
    val buildCommands: List<String>,
    @property:LLMDescription("List of shell commands to run tests. Field name: test_commands")
    @SerialName("test_commands")
    val testCommands: List<String>,
    @property:LLMDescription("Expected outcomes of successful build and test execution. Field name: expected_outcomes")
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
@LLMDescription("LLM analysis of Docker validation results with diagnostics and fix suggestions")
@Serializable
internal data class ValidationAnalysis(
    @property:LLMDescription("Overall validation status: SUCCESS, RETRY_NEEDED, or FAILED. Field name: overall_status")
    @SerialName("overall_status")
    val overallStatus: ValidationStatus,
    @property:LLMDescription("Detailed analysis of the build process and its output. Field name: build_analysis")
    @SerialName("build_analysis")
    val buildAnalysis: String,
    @property:LLMDescription("Detailed analysis of test execution and results. Optional. Field name: test_analysis")
    @SerialName("test_analysis")
    val testAnalysis: String?,
    @property:LLMDescription("Diagnosis of errors encountered during validation. Optional. Field name: error_diagnosis")
    @SerialName("error_diagnosis")
    val errorDiagnosis: String?,
    @property:LLMDescription("List of suggested fixes to resolve validation failures. Field name: fix_suggestions")
    @SerialName("fix_suggestions")
    val fixSuggestions: List<FixSuggestion>,
    @property:LLMDescription("Whether validation should be retried with fixes applied. Field name: should_retry")
    @SerialName("should_retry")
    val shouldRetry: Boolean,
    @property:LLMDescription("Reason why retry is recommended or not. Optional. Field name: retry_reason")
    @SerialName("retry_reason")
    val retryReason: String?
)

/**
 * Status of validation attempt
 */
@LLMDescription("Validation status: SUCCESS (passed all checks), RETRY_NEEDED (fixable failures), FAILED (unrecoverable errors)")
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
@LLMDescription("LLM-suggested fix to resolve a validation failure")
@Serializable
internal data class FixSuggestion(
    @property:LLMDescription("Clear description of the fix and what it addresses. Field name: description")
    @SerialName("description")
    val description: String,
    @property:LLMDescription("Type of fix: DOCKERFILE_MODIFICATION, BUILD_COMMAND_CHANGE, TEST_COMMAND_CHANGE, DEPENDENCY_FIX, or CONFIGURATION_CHANGE. Field name: fix_type")
    @SerialName("fix_type")
    val fixType: FixType,
    @property:LLMDescription("Updated Dockerfile content if fix requires Dockerfile changes. Optional. Field name: updated_dockerfile")
    @SerialName("updated_dockerfile")
    val updatedDockerfile: String?,
    @property:LLMDescription("Updated build commands if fix requires command changes. Optional. Field name: updated_build_commands")
    @SerialName("updated_build_commands")
    val updatedBuildCommands: List<String>?,
    @property:LLMDescription("Updated test commands if fix requires test command changes. Optional. Field name: updated_test_commands")
    @SerialName("updated_test_commands")
    val updatedTestCommands: List<String>?
)

/**
 * Type of fix to apply
 */
@LLMDescription("Type of fix: DOCKERFILE_MODIFICATION (change Dockerfile), BUILD_COMMAND_CHANGE (modify build commands), TEST_COMMAND_CHANGE (modify test commands), DEPENDENCY_FIX (fix dependencies), CONFIGURATION_CHANGE (change configuration)")
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
@LLMDescription("Final validation report summarizing all validation attempts and outcomes")
@Serializable
internal data class FinalValidationReport(
    @property:LLMDescription("Executive summary of the validation process and results. Field name: summary")
    @SerialName("summary")
    val summary: String,
    @property:LLMDescription("Status of the build process (e.g., 'SUCCESS', 'FAILED', 'PARTIAL'). Field name: build_status")
    @SerialName("build_status")
    val buildStatus: String,
    @property:LLMDescription("Status of test execution if tests were run. Optional. Field name: test_status")
    @SerialName("test_status")
    val testStatus: String?,
    @property:LLMDescription("List of recommendations for improving code quality or fixing issues. Field name: recommendations")
    @SerialName("recommendations")
    val recommendations: List<String>,
    @property:LLMDescription("Total number of validation attempts made. Field name: total_attempts")
    @SerialName("total_attempts")
    val totalAttempts: Int,
    @property:LLMDescription("Final verdict on whether changes are safe to apply. Field name: verdict")
    @SerialName("verdict")
    val verdict: String
)
