package ru.andvl.chatter.codeagent.repository

import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.koog.service.Provider
import ru.andvl.chatter.shared.models.codeagent.CodeModificationRequest
import ru.andvl.chatter.shared.models.codeagent.CodeModificationResponse

/**
 * Repository interface for Code Modification operations
 *
 * This interface defines the contract for Code Modification data operations.
 * It follows the Repository pattern from Clean Architecture.
 */
interface CodeModificationRepository {
    /**
     * Request code modification with AI-generated plan
     *
     * @param sessionId Current session ID
     * @param modificationRequest Description of desired modifications
     * @param createBranch Whether to create a new git branch
     * @param branchName Custom branch name (null = auto-generate)
     * @param promptExecutor PromptExecutor for LLM calls
     * @return Result with CodeModificationResponse or error
     */
    suspend fun requestModification(
        sessionId: String,
        modificationRequest: String,
        createBranch: Boolean,
        branchName: String?,
        promptExecutor: PromptExecutor
    ): Result<CodeModificationResponse>
}

/**
 * Implementation of CodeModificationRepository
 *
 * This repository wraps KoogService API and provides clean interface for business logic layer.
 *
 * @property koogService Service for Koog AI agent interactions
 */
class CodeModificationRepositoryImpl(
    private val koogService: KoogService
) : CodeModificationRepository {
    private val logger = KotlinLogging.logger {}

    override suspend fun requestModification(
        sessionId: String,
        modificationRequest: String,
        createBranch: Boolean,
        branchName: String?,
        promptExecutor: PromptExecutor
    ): Result<CodeModificationResponse> {
        return withContext(Dispatchers.IO) {
            try {
                logger.info { "Requesting code modification for session: $sessionId" }

                val request = CodeModificationRequest(
                    sessionId = sessionId,
                    instructions = modificationRequest,
                    fileScope = null, // Will be determined by the agent
                    enableValidation = true,
                    maxChanges = 50
                )

                val response = koogService.modifyCode(
                    request = request,
                    promptExecutor = promptExecutor,
                    provider = Provider.OPENROUTER
                )

                if (response.success) {
                    logger.info { "Code modification completed successfully" }
                    Result.success(response)
                } else {
                    val errorMsg = response.errorMessage ?: "Unknown error occurred"
                    logger.error { "Code modification failed: $errorMsg" }
                    Result.failure(CodeModificationException(errorMsg))
                }
            } catch (e: Exception) {
                logger.error(e) { "Code modification threw exception" }
                Result.failure(e)
            }
        }
    }
}

/**
 * Exception thrown when code modification operation fails
 */
class CodeModificationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
