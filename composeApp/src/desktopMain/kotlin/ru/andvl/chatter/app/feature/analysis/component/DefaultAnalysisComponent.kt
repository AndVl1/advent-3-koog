package ru.andvl.chatter.app.feature.analysis.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.andvl.chatter.app.feature.analysis.repository.AnalysisRepository
import ru.andvl.chatter.app.models.AnalysisConfig
import ru.andvl.chatter.app.models.AnalysisEvent
import ru.andvl.chatter.app.models.AnalysisUiState
import ru.andvl.chatter.app.models.LLMProvider
import ru.andvl.chatter.app.platform.EnvLoader
import ru.andvl.chatter.app.platform.FileSaver
import kotlinx.datetime.Clock

class DefaultAnalysisComponent(
    componentContext: ComponentContext,
    private val analysisRepository: AnalysisRepository
) : AnalysisComponent, ComponentContext by componentContext {

    private val scope = coroutineScope(Dispatchers.Main)

    private val _state = MutableValue(AnalysisUiState(
        apiKey = EnvLoader.loadApiKey("OPENROUTER_API_KEY")
    ))
    override val state: Value<AnalysisUiState> = _state

    private var analysisJob: Job? = null

    init {
        scope.launch {
            initializeOllama()
        }
    }

    private suspend fun initializeOllama() {
        try {
            val ollamaModels = analysisRepository.getAvailableOllamaModels()
            if (ollamaModels.isNotEmpty()) {
                val updatedProviders = buildList {
                    add(LLMProvider.OPEN_ROUTER)
                    add(LLMProvider.GOOGLE)
                    add(LLMProvider.OLLAMA)
                    add(LLMProvider.CUSTOM)
                }
                _state.value = _state.value.copy(
                    availableProviders = updatedProviders,
                    ollamaModels = ollamaModels,
                    isOllamaAvailable = true
                )
            }
        } catch (e: Exception) {
            println("Ollama initialization failed: ${e.message}")
        }
    }

    override fun onEvent(event: AnalysisEvent) {
        when (event) {
            is AnalysisEvent.UpdateUserInput -> _state.value = _state.value.copy(userInput = event.input)
            is AnalysisEvent.UpdateApiKey -> _state.value = _state.value.copy(apiKey = event.apiKey)
            is AnalysisEvent.SelectLLMProvider -> _state.value = _state.value.copy(
                llmProvider = event.provider,
                selectedModel = event.provider.defaultModel
            )
            is AnalysisEvent.SelectModel -> _state.value = _state.value.copy(selectedModel = event.model)
            is AnalysisEvent.UpdateCustomBaseUrl -> _state.value = _state.value.copy(customBaseUrl = event.url)
            is AnalysisEvent.UpdateCustomModel -> _state.value = _state.value.copy(customModel = event.model)
            is AnalysisEvent.UpdateCustomMaxContextTokens -> _state.value = _state.value.copy(customMaxContextTokens = event.tokens)
            is AnalysisEvent.UpdateCustomFixingMaxContextTokens -> _state.value = _state.value.copy(customFixingMaxContextTokens = event.tokens)
            is AnalysisEvent.ToggleUseMainModelForFixing -> _state.value = _state.value.copy(useMainModelForFixing = event.useMain)
            is AnalysisEvent.SelectFixingModel -> _state.value = _state.value.copy(fixingModel = event.model)
            is AnalysisEvent.ToggleAttachGoogleSheets -> _state.value = _state.value.copy(attachGoogleSheets = event.attach)
            is AnalysisEvent.UpdateGoogleSheetsUrl -> _state.value = _state.value.copy(googleSheetsUrl = event.url)
            is AnalysisEvent.ToggleForceSkipDocker -> _state.value = _state.value.copy(forceSkipDocker = event.skip)
            is AnalysisEvent.ToggleEnableEmbeddings -> _state.value = _state.value.copy(enableEmbeddings = event.enable)
            is AnalysisEvent.StartAnalysis -> startAnalysis()
            is AnalysisEvent.CancelAnalysis -> {
                analysisJob?.cancel()
                _state.value = _state.value.copy(isLoading = false, currentEventText = null)
            }
            is AnalysisEvent.ClearError -> _state.value = _state.value.copy(error = null)
            is AnalysisEvent.ClearResult -> _state.value = _state.value.copy(analysisResult = null)
            is AnalysisEvent.SaveReport -> saveReport(event.content)
        }
    }

    private fun startAnalysis() {
        analysisJob?.cancel()
        val currentState = _state.value

        val mainModelId = if (currentState.llmProvider == LLMProvider.CUSTOM) {
            currentState.customModel
        } else {
            currentState.selectedModel
        }

        val fixingModelId = if (currentState.useMainModelForFixing) mainModelId else currentState.fixingModel

        val maxContextTokens = if (currentState.llmProvider == LLMProvider.CUSTOM) {
            currentState.customMaxContextTokens
        } else {
            currentState.llmProvider.getMaxContextTokens(mainModelId)
        }

        val fixingMaxContextTokens = if (currentState.llmProvider == LLMProvider.CUSTOM) {
            if (currentState.useMainModelForFixing) currentState.customMaxContextTokens
            else currentState.customFixingMaxContextTokens
        } else {
            currentState.llmProvider.getMaxContextTokens(fixingModelId)
        }

        val config = AnalysisConfig(
            userInput = currentState.userInput,
            apiKey = currentState.apiKey,
            llmProvider = currentState.llmProvider,
            selectedModel = mainModelId,
            customBaseUrl = if (currentState.llmProvider == LLMProvider.CUSTOM) currentState.customBaseUrl else null,
            customModel = if (currentState.llmProvider == LLMProvider.CUSTOM) currentState.customModel else null,
            maxContextTokens = maxContextTokens,
            fixingMaxContextTokens = fixingMaxContextTokens,
            useMainModelForFixing = currentState.useMainModelForFixing,
            fixingModel = fixingModelId,
            attachGoogleSheets = currentState.attachGoogleSheets,
            googleSheetsUrl = if (currentState.attachGoogleSheets) currentState.googleSheetsUrl else "",
            forceSkipDocker = currentState.forceSkipDocker,
            enableEmbeddings = currentState.enableEmbeddings
        )

        _state.value = _state.value.copy(
            isLoading = true,
            error = null,
            analysisResult = null,
            currentEventText = null,
            analysisProgress = 0,
            currentStep = 0,
            totalSteps = 6,
            currentStepName = "",
            recentEvents = emptyList()
        )

        analysisJob = scope.launch {
            try {
                analysisRepository.analyzeWithEvents(config).collect { eventData ->
                    if (eventData.isComplete) {
                        if (eventData.result != null) {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                analysisResult = eventData.result,
                                currentEventText = null,
                                error = null
                            )
                        } else if (eventData.error != null) {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                error = eventData.error,
                                currentEventText = null
                            )
                        }
                    } else {
                        val updatedEvents = (_state.value.recentEvents + eventData.eventText).takeLast(5)
                        _state.value = _state.value.copy(
                            currentEventText = eventData.eventText,
                            recentEvents = updatedEvents,
                            analysisProgress = eventData.progress,
                            currentStep = eventData.currentStep,
                            totalSteps = eventData.totalSteps,
                            currentStepName = eventData.currentStepName
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    currentEventText = null,
                    error = null  // Not an error - user cancelled
                )
                throw e  // Re-throw to properly propagate cancellation
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error",
                    currentEventText = null
                )
            }
        }
    }

    private fun saveReport(content: String) {
        scope.launch {
            val fileName = "analysis_report_${Clock.System.now().epochSeconds}.md"
            FileSaver.saveReport(content, fileName)
        }
    }
}
