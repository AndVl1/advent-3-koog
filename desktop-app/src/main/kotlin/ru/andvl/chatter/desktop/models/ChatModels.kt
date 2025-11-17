package ru.andvl.chatter.desktop.models

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Chat message model
 */
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

/**
 * Chat provider enum with available models
 */
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

/**
 * Chat state
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val currentInput: String = "",
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val error: String? = null,
    val recordingDuration: Long = 0, // in milliseconds
    val audioLevel: Float = 0f, // 0.0 to 1.0

    // Playback state
    val playingMessageId: String? = null, // ID of currently playing message
    val playbackPosition: Long = 0, // in milliseconds
    val playbackDuration: Long = 0, // in milliseconds

    // Koog configuration
    val provider: ChatProvider = ChatProvider.OPENROUTER,
    val model: String = ChatProvider.OPENROUTER.defaultModel,
    val apiKey: String = "",

    // History saving
    val saveHistoryEnabled: Boolean = true,
    val historyFilePath: String = "./chat_history.json"
)

/**
 * Chat actions
 */
sealed class ChatAction {
    data class UpdateInput(val input: String) : ChatAction()
    data class SendMessage(val text: String) : ChatAction()
    data object SendVoiceMessage : ChatAction()
    data object StartRecording : ChatAction()
    data object StopRecording : ChatAction()
    data object ClearChat : ChatAction()
    data object ClearError : ChatAction()
}

/**
 * Screen type for navigation
 */
enum class AppScreen {
    GITHUB_ANALYSIS,
    CHAT
}

private var messageIdCounter = 0L
private fun generateMessageId(): String {
    val timestamp = Clock.System.now().toEpochMilliseconds()
    val counter = messageIdCounter++
    return "msg-$timestamp-$counter"
}
