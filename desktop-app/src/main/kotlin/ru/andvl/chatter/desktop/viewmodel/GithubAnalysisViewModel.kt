package ru.andvl.chatter.desktop.viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.andvl.chatter.desktop.interactor.GithubAnalysisInteractor
import ru.andvl.chatter.desktop.models.AnalysisConfig
import ru.andvl.chatter.desktop.models.AppState
import ru.andvl.chatter.desktop.models.GithubAnalysisAction
import ru.andvl.chatter.desktop.models.LLMProvider

/**
 * ViewModel for GitHub analysis screen
 * Handles UI state and processes user actions
 */
class GithubAnalysisViewModel(
    private val interactor: GithubAnalysisInteractor = GithubAnalysisInteractor()
) {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /**
     * Single entry point for all user actions
     */
    fun dispatch(action: GithubAnalysisAction) {
        when (action) {
            is GithubAnalysisAction.UpdateGithubUrl -> handleUpdateGithubUrl(action.url)
            is GithubAnalysisAction.UpdateUserRequest -> handleUpdateUserRequest(action.request)
            is GithubAnalysisAction.UpdateApiKey -> handleUpdateApiKey(action.apiKey)
            is GithubAnalysisAction.SelectLLMProvider -> handleSelectLLMProvider(action.provider)
            is GithubAnalysisAction.StartAnalysis -> handleStartAnalysis()
            is GithubAnalysisAction.ClearError -> handleClearError()
            is GithubAnalysisAction.ClearResult -> handleClearResult()
        }
    }

    private fun handleUpdateGithubUrl(url: String) {
        _state.update { it.copy(githubUrl = url) }
    }

    private fun handleUpdateUserRequest(request: String) {
        _state.update { it.copy(userRequest = request) }
    }

    private fun handleUpdateApiKey(apiKey: String) {
        _state.update { it.copy(apiKey = apiKey) }
    }

    private fun handleSelectLLMProvider(provider: LLMProvider) {
        _state.update { it.copy(llmProvider = provider) }
    }

    private fun handleStartAnalysis() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    analysisResult = null
                )
            }

            val config = AnalysisConfig(
                githubUrl = _state.value.githubUrl,
                userRequest = _state.value.userRequest,
                apiKey = _state.value.apiKey,
                llmProvider = _state.value.llmProvider
            )

            val result = interactor.analyzeRepository(config)

            _state.update { currentState ->
                if (result.isSuccess) {
                    currentState.copy(
                        isLoading = false,
                        analysisResult = result.getOrNull(),
                        error = null
                    )
                } else {
                    currentState.copy(
                        isLoading = false,
                        analysisResult = null,
                        error = result.exceptionOrNull()?.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    private fun handleClearError() {
        _state.update { it.copy(error = null) }
    }

    private fun handleClearResult() {
        _state.update { it.copy(analysisResult = null) }
    }

    /**
     * Clean up resources when ViewModel is no longer needed
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}
