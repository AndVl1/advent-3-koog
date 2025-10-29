package ru.andvl.mcp.telegraph

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * HTTP клиент для работы с Telegraph API
 */
class TelegraphClient(private val accessToken: String? = null) {

    private val logger = LoggerFactory.getLogger(TelegraphClient::class.java)

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
     * Создать новый аккаунт Telegraph
     */
    suspend fun createAccount(
        shortName: String,
        authorName: String? = null,
        authorUrl: String? = null
    ): TelegraphAccount? {
        return try {
            val response = httpClient.post("https://api.telegra.ph/createAccount") {
                setBody(FormDataContent(Parameters.build {
                    append("short_name", shortName)
                    authorName?.let { append("author_name", it) }
                    authorUrl?.let { append("author_url", it) }
                }))
            }

            val apiResponse = response.body<TelegraphResponse<TelegraphAccount>>()
            if (apiResponse.ok && apiResponse.result != null) {
                apiResponse.result
            } else {
                logger.error("Error creating account: ${apiResponse.error}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error creating account: ${e.message}", e)
            null
        }
    }

    /**
     * Получить информацию об аккаунте
     */
    suspend fun getAccountInfo(
        accessToken: String,
        fields: List<String> = listOf("short_name", "author_name", "author_url", "page_count")
    ): TelegraphAccount? {
        return try {
            val fieldsJson = buildJsonArray {
                fields.forEach { field ->
                    add(JsonPrimitive(field))
                }
            }

            val response = httpClient.post("https://api.telegra.ph/getAccountInfo") {
                setBody(FormDataContent(Parameters.build {
                    append("access_token", accessToken)
                    append("fields", fieldsJson.toString())
                }))
            }

            val apiResponse = response.body<TelegraphResponse<TelegraphAccount>>()
            if (apiResponse.ok && apiResponse.result != null) {
                apiResponse.result
            } else {
                logger.error("Error getting account info: ${apiResponse.error}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error getting account info: ${e.message}", e)
            null
        }
    }

    /**
     * Изменить информацию об аккаунте
     */
    suspend fun editAccountInfo(
        accessToken: String,
        shortName: String? = null,
        authorName: String? = null,
        authorUrl: String? = null
    ): TelegraphAccount? {
        return try {
            val response = httpClient.post("https://api.telegra.ph/editAccountInfo") {
                setBody(FormDataContent(Parameters.build {
                    append("access_token", accessToken)
                    shortName?.let { append("short_name", it) }
                    authorName?.let { append("author_name", it) }
                    authorUrl?.let { append("author_url", it) }
                }))
            }

            val apiResponse = response.body<TelegraphResponse<TelegraphAccount>>()
            if (apiResponse.ok && apiResponse.result != null) {
                apiResponse.result
            } else {
                logger.error("Error editing account info: ${apiResponse.error}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error editing account info: ${e.message}", e)
            null
        }
    }

    /**
     * Создать новую страницу Telegraph
     */
    suspend fun createPage(
        accessToken: String,
        title: String,
        authorName: String? = null,
        authorUrl: String? = null,
        content: List<TelegraphNode>,
        returnContent: Boolean = false
    ): TelegraphPage? {
        return try {
            retryWithBackoff(maxRetries = 3) {
                logger.debug("Creating page with content: ${content.toJsonArray().toString()}")
                val response = httpClient.post("https://api.telegra.ph/createPage") {
                    setBody(FormDataContent(Parameters.build {
                        append("access_token", accessToken)
                        append("title", title)
                        authorName?.let { append("author_name", it) }
                        authorUrl?.let { append("author_url", it) }
                        append("content", content.toJsonArray().toString())
                        append("return_content", returnContent.toString())
                    }))
                }

                val apiResponse = response.body<TelegraphResponse<TelegraphPage>>()
                if (apiResponse.ok && apiResponse.result != null) {
                    apiResponse.result
                } else {
                    logger.error("Error creating page: ${apiResponse.error}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Error creating page after retries: ${e.message}", e)
            null
        }
    }

    /**
     * Редактировать существующую страницу Telegraph
     */
    suspend fun editPage(
        accessToken: String,
        path: String,
        title: String,
        authorName: String? = null,
        authorUrl: String? = null,
        content: List<TelegraphNode>,
        returnContent: Boolean = false
    ): TelegraphPage? {
        return try {
            logger.debug("Editing page $path with content: ${content.toJsonArray().toString()}")
            val response = httpClient.post("https://api.telegra.ph/editPage/$path") {
                setBody(FormDataContent(Parameters.build {
                    append("access_token", accessToken)
                    append("title", title)
                    authorName?.let { append("author_name", it) }
                    authorUrl?.let { append("author_url", it) }
                    append("content", content.toJsonArray().toString())
                    append("return_content", returnContent.toString())
                }))
            }

            val apiResponse = response.body<TelegraphResponse<TelegraphPage>>()
            if (apiResponse.ok && apiResponse.result != null) {
                apiResponse.result
            } else {
                logger.error("Error editing page: ${apiResponse.error}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error editing page: ${e.message}", e)
            null
        }
    }

    /**
     * Получить страницу Telegraph
     */
    suspend fun getPage(
        path: String,
        returnContent: Boolean = false
    ): TelegraphPage? {
        return try {
            val response = httpClient.get("https://api.telegra.ph/getPage/$path") {
                parameter("return_content", returnContent)
            }

            val apiResponse = response.body<TelegraphResponse<TelegraphPage>>()
            if (apiResponse.ok && apiResponse.result != null) {
                apiResponse.result
            } else {
                logger.error("Error getting page: ${apiResponse.error}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error getting page: ${e.message}", e)
            null
        }
    }

    /**
     * Получить список страниц аккаунта
     */
    suspend fun getPageList(
        accessToken: String,
        offset: Int = 0,
        limit: Int = 50
    ): TelegraphPageList? {
        return try {
            val response = httpClient.get("https://api.telegra.ph/getPageList") {
                parameter("access_token", accessToken)
                parameter("offset", offset)
                parameter("limit", limit)
            }

            val apiResponse = response.body<TelegraphResponse<TelegraphPageList>>()
            if (apiResponse.ok && apiResponse.result != null) {
                apiResponse.result
            } else {
                logger.error("Error getting page list: ${apiResponse.error}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error getting page list: ${e.message}", e)
            null
        }
    }

    /**
     * Получить количество просмотров страницы
     */
    suspend fun getViews(
        path: String,
        year: Int? = null,
        month: Int? = null,
        day: Int? = null,
        hour: Int? = null
    ): TelegraphPageViews? {
        return try {
            val response = httpClient.get("https://api.telegra.ph/getViews/$path") {
                year?.let { parameter("year", it) }
                month?.let { parameter("month", it) }
                day?.let { parameter("day", it) }
                hour?.let { parameter("hour", it) }
            }

            val apiResponse = response.body<TelegraphResponse<TelegraphPageViews>>()
            if (apiResponse.ok && apiResponse.result != null) {
                apiResponse.result
            } else {
                logger.error("Error getting views: ${apiResponse.error}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error getting views: ${e.message}", e)
            null
        }
    }

    /**
     * Отозвать access токен и создать новый
     */
    suspend fun revokeAccessToken(
        accessToken: String
    ): TelegraphAccount? {
        return try {
            val response = httpClient.post("https://api.telegra.ph/revokeAccessToken") {
                setBody(FormDataContent(Parameters.build {
                    append("access_token", accessToken)
                }))
            }

            val apiResponse = response.body<TelegraphResponse<TelegraphAccount>>()
            if (apiResponse.ok && apiResponse.result != null) {
                apiResponse.result
            } else {
                logger.error("Error revoking access token: ${apiResponse.error}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error revoking access token: ${e.message}", e)
            null
        }
    }

    fun close() {
        httpClient.close()
    }
}

/**
 * Вспомогательная функция для повторных попыток
 */
private suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 500,
    maxDelay: Long = 2000,
    operation: suspend () -> T
): T? {
    val logger = LoggerFactory.getLogger("TelegraphClientRetry")
    var currentDelay = initialDelay
    repeat(maxRetries) { attempt ->
        try {
            val result = operation()
            return result
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) {
                throw e
            }
            logger.warn("Attempt ${attempt + 1} failed: ${e.message}. Retrying in ${currentDelay}ms...")
            delay(currentDelay)
            currentDelay = minOf(currentDelay * 2, maxDelay)
        }
    }
    return null
}

/**
 * Вспомогательные функции для конвертации
 */
private fun List<TelegraphNode>.toJsonArray(): JsonArray {
    return buildJsonArray {
        this@toJsonArray.forEach { node ->
            add(buildJsonObject {
                put("tag", node.tag)
                node.children?.let { children ->
                    putJsonArray("children") {
                        children.forEach { child ->
                            add(JsonPrimitive(child))
                        }
                    }
                }
                node.attrs?.let { attrs ->
                    putJsonObject("attrs") {
                        attrs.forEach { (key, value) ->
                            put(key, value)
                        }
                    }
                }
            })
        }
    }
}
