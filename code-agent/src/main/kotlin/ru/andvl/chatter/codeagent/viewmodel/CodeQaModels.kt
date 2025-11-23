package ru.andvl.chatter.codeagent.viewmodel

import ru.andvl.chatter.shared.models.codeagent.CodeQAMessage
import ru.andvl.chatter.shared.models.codeagent.CodeReference
import ru.andvl.chatter.shared.models.codeagent.MessageRole

/**
 * UI state for Code QA screen
 *
 * This state represents the complete UI state for the code question-answer feature.
 * It follows Clean Architecture principles by separating presentation state from domain models.
 *
 * @property sessionId Current session ID (null if no active session)
 * @property repositoryName Name of the analyzed repository
 * @property messages List of conversation messages
 * @property currentQuestion Question being typed by user
 * @property isLoading Whether AI is processing a question
 * @property error Error message if operation failed (null if no error)
 */
data class CodeQaUiState(
    val sessionId: String? = null,
    val repositoryName: String = "",
    val messages: List<CodeQaMessageUi> = emptyList(),
    val currentQuestion: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /**
     * Returns true if there is an active session
     */
    val hasSession: Boolean
        get() = sessionId != null

    /**
     * Returns true if the send button should be enabled
     */
    val canSendQuestion: Boolean
        get() = hasSession && currentQuestion.isNotBlank() && !isLoading

    /**
     * Returns true if conversation has started
     */
    val hasMessages: Boolean
        get() = messages.isNotEmpty()

    /**
     * Returns true if there is an error to display
     */
    val hasError: Boolean
        get() = error != null
}

/**
 * UI representation of a Code QA message
 *
 * @property id Unique message ID for stable keys in LazyColumn
 * @property role Message role (USER or ASSISTANT)
 * @property content Message text content
 * @property timestamp Message timestamp
 * @property codeReferences List of code references included in the message
 * @property isExpanded Whether code references are expanded (UI-only state)
 */
data class CodeQaMessageUi(
    val id: String,
    val role: CodeQaRole,
    val content: String,
    val timestamp: Long,
    val codeReferences: List<CodeReferenceUi> = emptyList(),
    val isExpanded: Boolean = false
) {
    /**
     * Returns true if message has code references
     */
    val hasCodeReferences: Boolean
        get() = codeReferences.isNotEmpty()

    companion object {
        /**
         * Convert backend model to UI model
         */
        fun fromBackendModel(message: CodeQAMessage): CodeQaMessageUi {
            return CodeQaMessageUi(
                id = "${message.role.name}_${message.timestamp}",
                role = when (message.role) {
                    MessageRole.USER -> CodeQaRole.USER
                    MessageRole.ASSISTANT -> CodeQaRole.ASSISTANT
                },
                content = message.content,
                timestamp = message.timestamp,
                codeReferences = message.codeReferences.map { CodeReferenceUi.fromBackendModel(it) },
                isExpanded = false
            )
        }
    }
}

/**
 * Message role in conversation
 */
enum class CodeQaRole {
    USER,
    ASSISTANT
}

/**
 * UI representation of a code reference
 *
 * @property filePath Full path to the file
 * @property lineStart Start line number (1-based)
 * @property lineEnd End line number (1-based)
 * @property codeSnippet The actual code snippet
 * @property language Detected programming language for syntax highlighting
 * @property displayPath Shortened file path for display
 */
data class CodeReferenceUi(
    val filePath: String,
    val lineStart: Int,
    val lineEnd: Int,
    val codeSnippet: String,
    val language: String,
    val displayPath: String
) {
    /**
     * Returns formatted line range string
     */
    val lineRange: String
        get() = if (lineStart == lineEnd) {
            "Line $lineStart"
        } else {
            "Lines $lineStart-$lineEnd"
        }

    companion object {
        /**
         * Convert backend model to UI model
         */
        fun fromBackendModel(reference: CodeReference): CodeReferenceUi {
            return CodeReferenceUi(
                filePath = reference.filePath,
                lineStart = reference.lineStart,
                lineEnd = reference.lineEnd,
                codeSnippet = reference.codeSnippet,
                language = detectLanguage(reference.filePath),
                displayPath = shortenFilePath(reference.filePath)
            )
        }
    }
}

/**
 * Detect programming language from file path
 *
 * @param filePath Path to the file
 * @return Detected language identifier for syntax highlighting
 */
fun detectLanguage(filePath: String): String {
    val extension = filePath.substringAfterLast('.', "")
    return when (extension.lowercase()) {
        "kt" -> "kotlin"
        "java" -> "java"
        "js" -> "javascript"
        "ts" -> "typescript"
        "py" -> "python"
        "rs" -> "rust"
        "go" -> "go"
        "cpp", "cc", "cxx" -> "cpp"
        "c" -> "c"
        "h", "hpp" -> "cpp"
        "cs" -> "csharp"
        "rb" -> "ruby"
        "php" -> "php"
        "swift" -> "swift"
        "scala" -> "scala"
        "json" -> "json"
        "xml" -> "xml"
        "yaml", "yml" -> "yaml"
        "md" -> "markdown"
        "sh", "bash" -> "bash"
        "sql" -> "sql"
        "html" -> "html"
        "css" -> "css"
        "gradle" -> "gradle"
        else -> "text"
    }
}

/**
 * Shorten file path for display
 *
 * Keeps the filename and last 2 directory components if path is too long.
 * Example: /very/long/path/to/src/main/File.kt -> .../main/File.kt
 *
 * @param filePath Full file path
 * @return Shortened path for display
 */
fun shortenFilePath(filePath: String): String {
    val parts = filePath.split('/')
    return if (parts.size > 3) {
        ".../" + parts.takeLast(2).joinToString("/")
    } else {
        filePath
    }
}
