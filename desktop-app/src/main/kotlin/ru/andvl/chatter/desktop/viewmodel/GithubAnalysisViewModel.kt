package ru.andvl.chatter.desktop.viewmodel

import io.github.cdimascio.dotenv.Dotenv
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
import ru.andvl.chatter.shared.models.github.AnalysisEvent
import ru.andvl.chatter.shared.models.github.AnalysisEventOrResult

/**
 * ViewModel for GitHub analysis screen
 * Handles UI state and processes user actions
 */
class GithubAnalysisViewModel(
    private val interactor: GithubAnalysisInteractor = GithubAnalysisInteractor()
) {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AppState(
        apiKey = loadApiKeyFromEnv()
    ))
    val state: StateFlow<AppState> = _state.asStateFlow()

    // Job for the current analysis, can be cancelled
    private var analysisJob: Job? = null

    /**
     * Load API key from .env file if it exists
     * Returns empty string if .env file is missing or API key is not set
     */
    private fun loadApiKeyFromEnv(): String {
        return try {
            val dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load()

            // Try to load OPENROUTER_API_KEY (default provider)
            // User can change it later via UI
            dotenv["OPENROUTER_API_KEY"] ?: ""
        } catch (e: Exception) {
            // If any error occurs, return empty string
            ""
        }
    }

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
            is GithubAnalysisAction.UpdateCustomMaxContextTokens -> handleUpdateCustomMaxContextTokens(action.maxContextTokens)
            is GithubAnalysisAction.UpdateCustomFixingMaxContextTokens -> handleUpdateCustomFixingMaxContextTokens(action.fixingMaxContextTokens)
            is GithubAnalysisAction.ToggleUseMainModelForFixing -> handleToggleUseMainModelForFixing(action.useMain)
            is GithubAnalysisAction.SelectFixingModel -> handleSelectFixingModel(action.model)
            is GithubAnalysisAction.ToggleAttachGoogleSheets -> handleToggleAttachGoogleSheets(action.attach)
            is GithubAnalysisAction.UpdateGoogleSheetsUrl -> handleUpdateGoogleSheetsUrl(action.url)
            is GithubAnalysisAction.ToggleForceSkipDocker -> handleToggleForceSkipDocker(action.forceSkip)
            is GithubAnalysisAction.ToggleEnableEmbeddings -> handleToggleEnableEmbeddings(action.enable)
            is GithubAnalysisAction.StartAnalysis -> handleStartAnalysis()
            is GithubAnalysisAction.CancelAnalysis -> handleCancelAnalysis()
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

    private fun handleUpdateCustomMaxContextTokens(maxContextTokens: Long) {
        _state.update { it.copy(customMaxContextTokens = maxContextTokens) }
    }

    private fun handleUpdateCustomFixingMaxContextTokens(fixingMaxContextTokens: Long) {
        _state.update { it.copy(customFixingMaxContextTokens = fixingMaxContextTokens) }
    }

    private fun handleToggleUseMainModelForFixing(useMain: Boolean) {
        _state.update { it.copy(useMainModelForFixing = useMain) }
    }

    private fun handleSelectFixingModel(model: String) {
        _state.update { it.copy(fixingModel = model) }
    }

    private fun handleToggleAttachGoogleSheets(attach: Boolean) {
        _state.update { it.copy(attachGoogleSheets = attach) }
    }

    private fun handleUpdateGoogleSheetsUrl(url: String) {
        _state.update { it.copy(googleSheetsUrl = url) }
    }

    private fun handleToggleForceSkipDocker(forceSkip: Boolean) {
        _state.update { it.copy(forceSkipDocker = forceSkip) }
    }

    private fun handleToggleEnableEmbeddings(enable: Boolean) {
        _state.update { it.copy(enableEmbeddings = enable) }
    }

    private fun handleStartAnalysis() {
        // Cancel any existing analysis job
        analysisJob?.cancel()

        analysisJob = viewModelScope.launch(Dispatchers.IO) {
            // Update UI state on Main thread
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        analysisResult = null,
                        currentEvent = null,
                        analysisProgress = 0,
                        currentStep = 0,
                        totalSteps = 6,
                        currentStepName = "",
                        recentEvents = emptyList()
                    )
                }
            }

            val currentState = _state.value

            // Determine main model ID
            val mainModelId = if (currentState.llmProvider == LLMProvider.CUSTOM) {
                currentState.customModel
            } else {
                currentState.selectedModel
            }

            // Determine fixing model ID
            val fixingModelId = if (currentState.useMainModelForFixing) {
                mainModelId
            } else {
                currentState.fixingModel
            }

            // Get max context tokens
            val maxContextTokens = if (currentState.llmProvider == LLMProvider.CUSTOM) {
                currentState.customMaxContextTokens
            } else {
                currentState.llmProvider.getMaxContextTokens(mainModelId)
            }

            // Get fixing max context tokens
            val fixingMaxContextTokens = if (currentState.llmProvider == LLMProvider.CUSTOM) {
                if (currentState.useMainModelForFixing) {
                    currentState.customMaxContextTokens
                } else {
                    currentState.customFixingMaxContextTokens
                }
            } else {
                currentState.llmProvider.getMaxContextTokens(fixingModelId)
            }

            val config = AnalysisConfig(
                userInput = currentState.userInput,
                apiKey = currentState.apiKey,
                llmProvider = currentState.llmProvider,
                selectedModel = mainModelId,
                customBaseUrl = if (currentState.llmProvider == LLMProvider.CUSTOM) {
                    currentState.customBaseUrl
                } else null,
                customModel = if (currentState.llmProvider == LLMProvider.CUSTOM) {
                    currentState.customModel
                } else null,
                maxContextTokens = maxContextTokens,
                fixingMaxContextTokens = fixingMaxContextTokens,
                useMainModelForFixing = currentState.useMainModelForFixing,
                fixingModel = fixingModelId,
                attachGoogleSheets = currentState.attachGoogleSheets,
                googleSheetsUrl = if (currentState.attachGoogleSheets) currentState.googleSheetsUrl else "",
                forceSkipDocker = currentState.forceSkipDocker,
                enableEmbeddings = currentState.enableEmbeddings
            )

            try {
                // Use streaming analysis with events
                interactor.analyzeRepositoryWithEvents(config)
                    .collect { eventOrResult ->
                        when (eventOrResult) {
                            is AnalysisEventOrResult.Event -> {
                                handleAnalysisEvent(eventOrResult.event)
                            }
                            is AnalysisEventOrResult.Result -> {
                                _state.update {
                                    it.copy(
                                        isLoading = false,
                                        analysisResult = eventOrResult.response,
                                        currentEvent = null,
                                        error = null
                                    )
                                }
                            }
                            is AnalysisEventOrResult.Error -> {
                                _state.update {
                                    it.copy(
                                        isLoading = false,
                                        analysisResult = null,
                                        error = eventOrResult.message,
                                        currentEvent = null
                                    )
                                }
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // Analysis was cancelled by user
                _state.update {
                    it.copy(
                        isLoading = false,
                        currentEvent = null,
                        error = "Анализ отменен пользователем"
                    )
                }
                throw e  // Re-throw to properly cancel the coroutine
            } catch (e: Exception) {
                // Handle other exceptions
                _state.update {
                    it.copy(
                        isLoading = false,
                        analysisResult = null,
                        error = e.message ?: "Неизвестная ошибка",
                        currentEvent = null
                    )
                }
            }
        }
    }

    private fun handleCancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        _state.update {
            it.copy(
                isLoading = false,
                currentEvent = null
            )
        }
    }

    private fun handleAnalysisEvent(event: AnalysisEvent) {
        _state.update { state ->
            // Обновляем информацию о шагах если получили Progress событие
            val (currentStep, totalSteps, stepName) = if (event is AnalysisEvent.Progress) {
                Triple(event.currentStep, event.totalSteps, event.stepName)
            } else {
                Triple(state.currentStep, state.totalSteps, state.currentStepName)
            }

            state.copy(
                currentEvent = event,
                recentEvents = (state.recentEvents + event).takeLast(5),
                analysisProgress = calculateProgress(event, state.analysisProgress),
                currentStep = currentStep,
                totalSteps = totalSteps,
                currentStepName = stepName
            )
        }
    }

    private fun calculateProgress(event: AnalysisEvent, currentProgress: Int): Int {
        return when (event) {
            // Прогресс теперь определяется только через Progress события
            is AnalysisEvent.Progress -> event.percentage
            is AnalysisEvent.Completed -> 100
            // Все остальные события не меняют прогресс
            else -> currentProgress
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
