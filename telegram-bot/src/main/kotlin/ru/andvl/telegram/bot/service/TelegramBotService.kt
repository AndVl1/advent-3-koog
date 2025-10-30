package ru.andvl.telegram.bot.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse
import ru.andvl.telegram.bot.model.BotConfig
import ru.andvl.telegram.bot.model.SendMessageRequest
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Сервис для работы с Telegram Bot API
 */
class TelegramBotService(
    private val config: BotConfig
) {
    private val logger = LoggerFactory.getLogger(TelegramBotService::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        engine {
            // Увеличиваем таймауты для медленных соединений
            requestTimeout = 30_000
            // Можно добавить прокси если нужно
            // proxy = ProxyBuilder.http("proxy.example.com", 8080)
        }
    }

    private val telegramApiUrl = "https://api.telegram.org/bot${config.telegramBotToken}"

    /**
     * Отправить ежедневный отчет администратору
     */
    suspend fun sendDailyReport(analysisResponse: GithubAnalysisResponse, repository: String, period: String) {
        try {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

            // Основное сообщение
            val message = buildString {
                append("📊 Отчет за ${date}\n\n")
                append("📈 TLDR: ${analysisResponse.tldr}\n\n")
                append("🔗 Репозиторий: $repository\n")
                append("📅 Период: $period")
            }

            // Отправляем основное сообщение
            sendMessage(config.adminUserId, message)

            // Создаем и отправляем MD файл с полным отчетом
            val reportFile = createReportFile(analysisResponse, repository, period, date)

            sendDocument(config.adminUserId, reportFile, "📋 Полный отчет об изменениях")

            // Удаляем временный файл
            reportFile.delete()

            logger.info("Daily report sent successfully to admin ${config.adminUserId}")

        } catch (e: Exception) {
            logger.error("Failed to send daily report: ${e.message}", e)

            // Попытка отправить сообщение об ошибке
            try {
                sendMessage(
                    config.adminUserId,
                    "⚠️ Ошибка при генерации отчета\n\n" +
                    "Не удалось создать ежедневный отчет для репозитория ${config.targetRepository}.\n" +
                    "Ошибка: ${e.message}"
                )
            } catch (ex: Exception) {
                logger.error("Failed to send error message: ${ex.message}", ex)
            }
        }
    }

    /**
     * Отправить уведомление о статусе бота
     */
    suspend fun sendStatusUpdate(message: String, isError: Boolean = false) {
        try {
            val emoji = if (isError) "❌" else "ℹ️"
            val formattedMessage = "$emoji Статус бота\n\n$message"

            sendMessage(config.adminUserId, formattedMessage)

            logger.info("Status update sent: $message")
        } catch (e: Exception) {
            logger.error("Failed to send status update: ${e.message}", e)
        }
    }

    /**
     * Создать временный MD файл с отчетом
     */
    private fun createReportFile(analysisResponse: GithubAnalysisResponse, repository: String, period: String, date: String): File {
        val fileName = "daily_report_${date.replace(".", "_")}.md"
        val tempFile = File.createTempFile("telegram_bot_", "_$fileName")

        val reportContent = buildString {
            append("# 📊 Ежедневный отчет по репозиторию $repository\n\n")
            append("**Дата:** $date\n")
            append("**Период анализа:** $period\n")
            append("**Модель:** ${analysisResponse.model ?: "N/A"}\n")
            analysisResponse.usage?.let { usage ->
                append("**Токены:** ${usage.totalTokens} (${usage.promptTokens} + ${usage.completionTokens})\n")
            }
            append("\n## 🎯 Краткая сводка\n\n")
            append("${analysisResponse.tldr}\n\n")
            append("## 📋 Подробный анализ\n\n")
            append("${analysisResponse.analysis}\n\n")
            if (analysisResponse.toolCalls.isNotEmpty()) {
                append("## 🔧 Использованные инструменты\n\n")
                analysisResponse.toolCalls.forEach { toolCall ->
                    append("- $toolCall\n")
                }
                append("\n")
            }
            append("---\n")
            append("*Отчет сгенерирован автоматически ботом*")
        }

        tempFile.writeText(reportContent)
        return tempFile
    }

    /**
     * Отправить текстовое сообщение через HTTP API
     */
    private suspend fun sendMessage(chatId: String, text: String) {
        try {
            val request = SendMessageRequest(
                chat_id = chatId,
                text = text,
                disable_web_page_preview = true
            )

            logger.debug("Sending message to Telegram API: $telegramApiUrl/sendMessage")
            val response = httpClient.post("$telegramApiUrl/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(request)
                timeout {
                    requestTimeoutMillis = 30_000
                }
            }

            if (response.status.isSuccess()) {
                logger.debug("Message sent successfully")
            } else {
                logger.error("Telegram API returned error: ${response.status}")
            }
        } catch (e: java.nio.channels.UnresolvedAddressException) {
            logger.error("Cannot resolve Telegram API address. Check internet connection or try using a proxy.")
            throw e
        } catch (e: Exception) {
            logger.error("Failed to send message to Telegram: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    /**
     * Отправить документ через HTTP API
     */
    private suspend fun sendDocument(chatId: String, file: File, caption: String) {
        httpClient.submitFormWithBinaryData(
            url = "$telegramApiUrl/sendDocument",
            formData = formData {
                append("chat_id", chatId)
                append("caption", caption)
                append("document", file.readBytes(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                })
            }
        )
    }

    /**
     * Проверить подключение к Telegram API
     */
    suspend fun checkTelegramConnection(): Boolean {
        return try {
            logger.info("Testing connection to Telegram API...")
            val response = httpClient.get("$telegramApiUrl/getMe") {
                timeout {
                    requestTimeoutMillis = 10_000
                }
            }

            val isSuccess = response.status.isSuccess()
            if (isSuccess) {
                logger.info("Telegram API connection successful")
            } else {
                logger.error("Telegram API connection failed: ${response.status}")
            }
            isSuccess
        } catch (e: java.nio.channels.UnresolvedAddressException) {
            logger.error("Cannot resolve api.telegram.org. Check internet connection or DNS settings.")
            false
        } catch (e: Exception) {
            logger.error("Failed to connect to Telegram API: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * Проверить валидность конфигурации бота
     */
    fun validateConfig(): Boolean {
        return try {
            config.adminUserId.toLong()
            config.telegramBotToken.isNotBlank()
            true
        } catch (e: Exception) {
            logger.error("Invalid bot configuration: ${e.message}")
            false
        }
    }

    /**
     * Закрыть HTTP клиент
     */
    fun shutdown() {
        httpClient.close()
    }
}
