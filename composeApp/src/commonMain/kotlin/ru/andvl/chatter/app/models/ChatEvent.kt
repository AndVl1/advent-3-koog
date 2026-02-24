package ru.andvl.chatter.app.models

import ru.andvl.chatter.core.model.conversation.PersonalizationConfig

sealed interface ChatEvent {
    data class UpdateInput(val input: String) : ChatEvent
    data class SendMessage(val text: String) : ChatEvent
    data object StartVoiceRecording : ChatEvent
    data object StopVoiceRecording : ChatEvent
    data object SendVoiceMessage : ChatEvent
    data class PlayVoiceMessage(val messageId: String) : ChatEvent
    data class PauseVoiceMessage(val messageId: String) : ChatEvent
    data class StopVoicePlayback(val messageId: String) : ChatEvent
    data object ClearChat : ChatEvent
    data object ClearError : ChatEvent
    data class SelectProvider(val provider: ChatProvider) : ChatEvent
    data class UpdateModel(val model: String) : ChatEvent
    data class UpdateApiKey(val apiKey: String) : ChatEvent
    data class ToggleHistorySaving(val enabled: Boolean) : ChatEvent
    data class UpdatePersonalization(val config: PersonalizationConfig) : ChatEvent
}
