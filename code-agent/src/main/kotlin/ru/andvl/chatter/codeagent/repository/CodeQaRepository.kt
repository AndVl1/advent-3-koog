package ru.andvl.chatter.codeagent.repository

import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.andvl.chatter.codeagent.viewmodel.CodeQaMessageUi
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.koog.service.Provider
import ru.andvl.chatter.shared.models.codeagent.CodeQAMessage
import ru.andvl.chatter.shared.models.codeagent.CodeQARequest
import ru.andvl.chatter.shared.models.codeagent.CodeQAResponse
import ru.andvl.chatter.shared.models.codeagent.MessageRole

/**
 * Repository interface for Code QA operations
 *
 * This interface defines the contract for Code QA data operations.
 * It follows the Repository pattern from Clean Architecture.
 */
interface CodeQaRepository {
    /**
     * Ask a question about code in the repository
     *
     * @param sessionId Current session ID
     * @param question Question text from user
     * @param history Previous conversation messages
     * @param promptExecutor PromptExecutor for LLM calls
     * @return Result with CodeQAResponse or error
     */
    suspend fun askQuestion(
        sessionId: String,
        question: String,
        history: List<CodeQaMessageUi>,
        promptExecutor: PromptExecutor
    ): Result<CodeQAResponse>
}

/**
 * Implementation of CodeQaRepository
 *
 * This repository wraps KoogService API and provides clean interface for business logic layer.
 *
 * @property koogService Service for Koog AI agent interactions
 */
class CodeQaRepositoryImpl(
    private val koogService: KoogService
) : CodeQaRepository {
    private val logger = KotlinLogging.logger {}

    override suspend fun askQuestion(
        sessionId: String,
        question: String,
        history: List<CodeQaMessageUi>,
        promptExecutor: PromptExecutor
    ): Result<CodeQAResponse> {
        return withContext(Dispatchers.IO) {
            try {
                logger.info { "Asking code question for session: $sessionId" }

                // Convert UI history to backend model
                val backendHistory = history.map { message ->
                    CodeQAMessage(
                        role = when (message.role) {
                            ru.andvl.chatter.codeagent.viewmodel.CodeQaRole.USER -> MessageRole.USER
                            ru.andvl.chatter.codeagent.viewmodel.CodeQaRole.ASSISTANT -> MessageRole.ASSISTANT
                        },
                        content = message.content,
                        timestamp = message.timestamp,
                        codeReferences = message.codeReferences.map { ref ->
                            ru.andvl.chatter.shared.models.codeagent.CodeReference(
                                filePath = ref.filePath,
                                lineStart = ref.lineStart,
                                lineEnd = ref.lineEnd,
                                codeSnippet = ref.codeSnippet
                            )
                        }
                    )
                }

                val request = CodeQARequest(
                    sessionId = sessionId,
                    question = question,
                    history = backendHistory,
                    maxHistoryLength = 10
                )

                val response = koogService.askCodeQuestion(
                    request = request,
                    promptExecutor = promptExecutor,
                    provider = Provider.OPENROUTER
                )

                logger.info { "Code question answered successfully" }
                Result.success(response)
            } catch (e: Exception) {
                logger.error(e) { "Code question failed" }
                Result.failure(e)
            }
        }
    }
}
