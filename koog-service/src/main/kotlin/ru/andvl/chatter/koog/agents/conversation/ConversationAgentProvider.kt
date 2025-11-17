package ru.andvl.chatter.koog.agents.conversation

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ru.andvl.chatter.koog.agents.conversation.subgraphs.conversationAnswerSubgraph
import ru.andvl.chatter.koog.agents.conversation.subgraphs.nodeTranscribeAudio
import ru.andvl.chatter.koog.agents.utils.isHistoryTooLong
import ru.andvl.chatter.koog.agents.utils.nodeLLMCPrintCompressedHistory
import ru.andvl.chatter.koog.model.conversation.ConversationRequest

/**
 * Response type for conversation agent
 */
internal typealias ConversationAgentResponse = Result<Message.Assistant>

/**
 * Build prompt for conversation agent
 */
internal fun getConversationAgentPrompt(
    systemPrompt: String,
    request: ConversationRequest,
    temperature: Double? = null,
): Prompt {
    return prompt(
        Prompt(
            emptyList(),
            "conversation",
            params = LLMParams(
                temperature = temperature
            )
        )
    ) {
        system {
            text(systemPrompt)
        }
        messages(request.history)
        if (request.audioFilePath == null) {
            user {
                text(request.message)
            }
        }
    }
}

/**
 * Build conversation strategy
 *
 * Strategy flow:
 * 1. Transcribe audio if present (transcribeAudio node - passes through if no audio)
 * 2. If history too long → compress it (tldrHistory)
 * 3. Generate answer (answer subgraph)
 *
 * Audio transcription:
 * - Always runs first but quickly passes through if audioFilePath is null
 * - If audio present: transcribes to text and updates request.message, clears audioFilePath
 * - After transcription, request is treated as text message
 */
internal fun getConversationAgentStrategy(
    mainSystemPrompt: String? = null,
): AIAgentGraphStrategy<ConversationRequest, ConversationAgentResponse> = strategy("conversation") {

    // Audio transcription node - always runs, but passes through if no audio
    val transcribeAudio by nodeTranscribeAudio<ConversationRequest>("transcribe_audio")

    // Main answer subgraph - generates text response
    val answer: AIAgentSubgraph<ConversationRequest, ConversationAgentResponse> by conversationAnswerSubgraph(mainSystemPrompt)

    // History compression (if needed)
    val tldrHistory by nodeLLMCompressHistory<ConversationRequest>("tldr")
    val printCompressedHistory by nodeLLMCPrintCompressedHistory<ConversationRequest>("print_compressed")

    // === FLOW ===
    // 1. Always transcribe audio first (passes through if no audio)
    edge(nodeStart forwardTo transcribeAudio)

    // 2. After transcription → check history length
    edge(transcribeAudio forwardTo answer onCondition { !isHistoryTooLong() })
    edge(transcribeAudio forwardTo tldrHistory onCondition { isHistoryTooLong() })

    // 3. History compression → answer
    edge(tldrHistory forwardTo printCompressedHistory)
    edge(printCompressedHistory forwardTo answer)

    // 4. Answer → finish
    edge(answer forwardTo nodeFinish)
}
