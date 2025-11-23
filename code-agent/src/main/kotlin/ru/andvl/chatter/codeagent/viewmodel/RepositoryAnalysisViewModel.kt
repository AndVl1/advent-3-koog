package ru.andvl.chatter.codeagent.viewmodel

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
import ru.andvl.chatter.codeagent.interactor.RepositoryAnalysisInteractor
import ru.andvl.chatter.shared.models.codeagent.AnalysisType

/**
 * ViewModel for Repository Analysis screen
 *
 * This ViewModel manages UI state and handles user actions.
 * It follows Clean Architecture and MVVM principles:
 * - UI layer observes state via StateFlow
 * - UI sends actions via dispatch()
 * - ViewModel delegates business logic to Interactor
 * - Uses Dispatchers.Main for UI updates (kotlinx-coroutines-swing)
 *
 * @property interactor Business logic layer
 */
class RepositoryAnalysisViewModel(
    private val interactor: RepositoryAnalysisInteractor = RepositoryAnalysisInteractor()
) {
    private val logger = KotlinLogging.logger {}

    // ViewModel scope with SupervisorJob for proper coroutine management
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Mutable state for internal updates
    private val _state = MutableStateFlow(RepositoryAnalysisUiState())

    // Public immutable state for UI observation
    val state: StateFlow<RepositoryAnalysisUiState> = _state.asStateFlow()

    /**
     * Dispatch user action
     *
     * This is the single entry point for all user actions.
     * It implements the Unidirectional Data Flow pattern.
     *
     * @param action User action to process
     */
    fun dispatch(action: RepositoryAnalysisAction) {
        logger.debug { "Dispatching action: ${action::class.simpleName}" }

        when (action) {
            is RepositoryAnalysisAction.UpdateGitHubUrl -> handleUpdateGitHubUrl(action.url)
            is RepositoryAnalysisAction.StartAnalysis -> handleStartAnalysis()
            is RepositoryAnalysisAction.ClearResult -> handleClearResult()
            is RepositoryAnalysisAction.ToggleTreeNode -> handleToggleTreeNode(action.nodePath)
        }
    }

    /**
     * Handle UpdateGitHubUrl action
     */
    private fun handleUpdateGitHubUrl(url: String) {
        _state.update { it.copy(githubUrl = url, error = null) }
    }

    /**
     * Handle StartAnalysis action
     */
    private fun handleStartAnalysis() {
        val currentUrl = _state.value.githubUrl

        // Validate URL before starting analysis
        val validationResult = interactor.validateGitHubUrl(currentUrl)
        if (validationResult.isFailure) {
            _state.update {
                it.copy(
                    error = validationResult.exceptionOrNull()?.message
                        ?: "Invalid GitHub URL"
                )
            }
            return
        }

        // Start analysis in IO dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            // Update state to show loading
            _state.update {
                it.copy(
                    isAnalyzing = true,
                    progressMessage = "Analyzing repository...",
                    error = null,
                    analysisResult = null
                )
            }

            try {
                // Perform analysis (this is LLM-free, so it should be fast)
                val result = interactor.analyzeRepository(
                    githubUrl = currentUrl,
                    analysisType = AnalysisType.STRUCTURE,
                    enableEmbeddings = false
                )

                // Update state on Main dispatcher
                launch(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val analysisResult = result.getOrThrow()
                        logger.info { "Analysis completed successfully: ${analysisResult.repositoryName}" }

                        // Don't auto-expand anything - let user expand folders manually
                        val initialExpansionState = emptyMap<String, Boolean>()

                        _state.update {
                            it.copy(
                                isAnalyzing = false,
                                progressMessage = "",
                                analysisResult = analysisResult,
                                error = null,
                                fileTreeExpansionState = initialExpansionState
                            )
                        }
                    } else {
                        val errorMessage = result.exceptionOrNull()?.message
                            ?: "Unknown error occurred"
                        logger.error { "Analysis failed: $errorMessage" }

                        _state.update {
                            it.copy(
                                isAnalyzing = false,
                                progressMessage = "",
                                error = errorMessage
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Analysis threw exception" }

                launch(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            progressMessage = "",
                            error = "Analysis failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Handle ClearResult action
     */
    private fun handleClearResult() {
        _state.update {
            RepositoryAnalysisUiState(
                githubUrl = it.githubUrl // Preserve the URL
            )
        }
    }

    /**
     * Handle ToggleTreeNode action
     */
    private fun handleToggleTreeNode(nodePath: String) {
        _state.update { currentState ->
            val currentExpansionState = currentState.fileTreeExpansionState
            val isCurrentlyExpanded = currentExpansionState[nodePath] ?: false

            currentState.copy(
                fileTreeExpansionState = currentExpansionState + (nodePath to !isCurrentlyExpanded)
            )
        }
    }

    /**
     * Cleanup method - should be called when ViewModel is no longer needed
     */
    fun onCleared() {
        logger.info { "ViewModel cleared, cancelling coroutines" }
        viewModelScope.cancel()
    }
}
