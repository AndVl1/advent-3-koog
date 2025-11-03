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
            is GithubAnalysisAction.UpdateUserInput -> handleUpdateUserInput(action.input)
            is GithubAnalysisAction.UpdateApiKey -> handleUpdateApiKey(action.apiKey)
            is GithubAnalysisAction.SelectLLMProvider -> handleSelectLLMProvider(action.provider)
            is GithubAnalysisAction.SelectModel -> handleSelectModel(action.model)
            is GithubAnalysisAction.UpdateCustomBaseUrl -> handleUpdateCustomBaseUrl(action.url)
            is GithubAnalysisAction.UpdateCustomModel -> handleUpdateCustomModel(action.model)
            is GithubAnalysisAction.ToggleUseMainModelForFixing -> handleToggleUseMainModelForFixing(action.useMain)
            is GithubAnalysisAction.SelectFixingModel -> handleSelectFixingModel(action.model)
            is GithubAnalysisAction.StartAnalysis -> handleStartAnalysis()
            is GithubAnalysisAction.ClearError -> handleClearError()
            is GithubAnalysisAction.ClearResult -> handleClearResult()
        }
    }

    private fun handleUpdateUserInput(input: String) {
        _state.update { it.copy(userInput = input) }
    }

    private fun handleUpdateApiKey(apiKey: String) {
        _state.update { it.copy(apiKey = apiKey) }
    }

    private fun handleSelectLLMProvider(provider: LLMProvider) {
        _state.update {
            it.copy(
                llmProvider = provider,
                selectedModel = provider.defaultModel
            )
        }
    }

    private fun handleSelectModel(model: String) {
        _state.update { it.copy(selectedModel = model) }
    }

    private fun handleUpdateCustomBaseUrl(url: String) {
        _state.update { it.copy(customBaseUrl = url) }
    }

    private fun handleUpdateCustomModel(model: String) {
        _state.update { it.copy(customModel = model) }
    }

    private fun handleToggleUseMainModelForFixing(useMain: Boolean) {
        _state.update { it.copy(useMainModelForFixing = useMain) }
    }

    private fun handleSelectFixingModel(model: String) {
        _state.update { it.copy(fixingModel = model) }
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

            val currentState = _state.value
            val config = AnalysisConfig(
                userInput = currentState.userInput,
                apiKey = currentState.apiKey,
                llmProvider = currentState.llmProvider,
                selectedModel = if (currentState.llmProvider == LLMProvider.CUSTOM) {
                    currentState.customModel
                } else {
                    currentState.selectedModel
                },
                customBaseUrl = if (currentState.llmProvider == LLMProvider.CUSTOM) {
                    currentState.customBaseUrl
                } else null,
                customModel = if (currentState.llmProvider == LLMProvider.CUSTOM) {
                    currentState.customModel
                } else null,
                useMainModelForFixing = currentState.useMainModelForFixing,
                fixingModel = if (currentState.useMainModelForFixing) {
                    if (currentState.llmProvider == LLMProvider.CUSTOM) {
                        currentState.customModel
                    } else {
                        currentState.selectedModel
                    }
                } else {
                    currentState.fixingModel
                }
            )

            val result = interactor.analyzeRepository(config)

            _state.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        isLoading = false,
                        analysisResult = result.getOrNull(),
                        error = null
                    )
                } else {
                    state.copy(
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
