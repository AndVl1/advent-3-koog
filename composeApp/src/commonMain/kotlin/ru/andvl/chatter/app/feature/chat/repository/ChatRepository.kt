package ru.andvl.chatter.app.feature.chat.repository

import ru.andvl.chatter.app.models.ChatMessage
import ru.andvl.chatter.app.models.ChatProvider
import ru.andvl.chatter.core.model.conversation.PersonalizationConfig

interface ChatRepository {
    suspend fun sendMessage(
        text: String,
        history: List<ChatMessage>,
        provider: ChatProvider,
        model: String,
        apiKey: String,
        personalization: PersonalizationConfig
    ): Result<String>

    suspend fun sendVoiceMessage(
        audioFilePath: String,
        history: List<ChatMessage>,
        provider: ChatProvider,
        model: String,
        apiKey: String,
        personalization: PersonalizationConfig
    ): Result<String>
}
