package ru.andvl.chatter.shared.models.github

import kotlinx.serialization.Serializable

/**
 * Wrapper для потока событий анализа или финального результата
 */
@Serializable
sealed class AnalysisEventOrResult {
    /**
     * Промежуточное событие анализа
     */
    @Serializable
    data class Event(val event: AnalysisEvent) : AnalysisEventOrResult()

    /**
     * Финальный результат анализа
     */
    @Serializable
    data class Result(val response: GithubAnalysisResponse) : AnalysisEventOrResult()

    /**
     * Ошибка в процессе анализа
     */
    @Serializable
    data class Error(
        val message: String,
        val stackTrace: String? = null
    ) : AnalysisEventOrResult()
}
