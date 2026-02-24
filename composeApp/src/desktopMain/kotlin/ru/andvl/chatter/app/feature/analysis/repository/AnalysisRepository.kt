package ru.andvl.chatter.app.feature.analysis.repository

import kotlinx.coroutines.flow.Flow
import ru.andvl.chatter.app.models.AnalysisConfig
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse

interface AnalysisRepository {
    fun analyzeWithEvents(config: AnalysisConfig): Flow<AnalysisEventData>
    suspend fun analyze(config: AnalysisConfig): Result<GithubAnalysisResponse>
    suspend fun getAvailableOllamaModels(): List<String>
    suspend fun isOllamaAvailable(): Boolean
}

data class AnalysisEventData(
    val progress: Int,
    val eventText: String,
    val currentStep: Int = 0,
    val totalSteps: Int = 6,
    val currentStepName: String = "",
    val result: GithubAnalysisResponse? = null,
    val error: String? = null,
    val isComplete: Boolean = false
)
