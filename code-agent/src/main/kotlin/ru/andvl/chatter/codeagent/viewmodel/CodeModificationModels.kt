package ru.andvl.chatter.codeagent.viewmodel

import ru.andvl.chatter.shared.models.codeagent.ChecklistTask
import ru.andvl.chatter.shared.models.codeagent.ModificationChecklist
import ru.andvl.chatter.shared.models.codeagent.TaskStatus

/**
 * UI state for Code Modification screen
 *
 * This state represents the complete UI state for the code modification feature.
 * It follows Clean Architecture principles by separating presentation state from domain models.
 *
 * @property sessionId Current session ID (null if no active session)
 * @property repositoryName Name of the analyzed repository
 * @property modificationRequest Current modification request text
 * @property fileScope Selected files for modification scope (null = all files)
 * @property contextFromQA Context pre-filled from Code QA
 * @property modificationPlan Current modification plan (null if not yet generated)
 * @property selectedChanges Set of change IDs that user has selected
 * @property selectedChangeForDetails ID of change selected for viewing details
 * @property diffViewMode Current diff view mode (UNIFIED or SIDE_BY_SIDE)
 * @property commitMessage Git commit message
 * @property createBranch Whether to create a new branch
 * @property branchName Custom branch name (null = auto-generate)
 * @property isLoadingPlan Whether plan is being generated
 * @property isApplyingChanges Whether changes are being applied
 * @property isCommitting Whether git commit is in progress
 * @property error Error message if operation failed (null if no error)
 */
data class CodeModificationUiState(
    val sessionId: String? = null,
    val repositoryName: String = "",
    val modificationRequest: String = "",
    val fileScope: List<String>? = null,
    val contextFromQA: String? = null,
    val modificationPlan: ModificationPlanUi? = null,
    val selectedChanges: Set<String> = emptySet(),
    val selectedChangeForDetails: String? = null,
    val diffViewMode: DiffViewMode = DiffViewMode.UNIFIED,
    val commitMessage: String = "",
    val createBranch: Boolean = true,
    val branchName: String? = null,
    val isLoadingPlan: Boolean = false,
    val isApplyingChanges: Boolean = false,
    val isCommitting: Boolean = false,
    val error: String? = null
) {
    /**
     * Returns true if there is an active session
     */
    val hasSession: Boolean
        get() = sessionId != null

    /**
     * Returns true if the generate plan button should be enabled
     */
    val canGeneratePlan: Boolean
        get() = hasSession && modificationRequest.isNotBlank() && !isLoadingPlan

    /**
     * Returns true if the apply changes button should be enabled
     */
    val canApplyChanges: Boolean
        get() = modificationPlan != null && selectedChanges.isNotEmpty() && !isApplyingChanges

    /**
     * Returns true if the commit button should be enabled
     */
    val canCommit: Boolean
        get() = modificationPlan != null &&
                modificationPlan.appliedChanges.isNotEmpty() &&
                commitMessage.isNotBlank() &&
                !isCommitting

    /**
     * Returns true if there is an error to display
     */
    val hasError: Boolean
        get() = error != null

    /**
     * Returns selected change for details panel
     */
    val selectedChange: FileChangeUi?
        get() = modificationPlan?.changes?.find { it.id == selectedChangeForDetails }
}

/**
 * UI representation of a modification plan
 *
 * @property id Plan ID
 * @property summary Summary of planned changes
 * @property complexity Estimated complexity
 * @property warnings List of warnings about potential issues
 * @property changes List of proposed file changes
 * @property appliedChanges Set of change IDs that have been applied
 * @property commitSha Git commit SHA if committed
 * @property branchName Git branch name if created
 */
data class ModificationPlanUi(
    val id: String,
    val summary: String,
    val complexity: ModificationComplexity,
    val warnings: List<String> = emptyList(),
    val changes: List<FileChangeUi> = emptyList(),
    val appliedChanges: Set<String> = emptySet(),
    val commitSha: String? = null,
    val branchName: String? = null
) {
    /**
     * Returns count of changes by type
     */
    val changeTypeCounts: Map<ChangeType, Int>
        get() = changes.groupingBy { it.changeType }.eachCount()

    /**
     * Returns total number of changes
     */
    val totalChanges: Int
        get() = changes.size

    /**
     * Returns number of selected changes
     */
    fun selectedCount(selected: Set<String>): Int =
        changes.count { it.id in selected }

    /**
     * Returns progress as percentage
     */
    val progressPercentage: Int
        get() = if (changes.isEmpty()) 0
        else (appliedChanges.size * 100) / changes.size

    companion object {
        /**
         * Convert backend model to UI model
         */
        fun fromBackendModel(response: ru.andvl.chatter.shared.models.codeagent.CodeModificationResponse): ModificationPlanUi {
            val plan = response.modificationPlan
                ?: return ModificationPlanUi(
                    id = System.currentTimeMillis().toString(),
                    summary = response.errorMessage ?: "No modification plan available",
                    complexity = ModificationComplexity.LOW,
                    warnings = listOfNotNull(response.errorMessage),
                    changes = emptyList(),
                    appliedChanges = emptySet(),
                    commitSha = null,
                    branchName = null
                )

            return ModificationPlanUi(
                id = System.currentTimeMillis().toString(),
                summary = plan.rationale,
                complexity = when (plan.estimatedComplexity.uppercase()) {
                    "SIMPLE" -> ModificationComplexity.LOW
                    "MODERATE" -> ModificationComplexity.MEDIUM
                    "COMPLEX" -> ModificationComplexity.HIGH
                    "CRITICAL" -> ModificationComplexity.HIGH
                    else -> ModificationComplexity.MEDIUM
                },
                warnings = if (response.breakingChangesDetected) {
                    listOf("Breaking changes detected")
                } else {
                    emptyList()
                },
                changes = plan.changes.map { FileChangeUi.fromBackendModel(it) },
                appliedChanges = emptySet(), // No changes applied yet
                commitSha = null, // Git integration not implemented yet
                branchName = null // Git integration not implemented yet
            )
        }
    }
}

