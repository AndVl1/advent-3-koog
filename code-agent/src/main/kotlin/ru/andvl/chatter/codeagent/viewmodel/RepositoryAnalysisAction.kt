package ru.andvl.chatter.codeagent.viewmodel

/**
 * Sealed interface representing all possible user actions in Repository Analysis screen
 *
 * This follows the Unidirectional Data Flow (UDF) pattern:
 * - UI sends actions to ViewModel via dispatch()
 * - ViewModel processes actions and updates state
 * - UI observes state and recomposes
 */
sealed interface RepositoryAnalysisAction {
    /**
     * User updated the GitHub URL field
     */
    data class UpdateGitHubUrl(val url: String) : RepositoryAnalysisAction

    /**
     * User clicked the "Analyze" button
     */
    data object StartAnalysis : RepositoryAnalysisAction

    /**
     * User clicked the "Clear" button to reset the screen
     */
    data object ClearResult : RepositoryAnalysisAction

    /**
     * User clicked on a tree node to expand/collapse it
     */
    data class ToggleTreeNode(val nodePath: String) : RepositoryAnalysisAction
}
