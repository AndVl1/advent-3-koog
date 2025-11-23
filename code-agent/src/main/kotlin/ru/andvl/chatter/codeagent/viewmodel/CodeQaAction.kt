package ru.andvl.chatter.codeagent.viewmodel

/**
 * Sealed interface representing all possible user actions in Code QA screen
 *
 * This follows the Unidirectional Data Flow pattern where:
 * - UI emits actions
 * - ViewModel processes actions
 * - ViewModel updates state
 * - UI observes state and recomposes
 */
sealed interface CodeQaAction {
    /**
     * User updated the question text field
     *
     * @property question New question text
     */
    data class UpdateQuestion(val question: String) : CodeQaAction

    /**
     * User clicked Send button or pressed Enter
     *
     * This will submit the current question to the AI assistant.
     */
    data object SendQuestion : CodeQaAction

    /**
     * User clicked Clear/Reset button
     *
     * This will clear the conversation history and reset the session.
     */
    data object ClearConversation : CodeQaAction

    /**
     * User toggled code reference expansion
     *
     * @property messageId ID of the message containing the reference
     * @property referenceIndex Index of the reference to toggle
     */
    data class ToggleCodeReference(
        val messageId: String,
        val referenceIndex: Int
    ) : CodeQaAction

    /**
     * User clicked Copy button on code snippet
     *
     * @property codeSnippet The code snippet to copy to clipboard
     */
    data class CopyCodeSnippet(val codeSnippet: String) : CodeQaAction

    /**
     * User dismissed the error message
     */
    data object DismissError : CodeQaAction

    /**
     * Initialize session from repository analysis result
     *
     * This is called when switching from Repository Analysis tab to Code QA tab.
     *
     * @property sessionId Session ID from analysis
     * @property repositoryName Name of the analyzed repository
     */
    data class InitializeSession(
        val sessionId: String,
        val repositoryName: String
    ) : CodeQaAction
}
