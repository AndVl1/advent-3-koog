package ru.andvl.chatter.shared.models.github

import kotlinx.serialization.Serializable

/**
 * События анализа GitHub репозитория для отображения промежуточных результатов
 */
@Serializable
sealed class AnalysisEvent {
    /**
     * Уникальный идентификатор события для использования в UI (ключи LazyColumn)
     */
    abstract val id: String

    /**
     * Начало анализа
     */
    @Serializable
    data class Started(
        val message: String = "Начинаем анализ репозитория...",
        override val id: String = generateEventId()
    ) : AnalysisEvent()

    /**
     * Текущая стадия анализа
     */
    @Serializable
    data class StageUpdate(
        val stage: AnalysisStage,
        val description: String,
        override val id: String = generateEventId()
    ) : AnalysisEvent()

    /**
     * Выполнение инструмента (tool call)
     */
    @Serializable
    data class ToolExecution(
        val toolName: String,
        val description: String,
        override val id: String = generateEventId()
    ) : AnalysisEvent()

    /**
     * Частичный результат от LLM (streaming)
     */
    @Serializable
    data class LLMStreamChunk(
        val content: String,
        val isComplete: Boolean = false,
        override val id: String = generateEventId()
    ) : AnalysisEvent()

    /**
     * Узел графа начал выполнение
     */
    @Serializable
    data class NodeStarted(
        val nodeName: String,
        val description: String? = null,
        override val id: String = generateEventId()
    ) : AnalysisEvent()

    /**
     * Узел графа завершил выполнение
     */
    @Serializable
    data class NodeCompleted(
        val nodeName: String,
        val durationMs: Long? = null,
        override val id: String = generateEventId()
    ) : AnalysisEvent()

    /**
     * Индексация для RAG
     */
    @Serializable
    data class RAGIndexing(
        val filesIndexed: Int,
        val totalChunks: Int,
        val isComplete: Boolean = false,
        override val id: String = generateEventId()
    ) : AnalysisEvent()

    /**
     * Ошибка в процессе анализа
     */
    @Serializable
    data class Error(
        val message: String,
        val recoverable: Boolean = true,
        override val id: String = generateEventId()
    ) : AnalysisEvent()

    /**
     * Анализ завершён
     */
    @Serializable
    data class Completed(
        val message: String = "Анализ завершён",
        override val id: String = generateEventId()
    ) : AnalysisEvent()

    /**
     * Прогресс выполнения по шагам (подграфам)
     */
    @Serializable
    data class Progress(
        val currentStep: Int,     // Текущий шаг (1-based)
        val totalSteps: Int,       // Всего шагов
        val stepName: String,      // Название текущего шага
        override val id: String = generateEventId()
    ) : AnalysisEvent() {
        // Вычисляемый процент на основе текущего шага
        val percentage: Int
            get() = if (totalSteps > 0) (currentStep * 100 / totalSteps) else 0
    }
}

/**
 * Генератор уникальных ID для событий
 * Использует timestamp + счётчик для гарантированной уникальности
 */
private var eventIdCounter = 0L
private fun generateEventId(): String {
    val timestamp = System.currentTimeMillis()
    val counter = eventIdCounter++
    return "event-$timestamp-$counter"
}

/**
 * Стадии анализа GitHub репозитория
 */
@Serializable
enum class AnalysisStage {
    INITIALIZING,           // Инициализация
    COLLECTING_REQUIREMENTS, // Сбор требований
    RAG_INDEXING,           // Индексация для RAG
    ANALYZING_REPOSITORY,   // Анализ репозитория
    CHECKING_REQUIREMENTS,  // Проверка требований
    DOCKER_ANALYSIS,        // Анализ Docker
    GOOGLE_SHEETS_INTEGRATION, // Интеграция с Google Sheets
    FINALIZING              // Завершение
}
