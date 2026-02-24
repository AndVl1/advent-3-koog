package ru.andvl.chatter.app.feature.analysis.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.andvl.chatter.app.interactor.GithubAnalysisInteractor
import ru.andvl.chatter.app.models.AnalysisConfig
import ru.andvl.chatter.app.services.OllamaService
import ru.andvl.chatter.shared.models.github.AnalysisEvent
import ru.andvl.chatter.shared.models.github.AnalysisEventOrResult
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse

class AnalysisRepositoryImpl(
    private val interactor: GithubAnalysisInteractor = GithubAnalysisInteractor()
) : AnalysisRepository {

    override fun analyzeWithEvents(config: AnalysisConfig): Flow<AnalysisEventData> {
        return interactor.analyzeRepositoryWithEvents(config).map { eventOrResult ->
            when (eventOrResult) {
                is AnalysisEventOrResult.Event -> mapAnalysisEvent(eventOrResult.event)
                is AnalysisEventOrResult.Result -> AnalysisEventData(
                    progress = 100,
                    eventText = "Analysis complete",
                    result = eventOrResult.response,
                    isComplete = true
                )
                is AnalysisEventOrResult.Error -> AnalysisEventData(
                    progress = 0,
                    eventText = eventOrResult.message,
                    error = eventOrResult.message,
                    isComplete = true
                )
            }
        }
    }

    private fun mapAnalysisEvent(event: AnalysisEvent): AnalysisEventData {
        return when (event) {
            is AnalysisEvent.Progress -> AnalysisEventData(
                progress = event.percentage,
                eventText = if (event.currentStep > 0) "Step ${event.currentStep}/${event.totalSteps}: ${event.stepName}" else event.stepName,
                currentStep = event.currentStep,
                totalSteps = event.totalSteps,
                currentStepName = event.stepName
            )
            is AnalysisEvent.Completed -> AnalysisEventData(
                progress = 100,
                eventText = event.message
            )
            is AnalysisEvent.Started -> AnalysisEventData(
                progress = 0,
                eventText = event.message
            )
            is AnalysisEvent.StageUpdate -> AnalysisEventData(
                progress = 0,
                eventText = event.description
            )
            is AnalysisEvent.ToolExecution -> AnalysisEventData(
                progress = 0,
                eventText = "Tool: ${event.description}"
            )
            is AnalysisEvent.NodeStarted -> AnalysisEventData(
                progress = 0,
                eventText = event.description ?: event.nodeName
            )
            is AnalysisEvent.NodeCompleted -> AnalysisEventData(
                progress = 0,
                eventText = "Completed: ${event.nodeName}"
            )
            is AnalysisEvent.RAGIndexing -> AnalysisEventData(
                progress = 0,
                eventText = "RAG: ${event.filesIndexed} files indexed"
            )
            is AnalysisEvent.LLMStreamChunk -> AnalysisEventData(
                progress = 0,
                eventText = "LLM: ${event.content.take(50)}"
            )
            is AnalysisEvent.Error -> AnalysisEventData(
                progress = 0,
                eventText = "Error: ${event.message}",
                error = event.message
            )
        }
    }

    override suspend fun analyze(config: AnalysisConfig): Result<GithubAnalysisResponse> {
        return interactor.analyzeRepository(config)
    }

    override suspend fun getAvailableOllamaModels(): List<String> {
        return OllamaService.getAvailableModels()
    }

    override suspend fun isOllamaAvailable(): Boolean {
        return OllamaService.isAvailable()
    }
}
