package ru.andvl.chatter.app.models

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import ru.andvl.chatter.core.model.conversation.PersonalizationConfig

@OptIn(ExperimentalUuidApi::class)
private fun generateMessageId(): String = Uuid.random().toString()

data class ChatMessage(
    val id: String = generateMessageId(),
    val content: String,
    val role: MessageRole,
    val timestamp: Instant = Clock.System.now(),
    val isVoice: Boolean = false,
    val voiceFilePath: String? = null
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class ChatProvider(
    val displayName: String,
    val availableModels: List<String>
) {
    GOOGLE(
        displayName = "Google",
        availableModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro-latest",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        )
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        availableModels = listOf(
            "qwen/qwen3-coder",
            "z-ai/glm-4.6",
            "google/gemini-2.5-pro",
            "z-ai/glm-4.5-air:free"
        )
    );

    val defaultModel: String
        get() = availableModels.first()
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentInput: String = "",
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val error: String? = null,
    val recordingDuration: Long = 0L,
    val audioLevel: Float = 0f,
    val playingMessageId: String? = null,
    val playbackPosition: Long = 0L,
    val playbackDuration: Long = 0L,
    val provider: ChatProvider = ChatProvider.OPENROUTER,
    val model: String = ChatProvider.OPENROUTER.defaultModel,
    val apiKey: String = "",
    val saveHistoryEnabled: Boolean = true,
    val historyFilePath: String = "./chat_history.json",
    val personalizationConfig: PersonalizationConfig = PersonalizationConfig.DEFAULT
)
