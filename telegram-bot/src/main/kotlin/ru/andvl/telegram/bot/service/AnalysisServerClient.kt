package ru.andvl.telegram.bot.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.andvl.chatter.shared.models.github.GithubAnalysisRequest
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * HTTP клиент для взаимодействия с сервером анализа
 */
class AnalysisServerClient(
    private val serverUrl: String
) {
    private val logger = LoggerFactory.getLogger(AnalysisServerClient::class.java)

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
    }

    /**
     * Получить анализ изменений репозитория за последние 24 часа
     */
    suspend fun getDailyRepositoryAnalysis(repository: String): GithubAnalysisResponse? {
        return try {
            val now = Instant.now()
            val yesterday = now.minusSeconds(24 * 60 * 60)

            val formatter = DateTimeFormatter.ISO_INSTANT
            val timeRange = "${formatter.format(yesterday.atOffset(ZoneOffset.UTC))} to ${formatter.format(now.atOffset(ZoneOffset.UTC))}"

            val userMessage = "Проанализируй изменения в репозитории $repository за период $timeRange. " +
                    "Сделай подробный анализ коммитов, измененных файлов, авторов и общий обзор активности."

            val request = GithubAnalysisRequest(
                userMessage = userMessage
            )

            logger.info("Requesting GitHub analysis for repository: $repository")
            logger.debug("Request message: ${userMessage.take(100)}...")

            val response = httpClient.post("$serverUrl/ai/analyze-github") {
                contentType(ContentType.Application.Json)
                setBody(request)
                timeout {
                    requestTimeoutMillis = 300_000 // 5 minutes timeout
                }
            }

            if (response.status.isSuccess()) {
                val analysisResponse = response.body<GithubAnalysisResponse>()
                logger.info("Successfully received GitHub analysis response")
                analysisResponse
            } else {
                logger.error("Server returned error: ${response.status}")
                null
            }

        } catch (e: Exception) {
            logger.error("Failed to get GitHub analysis: ${e.message}", e)
            null
        }
    }

    /**
     * Проверить доступность сервера анализа
     */
    suspend fun checkServerHealth(): Boolean {
        return try {
            val response = httpClient.get("$serverUrl/ai/health")
            response.status.isSuccess()
        } catch (e: Exception) {
            logger.warn("Server health check failed: ${e.message}")
            false
        }
    }

    fun close() {
        httpClient.close()
    }
}
