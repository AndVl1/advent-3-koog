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
import ru.andvl.chatter.codeagent.repository.CodeModificationRepository
import ru.andvl.chatter.codeagent.repository.CodeModificationRepositoryImpl
import ru.andvl.chatter.koog.service.KoogServiceFactory
import java.io.File

/**
 * ViewModel for Code Modification screen
 *
 * This ViewModel manages UI state and handles user actions for the Code Modification feature.
 * It follows Clean Architecture and MVVM principles:
 * - UI layer observes state via StateFlow
 * - UI sends actions via dispatch()
 * - ViewModel delegates business logic to Repository
 * - Uses Dispatchers.Main for UI updates (kotlinx-coroutines-swing)
 *
 * @property repository Repository for Code Modification operations
 */
class CodeModificationViewModel(
    private val repository: CodeModificationRepository = createDefaultRepository()
) {
    private val logger = KotlinLogging.logger {}

    // ViewModel scope with SupervisorJob for proper coroutine management
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Mutable state for internal updates
    private val _state = MutableStateFlow(CodeModificationUiState())

    // Public immutable state for UI observation
    val state: StateFlow<CodeModificationUiState> = _state.asStateFlow()

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
    fun dispatch(action: CodeModificationAction) {
        logger.debug { "Dispatching action: ${action::class.simpleName}" }

        when (action) {
            is CodeModificationAction.InitializeSession -> handleInitializeSession(action)
            is CodeModificationAction.PreFillFromQA -> handlePreFillFromQA(action)
            is CodeModificationAction.UpdateRequest -> handleUpdateRequest(action.request)
            is CodeModificationAction.UpdateFileScope -> handleUpdateFileScope(action.files)
            is CodeModificationAction.GeneratePlan -> handleGeneratePlan()
            is CodeModificationAction.ToggleChangeSelection -> handleToggleChangeSelection(action.changeId)
            is CodeModificationAction.SelectAllChanges -> handleSelectAllChanges()
            is CodeModificationAction.DeselectAllChanges -> handleDeselectAllChanges()
            is CodeModificationAction.ViewChangeDetails -> handleViewChangeDetails(action.changeId)
            is CodeModificationAction.SwitchDiffViewMode -> handleSwitchDiffViewMode(action.mode)
            is CodeModificationAction.ApplySelectedChanges -> handleApplySelectedChanges()
            is CodeModificationAction.UpdateCommitMessage -> handleUpdateCommitMessage(action.message)
            is CodeModificationAction.ToggleCreateBranch -> handleToggleCreateBranch()
            is CodeModificationAction.UpdateBranchName -> handleUpdateBranchName(action.branchName)
            is CodeModificationAction.CommitChanges -> handleCommitChanges()
            is CodeModificationAction.DismissError -> handleDismissError()
        }
    }

    /**
     * Handle InitializeSession action
     */
    private fun handleInitializeSession(action: CodeModificationAction.InitializeSession) {
        _state.update {
            CodeModificationUiState(
                sessionId = action.sessionId,
                repositoryName = action.repositoryName
            )
        }
        logger.info { "Session initialized: ${action.sessionId}, repository: ${action.repositoryName}" }
    }

    /**
     * Handle PreFillFromQA action
     */
    private fun handlePreFillFromQA(action: CodeModificationAction.PreFillFromQA) {
        _state.update {
            it.copy(
                contextFromQA = action.qaContext,
                modificationRequest = it.modificationRequest + "\n\nContext from QA:\n${action.qaContext}"
            )
        }
        logger.info { "Pre-filled request from QA context" }
    }

    /**
     * Handle UpdateRequest action
     */
    private fun handleUpdateRequest(request: String) {
        _state.update { it.copy(modificationRequest = request, error = null) }
    }

    /**
     * Handle UpdateFileScope action
     */
    private fun handleUpdateFileScope(files: List<String>?) {
        _state.update { it.copy(fileScope = files, error = null) }
    }

    /**
     * Handle GeneratePlan action
     */
    private fun handleGeneratePlan() {
        val currentState = _state.value

        // Validate state
        if (!currentState.hasSession) {
            _state.update { it.copy(error = "No active session. Please analyze a repository first.") }
            return
        }

        if (currentState.modificationRequest.isBlank()) {
            _state.update { it.copy(error = "Please enter a modification request.") }
            return
        }

        val sessionId = currentState.sessionId!!
        val request = currentState.modificationRequest.trim()

        _state.update {
            it.copy(
                isLoadingPlan = true,
                error = null
            )
        }

        // Generate modification plan
        viewModelScope.launch(Dispatchers.IO) {
            try {
                logger.info { "Generating modification plan: $request" }

                val result = repository.requestModification(
                    sessionId = sessionId,
                    modificationRequest = request,
                    createBranch = currentState.createBranch,
                    branchName = currentState.branchName,
                    promptExecutor = promptExecutor
                )

                // Update state on Main dispatcher
                launch(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val response = result.getOrThrow()
                        logger.info { "Modification plan generated successfully" }

                        val plan = ModificationPlanUi.fromBackendModel(response)

                        _state.update {
                            it.copy(
                                modificationPlan = plan,
                                selectedChanges = plan.changes.map { change -> change.id }.toSet(),
                                isLoadingPlan = false
                            )
                        }
                    } else {
                        val errorMessage = result.exceptionOrNull()?.message
                            ?: "Failed to generate modification plan"
                        logger.error { "Plan generation failed: $errorMessage" }

                        _state.update {
                            it.copy(
                                isLoadingPlan = false,
                                error = errorMessage
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Plan generation threw exception" }

                launch(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isLoadingPlan = false,
                            error = "Error: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Handle ToggleChangeSelection action
     */
    private fun handleToggleChangeSelection(changeId: String) {
        _state.update { currentState ->
            val newSelection = if (changeId in currentState.selectedChanges) {
                currentState.selectedChanges - changeId
            } else {
                currentState.selectedChanges + changeId
            }
            currentState.copy(selectedChanges = newSelection)
        }
    }

    /**
     * Handle SelectAllChanges action
     */
    private fun handleSelectAllChanges() {
        _state.update { currentState ->
            val allChangeIds = currentState.modificationPlan?.changes?.map { it.id }?.toSet() ?: emptySet()
            currentState.copy(selectedChanges = allChangeIds)
        }
    }

    /**
     * Handle DeselectAllChanges action
     */
    private fun handleDeselectAllChanges() {
        _state.update { it.copy(selectedChanges = emptySet()) }
    }

    /**
     * Handle ViewChangeDetails action
     */
    private fun handleViewChangeDetails(changeId: String?) {
        _state.update { it.copy(selectedChangeForDetails = changeId) }
    }

    /**
     * Handle SwitchDiffViewMode action
     */
    private fun handleSwitchDiffViewMode(mode: DiffViewMode) {
        _state.update { it.copy(diffViewMode = mode) }
    }

    /**
     * Handle ApplySelectedChanges action
     */
    private fun handleApplySelectedChanges() {
        val currentState = _state.value

        if (currentState.modificationPlan == null) {
            _state.update { it.copy(error = "No modification plan available.") }
            return
        }

        if (currentState.selectedChanges.isEmpty()) {
            _state.update { it.copy(error = "Please select at least one change to apply.") }
            return
        }

        _state.update {
            it.copy(
                isApplyingChanges = true,
                error = null
            )
        }

        // Simulate applying changes (in real implementation, this would call repository)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                logger.info { "Applying ${currentState.selectedChanges.size} changes" }

                // TODO: Implement actual application logic when backend is ready
                kotlinx.coroutines.delay(2000) // Simulate work

                launch(Dispatchers.Main) {
                    _state.update { state ->
                        val updatedPlan = state.modificationPlan?.copy(
                            appliedChanges = state.selectedChanges
                        )
                        state.copy(
                            modificationPlan = updatedPlan,
                            isApplyingChanges = false,
                            commitMessage = "Applied ${state.selectedChanges.size} code modifications"
                        )
                    }
                    logger.info { "Changes applied successfully" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Apply changes threw exception" }

                launch(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isApplyingChanges = false,
                            error = "Error applying changes: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Handle UpdateCommitMessage action
     */
    private fun handleUpdateCommitMessage(message: String) {
        _state.update { it.copy(commitMessage = message, error = null) }
    }

    /**
     * Handle ToggleCreateBranch action
     */
    private fun handleToggleCreateBranch() {
        _state.update { it.copy(createBranch = !it.createBranch) }
    }

    /**
     * Handle UpdateBranchName action
     */
    private fun handleUpdateBranchName(branchName: String?) {
        _state.update { it.copy(branchName = branchName) }
    }

    /**
     * Handle CommitChanges action
     */
    private fun handleCommitChanges() {
        val currentState = _state.value

        if (currentState.modificationPlan == null) {
            _state.update { it.copy(error = "No modification plan available.") }
            return
        }

        if (currentState.modificationPlan.appliedChanges.isEmpty()) {
            _state.update { it.copy(error = "Please apply changes before committing.") }
            return
        }

        if (currentState.commitMessage.isBlank()) {
            _state.update { it.copy(error = "Please enter a commit message.") }
            return
        }

        _state.update {
            it.copy(
                isCommitting = true,
                error = null
            )
        }

        // Simulate git commit (in real implementation, this would call repository)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                logger.info { "Committing changes: ${currentState.commitMessage}" }

                // TODO: Implement actual git commit logic when backend is ready
                kotlinx.coroutines.delay(1500) // Simulate work

                launch(Dispatchers.Main) {
                    val commitSha = "abc123" // Mock SHA
                    val branchName = currentState.branchName ?: "code-mod-${System.currentTimeMillis()}"

                    _state.update { state ->
                        val updatedPlan = state.modificationPlan?.copy(
                            commitSha = commitSha,
                            branchName = branchName
                        )
                        state.copy(
                            modificationPlan = updatedPlan,
                            isCommitting = false
                        )
                    }
                    logger.info { "Changes committed successfully: $commitSha" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Commit threw exception" }

                launch(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isCommitting = false,
                            error = "Error committing changes: ${e.message}"
                        )
                    }
                }
            }
        }
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
            requestTimeoutMillis = 180_000, // 3 minutes for code modification
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
        private fun createDefaultRepository(): CodeModificationRepository {
            val koogService = KoogServiceFactory.createFromEnv()
            return CodeModificationRepositoryImpl(koogService)
        }
    }
}
