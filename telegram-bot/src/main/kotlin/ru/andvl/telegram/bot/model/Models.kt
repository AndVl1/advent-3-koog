package ru.andvl.telegram.bot.model

import kotlinx.serialization.Serializable

data class BotConfig(
    val telegramBotToken: String,
    val adminUserId: String,
    val analysisServerUrl: String,
    val targetRepository: String,
    val dailyReportTime: String
)

@Serializable
data class SendMessageRequest(
    val chat_id: String,
    val text: String,
    val disable_web_page_preview: Boolean = true
)