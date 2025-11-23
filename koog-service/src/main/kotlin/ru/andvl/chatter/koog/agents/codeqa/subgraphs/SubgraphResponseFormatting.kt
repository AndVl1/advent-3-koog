package ru.andvl.chatter.koog.agents.codeqa.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codeqa.*
import ru.andvl.chatter.koog.model.codeqa.AnswerGenerationResult
import ru.andvl.chatter.koog.model.codeqa.CodeReferenceInternal
import ru.andvl.chatter.shared.models.codeagent.CodeQAResponse
import ru.andvl.chatter.shared.models.codeagent.CodeReference
import ru.andvl.chatter.shared.models.codeagent.CodeQAMessage
import ru.andvl.chatter.shared.models.codeagent.MessageRole

private val logger = LoggerFactory.getLogger("codeqa-response-formatting")

/**
 * Subgraph: Response Formatting
 *
 * Purpose: Convert internal models to public API response format
 *
 * Flow:
 * 1. Format response - convert internal models to CodeQAResponse
 * 2. Update conversation history - add current Q&A to history
 *
 * Input: AnswerGenerationResult
 * Output: CodeQAResponse (public API response)
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphResponseFormatting():
        AIAgentSubgraphDelegate<AnswerGenerationResult, CodeQAResponse> =
    subgraph(name = "response-formatting") {
        val nodeFormatResponse by nodeFormatResponse()
        val nodeUpdateConversationHistory by nodeUpdateConversationHistory()

        edge(nodeStart forwardTo nodeFormatResponse)
        edge(nodeFormatResponse forwardTo nodeUpdateConversationHistory)
        edge(nodeUpdateConversationHistory forwardTo nodeFinish)
    }

/**
 * Node: Format Response
 *
 * Converts internal AnswerGenerationResult to public CodeQAResponse.
 * Extracts only the essential code references (top N by relevance).
 */
private fun AIAgentSubgraphBuilderBase<AnswerGenerationResult, CodeQAResponse>.nodeFormatResponse() =
    node<AnswerGenerationResult, CodeQAResponse>("format-response") { answerResult ->
        logger.info("Formatting response for public API")

        val modelName = "gemini-2.5-flash-exp" // Default model, would be passed from config

        // Convert internal code references to public format
        // Take top 5 most relevant references
        val publicCodeReferences = answerResult.codeReferencesWithExplanations
            .sortedByDescending { it.reference.relevanceScore }
            .take(5)
            .map { refWithExplanation ->
                CodeReference(
                    filePath = refWithExplanation.reference.filePath,
                    lineStart = refWithExplanation.reference.lineStart,
                    lineEnd = refWithExplanation.reference.lineEnd,
                    codeSnippet = refWithExplanation.reference.codeSnippet
                )
            }

        // Build comprehensive answer including explanations
        val enhancedAnswer = buildString {
            append(answerResult.answer)

            // Add follow-up suggestions if available
            if (answerResult.followUpSuggestions.isNotEmpty()) {
                append("\n\n**Follow-up questions you might ask:**\n")
                answerResult.followUpSuggestions.forEach { suggestion ->
                    append("- $suggestion\n")
                }
            }

            // Suggest code modification agent if detected
            if (answerResult.suggestsCodeModification) {
                append("\n\n_Note: It seems you might want to modify code. Consider using the Code Modification Agent for this task._")
            }
        }

        val response = CodeQAResponse(
            answer = enhancedAnswer,
            codeReferences = publicCodeReferences,
            confidence = answerResult.confidence,
            model = modelName,
            usage = null // Would be populated with actual token usage
        )

        logger.info("Response formatted successfully")
        logger.debug("Response length: ${response.answer.length} characters")
        logger.debug("Code references: ${response.codeReferences.size}")

        response
    }

/**
 * Node: Update Conversation History
 *
 * Adds the current question and answer to conversation history.
 * This history will be used in future questions for context.
 *
 * Note: In a production system, this would persist to a database or session store.
 */
private fun AIAgentSubgraphBuilderBase<AnswerGenerationResult, CodeQAResponse>.nodeUpdateConversationHistory() =
    node<CodeQAResponse, CodeQAResponse>("update-conversation-history") { response ->
        val question = storage.get(questionKey)!!
        val currentHistory = storage.get(conversationHistoryKey) ?: emptyList()
        val maxHistoryLength = storage.get(maxHistoryLengthKey) ?: 10

        logger.info("Updating conversation history (current size: ${currentHistory.size})")

        // Convert code references for history
        val codeReferencesForHistory = response.codeReferences

        // Create new messages
        val userMessage = CodeQAMessage(
            role = MessageRole.USER,
            content = question,
            timestamp = System.currentTimeMillis(),
            codeReferences = emptyList()
        )

        val assistantMessage = CodeQAMessage(
            role = MessageRole.ASSISTANT,
            content = response.answer,
            timestamp = System.currentTimeMillis(),
            codeReferences = codeReferencesForHistory
        )

        // Add to history and limit to maxHistoryLength
        val updatedHistory = (currentHistory + listOf(userMessage, assistantMessage))
            .takeLast(maxHistoryLength * 2) // Each Q&A is 2 messages

        logger.info("Conversation history updated (new size: ${updatedHistory.size})")

        // In a production system, we would persist this history
        // For now, it's only available within the agent session
        storage.set(conversationHistoryKey, updatedHistory)

        logger.info("Response formatting completed successfully")

        response
    }

/**
 * Helper: Convert internal CodeReferenceInternal to public CodeReference
 */
private fun CodeReferenceInternal.toPublicReference(): CodeReference =
    CodeReference(
        filePath = filePath,
        lineStart = lineStart,
        lineEnd = lineEnd,
        codeSnippet = codeSnippet
    )
