package ru.andvl.chatter.codeagent.repository

import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.koog.service.Provider
import ru.andvl.chatter.shared.models.codeagent.AnalysisType
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisRequest
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisResult

/**
 * Repository layer for Repository Analysis
 *
 * This repository wraps KoogService API and provides clean interface for business logic layer.
 * It follows the Repository pattern from Clean Architecture.
 *
 * @property koogService Service for Koog AI agent interactions
 */
class RepositoryAnalysisRepository(
    private val koogService: KoogService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Analyze GitHub repository
     *
     * @param githubUrl GitHub repository URL
     * @param analysisType Type of analysis to perform
     * @param enableEmbeddings Whether to enable embeddings for RAG
     * @param promptExecutor PromptExecutor for LLM calls
     * @return Result with RepositoryAnalysisResult or error
     */
    suspend fun analyzeRepository(
        githubUrl: String,
        analysisType: AnalysisType,
        enableEmbeddings: Boolean,
        promptExecutor: PromptExecutor
    ): Result<RepositoryAnalysisResult> {
        return withContext(Dispatchers.IO) {
            try {
                logger.info { "Starting repository analysis: $githubUrl" }

                val request = RepositoryAnalysisRequest(
                    githubUrl = githubUrl,
                    analysisType = analysisType,
                    enableEmbeddings = enableEmbeddings
                )

                val result = koogService.analyzeRepository(
                    request = request,
                    promptExecutor = promptExecutor,
                    provider = Provider.OPENROUTER
                )

                if (result.errorMessage != null) {
                    logger.error { "Repository analysis failed: ${result.errorMessage}" }
                    Result.failure(Exception(result.errorMessage))
                } else {
                    logger.info { "Repository analysis completed successfully" }
                    Result.success(result)
                }
            } catch (e: Exception) {
                logger.error(e) { "Repository analysis threw exception" }
                Result.failure(e)
            }
        }
    }
}
