package ru.andvl.chatter.codeagent.viewmodel

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.cdimascio.dotenv.Dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.andvl.chatter.codeagent.repository.CodeQaRepository
import ru.andvl.chatter.codeagent.repository.CodeQaRepositoryImpl
import ru.andvl.chatter.koog.service.KoogServiceFactory
import java.io.File

/**
 * ViewModel for Code QA screen
 *
 * This ViewModel manages UI state and handles user actions for the Code QA feature.
 * It follows Clean Architecture and MVVM principles:
 * - UI layer observes state via StateFlow
 * - UI sends actions via dispatch()
 * - ViewModel delegates business logic to Repository
 * - Uses Dispatchers.Main for UI updates (kotlinx-coroutines-swing)
 *
 * @property repository Repository for Code QA operations
 */
class CodeQaViewModel(
    private val repository: CodeQaRepository = createDefaultRepository()
) {
    private val logger = KotlinLogging.logger {}

    // ViewModel scope with SupervisorJob for proper coroutine management
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Mutable state for internal updates
    private val _state = MutableStateFlow(CodeQaUiState())

    // Public immutable state for UI observation
    val state: StateFlow<CodeQaUiState> = _state.asStateFlow()

    // Lazy-initialized PromptExecutor from .env
    private val promptExecutor: PromptExecutor by lazy {
        createPromptExecutor()
    }

    /**
     * Dispatch user action
     *
     * This is the single entry point for all user actions.
     * It implements the Unidirectional Data Flow pattern.
     *
     * @param action User action to process
     */
    fun dispatch(action: CodeQaAction) {
        logger.debug { "Dispatching action: ${action::class.simpleName}" }

        when (action) {
            is CodeQaAction.InitializeSession -> handleInitializeSession(action)
            is CodeQaAction.UpdateQuestion -> handleUpdateQuestion(action.question)
            is CodeQaAction.SendQuestion -> handleSendQuestion()
            is CodeQaAction.ClearConversation -> handleClearConversation()
            is CodeQaAction.ToggleCodeReference -> handleToggleCodeReference(action)
            is CodeQaAction.CopyCodeSnippet -> handleCopyCodeSnippet(action.codeSnippet)
            is CodeQaAction.DismissError -> handleDismissError()
        }
    }

    /**
     * Handle InitializeSession action
     */
    private fun handleInitializeSession(action: CodeQaAction.InitializeSession) {
        _state.update {
            CodeQaUiState(
                sessionId = action.sessionId,
                repositoryName = action.repositoryName
            )
        }
        logger.info { "Session initialized: ${action.sessionId}, repository: ${action.repositoryName}" }
    }

    /**
     * Handle UpdateQuestion action
     */
    private fun handleUpdateQuestion(question: String) {
        _state.update { it.copy(currentQuestion = question, error = null) }
    }

    /**
     * Handle SendQuestion action
     */
    private fun handleSendQuestion() {
        val currentState = _state.value

        // Validate state
        if (!currentState.hasSession) {
            _state.update { it.copy(error = "No active session. Please analyze a repository first.") }
            return
        }

        if (currentState.currentQuestion.isBlank()) {
            return
        }

        val sessionId = currentState.sessionId!!
        val question = currentState.currentQuestion.trim()

        // Add user message immediately
        val userMessage = CodeQaMessageUi(
            id = "user_${System.currentTimeMillis()}",
            role = CodeQaRole.USER,
            content = question,
            timestamp = System.currentTimeMillis()
        )

        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                currentQuestion = "",
                isLoading = true,
                error = null
            )
        }

        // Send question to backend
        viewModelScope.launch(Dispatchers.IO) {
            try {
                logger.info { "Sending question to backend: $question" }

                val result = repository.askQuestion(
                    sessionId = sessionId,
                    question = question,
                    history = currentState.messages,
                    promptExecutor = promptExecutor
                )

                // Update state on Main dispatcher
                launch(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val response = result.getOrThrow()
                        logger.info { "Received response from backend" }

                        // Create assistant message from response
                        val assistantMessage = CodeQaMessageUi(
                            id = "assistant_${System.currentTimeMillis()}",
                            role = CodeQaRole.ASSISTANT,
                            content = response.answer,
                            timestamp = System.currentTimeMillis(),
                            codeReferences = response.codeReferences.map {
                                CodeReferenceUi.fromBackendModel(it)
                            }
                        )

                        _state.update {
                            it.copy(
                                messages = it.messages + assistantMessage,
                                isLoading = false
                            )
                        }
                    } else {
                        val errorMessage = result.exceptionOrNull()?.message
                            ?: "Failed to get response from AI"
                        logger.error { "Question failed: $errorMessage" }

                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = errorMessage
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Question threw exception" }

                launch(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Error: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Handle ClearConversation action
     */
    private fun handleClearConversation() {
        _state.update {
            it.copy(
                messages = emptyList(),
                currentQuestion = "",
                isLoading = false,
                error = null
            )
        }
        logger.info { "Conversation cleared" }
    }

    /**
     * Handle ToggleCodeReference action
     */
    private fun handleToggleCodeReference(action: CodeQaAction.ToggleCodeReference) {
        _state.update { currentState ->
            val updatedMessages = currentState.messages.map { message ->
                if (message.id == action.messageId) {
                    // Toggle expansion state
                    message.copy(isExpanded = !message.isExpanded)
                } else {
                    message
                }
            }
            currentState.copy(messages = updatedMessages)
        }
    }

    /**
     * Handle CopyCodeSnippet action
     *
     * Note: Actual clipboard copy is handled in the UI layer using LocalClipboard.
     * This action is here for completeness and logging.
     */
    private fun handleCopyCodeSnippet(codeSnippet: String) {
        logger.info { "Copy code snippet requested: ${codeSnippet.take(50)}..." }
    }

    /**
     * Handle DismissError action
     */
    private fun handleDismissError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Create PromptExecutor from .env configuration
     */
    private fun createPromptExecutor(): PromptExecutor {
        val projectRoot = File(System.getProperty("user.dir"))
        val dotenv = Dotenv.configure()
            .directory(projectRoot.absolutePath)
            .ignoreIfMissing()
            .load()

        val apiKey = dotenv["OPENROUTER_API_KEY"]
            ?: throw IllegalStateException("OPENROUTER_API_KEY not found in .env")

        logger.info { "Creating PromptExecutor with OpenRouter" }

        val timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 120_000, // 2 minutes
            connectTimeoutMillis = 10_000   // 10 seconds
        )

        val settings = OpenRouterClientSettings(
            timeoutConfig = timeoutConfig
        )

        val baseClient = OpenRouterLLMClient(
            apiKey = apiKey,
            settings = settings
        )

        val resilientClient = RetryingLLMClient(
            delegate = baseClient,
            config = RetryConfig.CONSERVATIVE.copy(
                retryablePatterns = RetryConfig.DEFAULT_PATTERNS
            )
        )

        return SingleLLMPromptExecutor(resilientClient)
    }

    /**
     * Cleanup method - should be called when ViewModel is no longer needed
     */
    fun onCleared() {
        logger.info { "ViewModel cleared, cancelling coroutines" }
        viewModelScope.cancel()
    }

    companion object {
        /**
         * Create default repository instance
         */
        private fun createDefaultRepository(): CodeQaRepository {
            val koogService = KoogServiceFactory.createFromEnv()
            return CodeQaRepositoryImpl(koogService)
        }
    }
}