/**
 * UI representation of a file change
 *
 * @property id Change ID
 * @property filePath Path to the file
 * @property changeType Type of change (CREATE, MODIFY, DELETE)
 * @property lineRange Line range affected (null for CREATE/DELETE)
 * @property originalCode Original code (null for CREATE)
 * @property modifiedCode Modified code (null for DELETE)
 * @property reasoning AI reasoning for this change
 * @property confidence Confidence score (0.0 to 1.0)
 * @property status Current status of the change
 * @property verificationResult Result of verification after application
 */
data class FileChangeUi(
    val id: String,
    val filePath: String,
    val changeType: ChangeType,
    val lineRange: LineRange?,
    val originalCode: String?,
    val modifiedCode: String?,
    val reasoning: String,
    val confidence: Float,
    val status: ChangeStatus,
    val verificationResult: String?
) {
    /**
     * Returns shortened file path for display
     */
    val displayPath: String
        get() = shortenFilePath(filePath)

    /**
     * Returns formatted line range string
     */
    val lineRangeText: String
        get() = lineRange?.let { "Lines ${it.start}-${it.end}" } ?: "Full file"

    /**
     * Returns confidence percentage
     */
    val confidencePercentage: Int
        get() = (confidence * 100).toInt()

    /**
     * Returns diff lines for unified diff view
     */
    val diffLines: List<DiffLine>
        get() = generateDiffLines()

    /**
     * Generate diff lines from original and modified code
     */
    private fun generateDiffLines(): List<DiffLine> {
        val result = mutableListOf<DiffLine>()

        when (changeType) {
            ChangeType.CREATE -> {
                modifiedCode?.lines()?.forEachIndexed { index, line ->
                    result.add(DiffLine(index + 1, null, line, DiffType.ADDED))
                }
            }
            ChangeType.DELETE -> {
                originalCode?.lines()?.forEachIndexed { index, line ->
                    result.add(DiffLine(index + 1, line, null, DiffType.REMOVED))
                }
            }
            ChangeType.MODIFY -> {
                val original = originalCode?.lines() ?: emptyList()
                val modified = modifiedCode?.lines() ?: emptyList()

                // Simple line-by-line diff
                val maxLines = maxOf(original.size, modified.size)
                for (i in 0 until maxLines) {
                    val origLine = original.getOrNull(i)
                    val modLine = modified.getOrNull(i)

                    val diffType = when {
                        origLine == modLine -> DiffType.UNCHANGED
                        origLine == null -> DiffType.ADDED
                        modLine == null -> DiffType.REMOVED
                        else -> DiffType.MODIFIED
                    }

                    result.add(DiffLine(i + 1, origLine, modLine, diffType))
                }
            }
        }

        return result
    }

    companion object {
        /**
         * Convert backend model to UI model
         */
        fun fromBackendModel(change: ru.andvl.chatter.shared.models.codeagent.ProposedChange): FileChangeUi {
            val startLine = change.startLine
            val endLine = change.endLine
            val lineRange = if (startLine != null && endLine != null) {
                LineRange(start = startLine, end = endLine)
            } else null

            return FileChangeUi(
                id = change.changeId,
                filePath = change.filePath,
                changeType = when (change.changeType.uppercase()) {
                    "CREATE" -> ChangeType.CREATE
                    "DELETE" -> ChangeType.DELETE
                    "RENAME" -> ChangeType.MODIFY
                    "REFACTOR" -> ChangeType.MODIFY
                    else -> ChangeType.MODIFY
                },
                lineRange = lineRange,
                originalCode = change.oldContent,
                modifiedCode = change.newContent,
                reasoning = change.description,
                confidence = if (change.validationNotes != null) 0.75f else 0.85f,
                status = ChangeStatus.PENDING, // All changes start as pending
                verificationResult = change.validationNotes
            )
        }
    }
}

/**
 * Line range in a file
 */
data class LineRange(
    val start: Int,
    val end: Int
)

/**
 * Type of change to a file
 */
enum class ChangeType {
    CREATE,
    MODIFY,
    DELETE
}

/**
 * Modification complexity level
 */
enum class ModificationComplexity {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Status of a change
 */
enum class ChangeStatus {
    PENDING,
    IN_PROGRESS,
    APPLIED,
    FAILED
}

/**
 * Diff view mode
 */
enum class DiffViewMode {
    UNIFIED,
    SIDE_BY_SIDE
}

/**
 * A line in a diff view
 */
data class DiffLine(
    val lineNumber: Int,
    val originalContent: String?,
    val modifiedContent: String?,
    val type: DiffType
)

/**
 * Type of diff line
 */
enum class DiffType {
    UNCHANGED,
    ADDED,
    REMOVED,
    MODIFIED
}
