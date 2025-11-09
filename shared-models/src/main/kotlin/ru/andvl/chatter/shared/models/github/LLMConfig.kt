package ru.andvl.chatter.shared.models.github

import kotlinx.serialization.Serializable

/**
 * LLM Configuration for analysis
 */
@Serializable
data class LLMConfig(
    val provider: String,
    val model: String,
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val fixingModel: String? = null,  // Модель для исправления ошибок парсинга
    val maxContextTokens: Long = 100_000L,  // Максимальный размер контекста для основной модели
    val fixingMaxContextTokens: Long = 20_000L  // Максимальный размер контекста для fixing модели
)
