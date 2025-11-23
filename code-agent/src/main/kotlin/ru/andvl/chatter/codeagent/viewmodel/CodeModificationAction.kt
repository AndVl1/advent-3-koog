package ru.andvl.chatter.codeagent.viewmodel

/**
 * Actions for Code Modification screen
 *
 * This sealed interface defines all possible user actions in the Code Modification UI.
 * It implements the Unidirectional Data Flow pattern:
 * - UI dispatches actions
 * - ViewModel processes actions and updates state
 * - UI re-renders based on new state
 */
sealed interface CodeModificationAction {
    /**
     * Initialize session from Repository Analysis
     *
     * @property sessionId Session ID from repository analysis
     * @property repositoryName Name of the analyzed repository
     */
    data class InitializeSession(
        val sessionId: String,
        val repositoryName: String
    ) : CodeModificationAction

    /**
     * Pre-fill request from Code QA conversation
     *
     * @property qaContext Context from Code QA to include in modification request
     */
    data class PreFillFromQA(
        val qaContext: String
    ) : CodeModificationAction

    /**
     * Update modification request text
     *
     * @property request New modification request text
     */
    data class UpdateRequest(
        val request: String
    ) : CodeModificationAction

    /**
     * Update file scope selection
     *
     * @property files List of file paths to limit scope (null = all files)
     */
    data class UpdateFileScope(
        val files: List<String>?
    ) : CodeModificationAction

    /**
     * Generate modification plan
     *
     * Triggers AI to analyze request and generate a plan with proposed changes.
     */
    data object GeneratePlan : CodeModificationAction

    /**
     * Toggle selection of a specific change
     *
     * @property changeId ID of the change to toggle
     */
    data class ToggleChangeSelection(
        val changeId: String
    ) : CodeModificationAction

    /**
     * Select all changes in the plan
     */
    data object SelectAllChanges : CodeModificationAction

    /**
     * Deselect all changes in the plan
     */
    data object DeselectAllChanges : CodeModificationAction

    /**
     * View details of a specific change
     *
     * @property changeId ID of the change to view (null = close details panel)
     */
    data class ViewChangeDetails(
        val changeId: String?
    ) : CodeModificationAction

    /**
     * Switch diff view mode
     *
     * @property mode New diff view mode
     */
    data class SwitchDiffViewMode(
        val mode: DiffViewMode
    ) : CodeModificationAction

    /**
     * Apply selected changes to repository
     *
     * Applies all changes that are currently selected by the user.
     */
    data object ApplySelectedChanges : CodeModificationAction

    /**
     * Update commit message
     *
     * @property message New commit message
     */
    data class UpdateCommitMessage(
        val message: String
    ) : CodeModificationAction

    /**
     * Toggle create branch option
     */
    data object ToggleCreateBranch : CodeModificationAction

    /**
     * Update branch name
     *
     * @property branchName Custom branch name (null = auto-generate)
     */
    data class UpdateBranchName(
        val branchName: String?
    ) : CodeModificationAction

    /**
     * Commit applied changes to git
     *
     * Creates a git commit with the specified message and branch settings.
     */
    data object CommitChanges : CodeModificationAction

    /**
     * Dismiss error message
     */
    data object DismissError : CodeModificationAction
}
