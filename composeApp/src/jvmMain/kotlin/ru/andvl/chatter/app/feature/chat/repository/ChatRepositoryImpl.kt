package ru.andvl.chatter.app.feature.chat.repository

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.andvl.chatter.app.models.ChatMessage
import ru.andvl.chatter.app.models.ChatProvider
import ru.andvl.chatter.app.models.MessageRole
import ru.andvl.chatter.core.model.conversation.PersonalizationConfig
import ru.andvl.chatter.koog.mapping.toKoogMessage
import ru.andvl.chatter.koog.model.conversation.ConversationRequest
import ru.andvl.chatter.app.platform.FileSaver
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.koog.service.Provider
import ru.andvl.chatter.shared.models.MessageMeta
import ru.andvl.chatter.shared.models.SharedMessage
import ru.andvl.chatter.shared.models.MessageRole as SharedMessageRole

class ChatRepositoryImpl : ChatRepository {

    private val koogService = KoogService(logsDir = FileSaver.getLogsDir())

    override suspend fun sendMessage(
        text: String,
        history: List<ChatMessage>,
        provider: ChatProvider,
        model: String,
        apiKey: String,
        personalization: PersonalizationConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val promptExecutor = createPromptExecutor(provider, apiKey)
            val koogHistory = history.takeLast(10).map { it.toKoogMessage() }

            val conversationRequest = ConversationRequest(
                message = text,
                history = koogHistory,
                maxHistoryLength = 10,
                personalization = personalization
            )

            val koogProvider = provider.toKoogProvider()
            val response = koogService.conversation(conversationRequest, promptExecutor, koogProvider)
            Result.success(response.text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendVoiceMessage(
        audioFilePath: String,
        history: List<ChatMessage>,
        provider: ChatProvider,
        model: String,
        apiKey: String,
        personalization: PersonalizationConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val promptExecutor = createPromptExecutor(provider, apiKey)
            val koogHistory = history.takeLast(10).map { it.toKoogMessage() }

            val conversationRequest = ConversationRequest(
                message = "",
                history = koogHistory,
                maxHistoryLength = 10,
                audioFilePath = audioFilePath,
                personalization = personalization
            )

            val koogProvider = provider.toKoogProvider()
            val response = koogService.conversation(conversationRequest, promptExecutor, koogProvider)
            Result.success(response.text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createPromptExecutor(provider: ChatProvider, apiKey: String): PromptExecutor {
        val timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 120_000,
            connectTimeoutMillis = 10_000
        )
        val llmClient = when (provider) {
            ChatProvider.GOOGLE -> GoogleLLMClient(apiKey)
            ChatProvider.OPENROUTER -> OpenRouterLLMClient(
                apiKey = apiKey,
                settings = OpenRouterClientSettings(timeoutConfig = timeoutConfig)
            )
        }
        return SingleLLMPromptExecutor(llmClient)
    }

    private fun ChatProvider.toKoogProvider(): Provider = when (this) {
        ChatProvider.GOOGLE -> Provider.GOOGLE
        ChatProvider.OPENROUTER -> Provider.OPENROUTER
    }

    private fun ChatMessage.toKoogMessage(): ai.koog.prompt.message.Message {
        val sharedRole = when (role) {
            MessageRole.USER -> SharedMessageRole.User
            MessageRole.ASSISTANT -> SharedMessageRole.Assistant
            MessageRole.SYSTEM -> SharedMessageRole.System
        }
        val sharedMessage = SharedMessage(
            role = sharedRole,
            content = content,
            meta = MessageMeta(),
            timestamp = timestamp
        )
        return sharedMessage.toKoogMessage()
    }
}
