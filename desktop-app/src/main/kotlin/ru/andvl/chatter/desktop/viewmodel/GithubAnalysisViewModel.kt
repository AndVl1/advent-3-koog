package ru.andvl.chatter.desktop.viewmodel

import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import ru.andvl.chatter.core.model.conversation.PersonalizationConfig
import ru.andvl.chatter.desktop.audio.AudioRecorder
import ru.andvl.chatter.desktop.interactor.GithubAnalysisInteractor
import ru.andvl.chatter.desktop.models.*
import ru.andvl.chatter.desktop.services.OllamaService
import ru.andvl.chatter.koog.mapping.toKoogMessage
import ru.andvl.chatter.koog.model.conversation.ConversationRequest
import ru.andvl.chatter.shared.models.github.AnalysisEvent
import ru.andvl.chatter.shared.models.github.AnalysisEventOrResult
import java.io.File

/**
 * ViewModel for GitHub analysis screen
 * Handles UI state and processes user actions
 */
class GithubAnalysisViewModel(
    private val interactor: GithubAnalysisInteractor = GithubAnalysisInteractor()
) {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AppState(
        apiKey = loadApiKeyFromEnv(),
        chatState = ChatState(
            apiKey = loadApiKeyFromEnv(), // Load the same API key for chat
            provider = ru.andvl.chatter.desktop.models.ChatProvider.OPENROUTER
        )
    ))
    val state: StateFlow<AppState> = _state.asStateFlow()

    // Job for the current analysis, can be cancelled
    private var analysisJob: Job? = null

    // Audio recorder for voice messages
    private val audioRecorder = AudioRecorder()
    private var currentRecordingFile: File? = null
    private var recordingDurationJob: Job? = null
    private var audioLevelJob: Job? = null

    // Audio player for voice playback
    private val audioPlayer = ru.andvl.chatter.desktop.audio.AudioPlayer()
    private var playbackPositionJob: Job? = null

    init {
        // Initialize Ollama on startup (async)
        viewModelScope.launch {
            initializeOllama()
        }
    }

    /**
     * Initialize Ollama: check availability and load models
     */
    private suspend fun initializeOllama() {
        try {
            val ollamaModels = OllamaService.getAvailableModels()

            if (ollamaModels.isNotEmpty()) {
                // Ollama is available - add to providers and update state
                _state.update { currentState ->
                    val updatedProviders = buildList {
                        add(LLMProvider.OPEN_ROUTER)
                        add(LLMProvider.GOOGLE)
                        add(LLMProvider.OLLAMA)
                        add(LLMProvider.CUSTOM)
                    }

                    currentState.copy(
                        availableProviders = updatedProviders,
                        ollamaModels = ollamaModels,
                        isOllamaAvailable = true
                    )
                }
            }
        } catch (e: Exception) {
            // Silently fail - Ollama is optional
            println("Ollama initialization failed: ${e.message}")
        }
    }

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
            // Navigation
            is GithubAnalysisAction.NavigateToScreen -> handleNavigateToScreen(action.screen)

            // Chat actions
            is GithubAnalysisAction.UpdateChatInput -> handleUpdateChatInput(action.input)
            is GithubAnalysisAction.SendChatMessage -> handleSendChatMessage(action.text)
            is GithubAnalysisAction.StartVoiceRecording -> handleStartVoiceRecording()
            is GithubAnalysisAction.StopVoiceRecording -> handleStopVoiceRecording()
            is GithubAnalysisAction.SendVoiceMessage -> handleSendVoiceMessage()
            is GithubAnalysisAction.PlayVoiceMessage -> handlePlayVoiceMessage(action.messageId)
            is GithubAnalysisAction.PauseVoiceMessage -> handlePauseVoiceMessage()
            is GithubAnalysisAction.StopVoicePlayback -> handleStopVoicePlayback()
            is GithubAnalysisAction.ClearChat -> handleClearChat()
            is GithubAnalysisAction.ClearChatError -> handleClearChatError()

            // Chat configuration actions
            is GithubAnalysisAction.SelectChatProvider -> handleSelectChatProvider(action.provider)
            is GithubAnalysisAction.UpdateChatModel -> handleUpdateChatModel(action.model)
            is GithubAnalysisAction.UpdateChatApiKey -> handleUpdateChatApiKey(action.apiKey)
            is GithubAnalysisAction.ToggleChatHistorySaving -> handleToggleChatHistorySaving(action.enabled)
            is GithubAnalysisAction.UpdatePersonalization -> handleUpdatePersonalization(action.config)

            // GitHub Analysis actions
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

    // Navigation handlers
    private fun handleNavigateToScreen(screen: AppScreen) {
        _state.update { it.copy(currentScreen = screen) }
    }

    // Chat handlers (заглушки для будущей интеграции с Koog)
    private fun handleUpdateChatInput(input: String) {
        _state.update {
            it.copy(
                chatState = it.chatState.copy(currentInput = input)
            )
        }
    }

    private fun handleSendChatMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val currentChatState = _state.value.chatState

            // Валидация API ключа
            if (currentChatState.apiKey.isBlank()) {
                _state.update {
                    it.copy(
                        chatState = it.chatState.copy(
                            error = "API Key is required. Please configure it in Chat Settings."
                        )
                    )
                }
                return@launch
            }

            // Добавляем сообщение пользователя
            val userMessage = ChatMessage(
                content = text,
                role = MessageRole.USER
            )

            _state.update {
                it.copy(
                    chatState = it.chatState.copy(
                        messages = it.chatState.messages + userMessage,
                        currentInput = "",
                        isLoading = true,
                        error = null
                    )
                )
            }

            try {
                // Вызов Koog service
                val koogService = ru.andvl.chatter.koog.service.KoogServiceFactory.createFromEnv()

                // Создание PromptExecutor (требуется для KoogService)
                val timeoutConfig = ai.koog.prompt.executor.clients.ConnectionTimeoutConfig(
                    requestTimeoutMillis = 120_000,
                    connectTimeoutMillis = 10_000
                )

                val llmClient = when (currentChatState.provider) {
                    ru.andvl.chatter.desktop.models.ChatProvider.GOOGLE -> {
                        ai.koog.prompt.executor.clients.google.GoogleLLMClient(currentChatState.apiKey)
                    }
                    ru.andvl.chatter.desktop.models.ChatProvider.OPENROUTER -> {
                        ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient(
                            apiKey = currentChatState.apiKey,
                            settings = ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings(
                                timeoutConfig = timeoutConfig
                            )
                        )
                    }
                }

                val promptExecutor = ai.koog.prompt.executor.llms.SingleLLMPromptExecutor(llmClient)

                // Преобразование истории в формат Message
                val history: List<ai.koog.prompt.message.Message> = currentChatState.messages.takeLast(10).map { msg ->
                    // Convert desktop-app MessageRole to shared-models MessageRole
                    val sharedRole = when (msg.role) {
                        MessageRole.USER -> ru.andvl.chatter.shared.models.MessageRole.User
                        MessageRole.ASSISTANT -> ru.andvl.chatter.shared.models.MessageRole.Assistant
                        MessageRole.SYSTEM -> ru.andvl.chatter.shared.models.MessageRole.System
                    }
                    // Convert to SharedMessage and then to Koog Message
                    val sharedMessage = ru.andvl.chatter.shared.models.SharedMessage(
                        role = sharedRole,
                        content = msg.content,
                        meta = ru.andvl.chatter.shared.models.MessageMeta(),
                        timestamp = msg.timestamp
                    )
                    sharedMessage.toKoogMessage()
                }

                // Создание ConversationRequest
                val conversationRequest = ConversationRequest(
                    message = text,
                    history = history,
                    maxHistoryLength = 10,
                    personalization = currentChatState.personalizationConfig
                )

                // Определение провайдера
                val provider = when (currentChatState.provider) {
                    ru.andvl.chatter.desktop.models.ChatProvider.GOOGLE ->
                        ru.andvl.chatter.koog.service.Provider.GOOGLE
                    ru.andvl.chatter.desktop.models.ChatProvider.OPENROUTER ->
                        ru.andvl.chatter.koog.service.Provider.OPENROUTER
                }

                // Отправка запроса через новый метод conversation
                val response = koogService.conversation(conversationRequest, promptExecutor, provider)

                // Добавление ответа ассистента
                val assistantMessage = ChatMessage(
                    content = response.text,
                    role = MessageRole.ASSISTANT
                )

                _state.update {
                    it.copy(
                        chatState = it.chatState.copy(
                            messages = it.chatState.messages + assistantMessage,
                            isLoading = false
                        )
                    )
                }

                // Сохранение истории если включено
                if (currentChatState.saveHistoryEnabled) {
                    saveChatHistory(currentChatState.messages + assistantMessage, currentChatState.historyFilePath)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        chatState = it.chatState.copy(
                            error = "Error: ${e.message}",
                            isLoading = false
                        )
                    )
                }
            }
        }
    }

    private fun handleStartVoiceRecording() {
        viewModelScope.launch {
            try {
                // Create directory for voice recordings
                val voiceDir = File("./voice_recordings")
                if (!voiceDir.exists()) {
                    voiceDir.mkdirs()
                }

                // Create file for this recording
                val timestamp = Clock.System.now().toEpochMilliseconds()
                val file = File(voiceDir, "recording_$timestamp.wav")
                currentRecordingFile = file

                // Start recording
                val result = audioRecorder.startRecording(file)

                if (result.isSuccess) {
                    _state.update {
                        it.copy(
                            chatState = it.chatState.copy(
                                isRecording = true,
                                recordingDuration = 0,
                                error = null
                            )
                        )
                    }

                    // Start job to update recording duration
                    recordingDurationJob = viewModelScope.launch {
                        audioRecorder.recordingDuration.collect { duration ->
                            _state.update {
                                it.copy(
                                    chatState = it.chatState.copy(
                                        recordingDuration = duration
                                    )
                                )
                            }
                        }
                    }

                    // Start job to update audio level
                    audioLevelJob = viewModelScope.launch {
                        audioRecorder.audioLevel.collect { level ->
                            _state.update {
                                it.copy(
                                    chatState = it.chatState.copy(
                                        audioLevel = level
                                    )
                                )
                            }
                        }
                    }
                } else {
                    _state.update {
                        it.copy(
                            chatState = it.chatState.copy(
                                error = "Failed to start recording: ${result.exceptionOrNull()?.message}"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        chatState = it.chatState.copy(
                            error = "Error starting recording: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    private fun handleStopVoiceRecording() {
        viewModelScope.launch {
            try {
                // Stop duration and level tracking
                recordingDurationJob?.cancel()
                recordingDurationJob = null
                audioLevelJob?.cancel()
                audioLevelJob = null

                // Stop recording
                val result = audioRecorder.stopRecording()

                if (result.isSuccess) {
                    // Add voice message to chat
                    val file = currentRecordingFile
                    if (file != null && file.exists()) {
                        val voiceMessage = ChatMessage(
                            content = "[Voice message ${_state.value.chatState.recordingDuration / 1000}s]",
                            role = MessageRole.USER,
                            isVoice = true,
                            voiceFilePath = file.absolutePath
                        )

                        _state.update {
                            it.copy(
                                chatState = it.chatState.copy(
                                    messages = it.chatState.messages + voiceMessage,
                                    isRecording = false,
                                    recordingDuration = 0,
                                    error = null
                                )
                            )
                        }

                        // Automatically send voice message for transcription
                        handleSendVoiceMessage()
                    }
                } else {
                    _state.update {
                        it.copy(
                            chatState = it.chatState.copy(
                                isRecording = false,
                                error = "Failed to stop recording: ${result.exceptionOrNull()?.message}"
                            )
                        )
                    }
                }

                currentRecordingFile = null
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        chatState = it.chatState.copy(
                            isRecording = false,
                            error = "Error stopping recording: ${e.message}"
                        )
                    )
                }
                currentRecordingFile = null
            }
        }
    }

    private fun handleSendVoiceMessage() {
        viewModelScope.launch {
            val currentChatState = _state.value.chatState

            // Валидация API ключа
            if (currentChatState.apiKey.isBlank()) {
                _state.update {
                    it.copy(
                        chatState = it.chatState.copy(
                            error = "API Key is required. Please configure it in Chat Settings."
                        )
                    )
                }
                return@launch
            }

            // Получаем последнее голосовое сообщение пользователя
            val lastVoiceMessage = currentChatState.messages.lastOrNull { it.isVoice && it.role == MessageRole.USER }
            if (lastVoiceMessage == null || lastVoiceMessage.voiceFilePath == null) {
                _state.update {
                    it.copy(
                        chatState = it.chatState.copy(
                            error = "Voice message not found"
                        )
                    )
                }
                return@launch
            }

            // Устанавливаем состояние загрузки
            _state.update {
                it.copy(
                    chatState = it.chatState.copy(
                        isLoading = true,
                        error = null
                    )
                )
            }

            try {
                // Вызов Koog service
                val koogService = ru.andvl.chatter.koog.service.KoogServiceFactory.createFromEnv()

                // Создание PromptExecutor (требуется для KoogService)
                val timeoutConfig = ai.koog.prompt.executor.clients.ConnectionTimeoutConfig(
                    requestTimeoutMillis = 120_000,
                    connectTimeoutMillis = 10_000
                )

                val llmClient = when (currentChatState.provider) {
                    ru.andvl.chatter.desktop.models.ChatProvider.GOOGLE -> {
                        ai.koog.prompt.executor.clients.google.GoogleLLMClient(currentChatState.apiKey)
                    }
                    ru.andvl.chatter.desktop.models.ChatProvider.OPENROUTER -> {
                        ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient(
                            apiKey = currentChatState.apiKey,
                            settings = ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings(
                                timeoutConfig = timeoutConfig
                            )
                        )
                    }
                }

                val promptExecutor = ai.koog.prompt.executor.llms.SingleLLMPromptExecutor(llmClient)

                // Преобразование истории в формат Message (исключая последнее голосовое сообщение)
                val history: List<ai.koog.prompt.message.Message> = currentChatState.messages
                    .filter { it != lastVoiceMessage }
                    .takeLast(10)
                    .map { msg ->
                        val sharedRole = when (msg.role) {
                            MessageRole.USER -> ru.andvl.chatter.shared.models.MessageRole.User
                            MessageRole.ASSISTANT -> ru.andvl.chatter.shared.models.MessageRole.Assistant
                            MessageRole.SYSTEM -> ru.andvl.chatter.shared.models.MessageRole.System
                        }
                        val sharedMessage = ru.andvl.chatter.shared.models.SharedMessage(
                            role = sharedRole,
                            content = msg.content,
                            meta = ru.andvl.chatter.shared.models.MessageMeta(),
                            timestamp = msg.timestamp
                        )
                        sharedMessage.toKoogMessage()
                    }

                // Создание ConversationRequest с audioFilePath
                val conversationRequest = ConversationRequest(
                    message = lastVoiceMessage.content, // Placeholder message
                    history = history,
                    maxHistoryLength = 10,
                    audioFilePath = lastVoiceMessage.voiceFilePath,
                    personalization = currentChatState.personalizationConfig
                )

                // Определение провайдера
                val provider = when (currentChatState.provider) {
                    ru.andvl.chatter.desktop.models.ChatProvider.GOOGLE ->
                        ru.andvl.chatter.koog.service.Provider.GOOGLE
                    ru.andvl.chatter.desktop.models.ChatProvider.OPENROUTER ->
                        ru.andvl.chatter.koog.service.Provider.OPENROUTER
                }

                // Отправка запроса через conversation с аудио
                val response = koogService.conversation(conversationRequest, promptExecutor, provider)

                // Добавление ответа ассистента
                val assistantMessage = ChatMessage(
                    content = response.text,
                    role = MessageRole.ASSISTANT
                )

                _state.update {
                    it.copy(
                        chatState = it.chatState.copy(
                            messages = it.chatState.messages + assistantMessage,
                            isLoading = false
                        )
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        chatState = it.chatState.copy(
                            error = "Failed to process voice message: ${e.message}",
                            isLoading = false
                        )
                    )
                }
            }
        }
    }

    private fun handlePlayVoiceMessage(messageId: String) {
        viewModelScope.launch {
            try {
                val message = _state.value.chatState.messages.find { it.id == messageId }
                if (message == null || !message.isVoice || message.voiceFilePath == null) {
                    return@launch
                }

                // If already playing this message, resume it
                if (_state.value.chatState.playingMessageId == messageId) {
                    audioPlayer.resume()
                    return@launch
                }

                // Stop any current playback
                audioPlayer.stop()
                playbackPositionJob?.cancel()

                // Start playback
                val file = File(message.voiceFilePath)
                val result = audioPlayer.play(file)

                if (result.isSuccess) {
                    _state.update {
                        it.copy(
                            chatState = it.chatState.copy(
                                playingMessageId = messageId,
                                playbackPosition = 0,
                                playbackDuration = 0
                            )
                        )
                    }

                    // Track playback position and duration
                    playbackPositionJob = viewModelScope.launch {
                        launch {
                            audioPlayer.currentPosition.collect { position ->
                                _state.update {
                                    it.copy(
                                        chatState = it.chatState.copy(
                                            playbackPosition = position
                                        )
                                    )
                                }
                            }
                        }

                        launch {
                            audioPlayer.duration.collect { duration ->
                                _state.update {
                                    it.copy(
                                        chatState = it.chatState.copy(
                                            playbackDuration = duration
                                        )
                                    )
                                }
                            }
                        }

                        // Monitor playing state
                        launch {
                            audioPlayer.isPlaying.collect { playing ->
                                if (!playing && _state.value.chatState.playingMessageId != null) {
                                    // Playback finished
                                    _state.update {
                                        it.copy(
                                            chatState = it.chatState.copy(
                                                playingMessageId = null,
                                                playbackPosition = 0,
                                                playbackDuration = 0
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    _state.update {
                        it.copy(
                            chatState = it.chatState.copy(
                                error = "Failed to play voice message: ${result.exceptionOrNull()?.message}"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        chatState = it.chatState.copy(
                            error = "Error playing voice message: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    private fun handlePauseVoiceMessage() {
        audioPlayer.pause()
    }

    private fun handleStopVoicePlayback() {
        playbackPositionJob?.cancel()
        playbackPositionJob = null
        audioPlayer.stop()

        _state.update {
            it.copy(
                chatState = it.chatState.copy(
                    playingMessageId = null,
                    playbackPosition = 0,
                    playbackDuration = 0
                )
            )
        }
    }

    private fun handleClearChat() {
        _state.update {
            it.copy(chatState = ChatState())
        }
    }

    private fun handleClearChatError() {
        _state.update {
            it.copy(
                chatState = it.chatState.copy(error = null)
            )
        }
    }

    // Chat configuration handlers
    private fun handleSelectChatProvider(provider: ru.andvl.chatter.desktop.models.ChatProvider) {
        _state.update {
            it.copy(
                chatState = it.chatState.copy(
                    provider = provider,
                    model = provider.defaultModel
                )
            )
        }
    }

    private fun handleUpdateChatModel(model: String) {
        _state.update {
            it.copy(
                chatState = it.chatState.copy(model = model)
            )
        }
    }

    private fun handleUpdateChatApiKey(apiKey: String) {
        _state.update {
            it.copy(
                chatState = it.chatState.copy(apiKey = apiKey)
            )
        }
    }

    private fun handleToggleChatHistorySaving(enabled: Boolean) {
        _state.update {
            it.copy(
                chatState = it.chatState.copy(saveHistoryEnabled = enabled)
            )
        }
    }

    private fun handleUpdatePersonalization(config: PersonalizationConfig) {
        _state.update {
            it.copy(
                chatState = it.chatState.copy(personalizationConfig = config)
            )
        }
    }

    /**
     * Save chat history to file
     */
    private fun saveChatHistory(messages: List<ChatMessage>, filePath: String) {
        try {
            val file = java.io.File(filePath)
            val json = kotlinx.serialization.json.Json { prettyPrint = true }

            // Convert messages to serializable format
            val historyData = messages.map { msg ->
                mapOf(
                    "role" to msg.role.name,
                    "content" to msg.content,
                    "timestamp" to msg.timestamp.toString()
                )
            }

            file.writeText(json.encodeToString(
                kotlinx.serialization.serializer(),
                historyData
            ))
        } catch (e: Exception) {
            // Log error but don't fail the chat
            println("Failed to save chat history: ${e.message}")
        }
    }

    /**
     * Clean up resources when ViewModel is no longer needed
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}
