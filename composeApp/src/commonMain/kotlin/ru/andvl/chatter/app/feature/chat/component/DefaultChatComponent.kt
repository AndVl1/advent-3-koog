package ru.andvl.chatter.app.feature.chat.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.andvl.chatter.app.feature.chat.repository.ChatRepository
import ru.andvl.chatter.app.models.ChatEvent
import ru.andvl.chatter.app.models.ChatMessage
import ru.andvl.chatter.app.models.ChatProvider
import ru.andvl.chatter.app.models.ChatUiState
import ru.andvl.chatter.app.models.MessageRole
import ru.andvl.chatter.app.platform.AudioPlayer
import ru.andvl.chatter.app.platform.AudioRecorder
import ru.andvl.chatter.app.platform.EnvLoader
import ru.andvl.chatter.app.platform.FileSaver
import kotlinx.datetime.Clock

class DefaultChatComponent(
    componentContext: ComponentContext,
    private val chatRepository: ChatRepository
) : ChatComponent, ComponentContext by componentContext {

    private val scope = coroutineScope(Dispatchers.Main)

    private val _state = MutableValue(ChatUiState(
        apiKey = EnvLoader.loadApiKey("OPENROUTER_API_KEY"),
        historyFilePath = FileSaver.getDefaultChatHistoryPath()
    ))
    override val state: Value<ChatUiState> = _state

    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer()
    private var currentRecordingPath: String? = null
    private var recordingDurationJob: Job? = null
    private var audioLevelJob: Job? = null
    private var playbackPositionJob: Job? = null

    override fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.UpdateInput -> _state.value = _state.value.copy(currentInput = event.input)
            is ChatEvent.SendMessage -> handleSendMessage(event.text)
            is ChatEvent.StartVoiceRecording -> handleStartVoiceRecording()
            is ChatEvent.StopVoiceRecording -> handleStopVoiceRecording()
            is ChatEvent.SendVoiceMessage -> handleSendVoiceMessage()
            is ChatEvent.PlayVoiceMessage -> handlePlayVoiceMessage(event.messageId)
            is ChatEvent.PauseVoiceMessage -> audioPlayer.pause()
            is ChatEvent.StopVoicePlayback -> handleStopVoicePlayback()
            is ChatEvent.ClearChat -> _state.value = ChatUiState(
                apiKey = _state.value.apiKey,
                provider = _state.value.provider,
                model = _state.value.model,
                personalizationConfig = _state.value.personalizationConfig,
                historyFilePath = _state.value.historyFilePath
            )
            is ChatEvent.ClearError -> _state.value = _state.value.copy(error = null)
            is ChatEvent.SelectProvider -> _state.value = _state.value.copy(
                provider = event.provider,
                model = event.provider.defaultModel
            )
            is ChatEvent.UpdateModel -> _state.value = _state.value.copy(model = event.model)
            is ChatEvent.UpdateApiKey -> _state.value = _state.value.copy(apiKey = event.apiKey)
            is ChatEvent.ToggleHistorySaving -> _state.value = _state.value.copy(saveHistoryEnabled = event.enabled)
            is ChatEvent.UpdatePersonalization -> _state.value = _state.value.copy(personalizationConfig = event.config)
        }
    }

    private fun handleSendMessage(text: String) {
        if (text.isBlank()) return

        val currentState = _state.value
        if (currentState.apiKey.isBlank()) {
            _state.value = _state.value.copy(error = "API Key is required. Please configure it in Chat Settings.")
            return
        }

        val userMessage = ChatMessage(content = text, role = MessageRole.USER)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMessage,
            currentInput = "",
            isLoading = true,
            error = null
        )

        scope.launch {
            val result = chatRepository.sendMessage(
                text = text,
                history = currentState.messages,
                provider = currentState.provider,
                model = currentState.model,
                apiKey = currentState.apiKey,
                personalization = currentState.personalizationConfig
            )

            result.onSuccess { responseText ->
                val assistantMessage = ChatMessage(content = responseText, role = MessageRole.ASSISTANT)
                val updatedMessages = _state.value.messages + assistantMessage
                _state.value = _state.value.copy(messages = updatedMessages, isLoading = false)

                if (currentState.saveHistoryEnabled) {
                    saveChatHistory(updatedMessages)
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(error = "Error: ${e.message}", isLoading = false)
            }
        }
    }

    private fun handleStartVoiceRecording() {
        scope.launch {
            try {
                val timestamp = Clock.System.now().toEpochMilliseconds()
                val path = FileSaver.createVoiceRecordingPath(timestamp)
                currentRecordingPath = path

                val result = audioRecorder.startRecording(path)
                if (result.isSuccess) {
                    _state.value = _state.value.copy(isRecording = true, recordingDuration = 0, error = null)

                    recordingDurationJob = scope.launch {
                        audioRecorder.recordingDuration.collect { duration ->
                            _state.value = _state.value.copy(recordingDuration = duration)
                        }
                    }
                    audioLevelJob = scope.launch {
                        audioRecorder.audioLevel.collect { level ->
                            _state.value = _state.value.copy(audioLevel = level)
                        }
                    }
                } else {
                    _state.value = _state.value.copy(
                        error = "Failed to start recording: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Error starting recording: ${e.message}")
            }
        }
    }

    private fun handleStopVoiceRecording() {
        scope.launch {
            try {
                recordingDurationJob?.cancel()
                recordingDurationJob = null
                audioLevelJob?.cancel()
                audioLevelJob = null

                val result = audioRecorder.stopRecording()
                if (result.isSuccess) {
                    val path = currentRecordingPath
                    if (path != null) {
                        val voiceMessage = ChatMessage(
                            content = "[Voice message ${_state.value.recordingDuration / 1000}s]",
                            role = MessageRole.USER,
                            isVoice = true,
                            voiceFilePath = path
                        )
                        _state.value = _state.value.copy(
                            messages = _state.value.messages + voiceMessage,
                            isRecording = false,
                            recordingDuration = 0,
                            error = null
                        )
                        handleSendVoiceMessage()
                    }
                } else {
                    _state.value = _state.value.copy(
                        isRecording = false,
                        error = "Failed to stop recording: ${result.exceptionOrNull()?.message}"
                    )
                }
                currentRecordingPath = null
            } catch (e: Exception) {
                _state.value = _state.value.copy(isRecording = false, error = "Error stopping recording: ${e.message}")
                currentRecordingPath = null
            }
        }
    }

    private fun handleSendVoiceMessage() {
        val currentState = _state.value
        if (currentState.apiKey.isBlank()) {
            _state.value = _state.value.copy(error = "API Key is required.")
            return
        }

        val lastVoiceMessage = currentState.messages.lastOrNull { it.isVoice && it.role == MessageRole.USER }
        if (lastVoiceMessage?.voiceFilePath == null) {
            _state.value = _state.value.copy(error = "Voice message not found")
            return
        }

        _state.value = _state.value.copy(isLoading = true, error = null)

        scope.launch {
            val historyWithoutVoice = currentState.messages.filter { it != lastVoiceMessage }
            val result = chatRepository.sendVoiceMessage(
                audioFilePath = lastVoiceMessage.voiceFilePath,
                history = historyWithoutVoice,
                provider = currentState.provider,
                model = currentState.model,
                apiKey = currentState.apiKey,
                personalization = currentState.personalizationConfig
            )

            result.onSuccess { responseText ->
                val assistantMessage = ChatMessage(content = responseText, role = MessageRole.ASSISTANT)
                _state.value = _state.value.copy(
                    messages = _state.value.messages + assistantMessage,
                    isLoading = false
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    error = "Failed to process voice message: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun handlePlayVoiceMessage(messageId: String) {
        scope.launch {
            try {
                val message = _state.value.messages.find { it.id == messageId }
                if (message == null || !message.isVoice || message.voiceFilePath == null) return@launch

                if (_state.value.playingMessageId == messageId) {
                    audioPlayer.resume()
                    return@launch
                }

                audioPlayer.stop()
                playbackPositionJob?.cancel()

                val result = audioPlayer.play(message.voiceFilePath)
                if (result.isSuccess) {
                    _state.value = _state.value.copy(playingMessageId = messageId, playbackPosition = 0, playbackDuration = 0)

                    playbackPositionJob = scope.launch {
                        launch { audioPlayer.currentPosition.collect { pos -> _state.value = _state.value.copy(playbackPosition = pos) } }
                        launch { audioPlayer.duration.collect { dur -> _state.value = _state.value.copy(playbackDuration = dur) } }
                        launch {
                            audioPlayer.isPlaying.collect { playing ->
                                if (!playing && _state.value.playingMessageId != null) {
                                    _state.value = _state.value.copy(playingMessageId = null, playbackPosition = 0, playbackDuration = 0)
                                }
                            }
                        }
                    }
                } else {
                    _state.value = _state.value.copy(error = "Failed to play: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Error playing: ${e.message}")
            }
        }
    }

    private fun handleStopVoicePlayback() {
        playbackPositionJob?.cancel()
        playbackPositionJob = null
        audioPlayer.stop()
        _state.value = _state.value.copy(playingMessageId = null, playbackPosition = 0, playbackDuration = 0)
    }

    private fun saveChatHistory(messages: List<ChatMessage>) {
        scope.launch {
            try {
                val json = kotlinx.serialization.json.Json { prettyPrint = true }
                val historyData = messages.map { msg ->
                    mapOf(
                        "role" to msg.role.name,
                        "content" to msg.content,
                        "timestamp" to msg.timestamp.toString()
                    )
                }
                val jsonStr = json.encodeToString(kotlinx.serialization.serializer(), historyData)
                FileSaver.saveChatHistory(jsonStr, _state.value.historyFilePath)
            } catch (e: Exception) {
                println("Failed to save chat history: ${e.message}")
            }
        }
    }
}
