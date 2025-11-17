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
        user {
            text(request.message)
        }
    }
}

/**
 * Build conversation strategy
 *
 * Simple strategy with one node that generates text response.
 * Can be extended in the future with additional nodes for:
 * - Intent analysis
 * - Multi-turn reasoning
 * - Tool usage
 * - etc.
 */
internal fun getConversationAgentStrategy(
    mainSystemPrompt: String? = null,
): AIAgentGraphStrategy<ConversationRequest, ConversationAgentResponse> = strategy("conversation") {

    // Main subgraph - generates text response with history
    val answer: AIAgentSubgraph<ConversationRequest, ConversationAgentResponse> by conversationAnswerSubgraph(mainSystemPrompt)

    // Optional: compress history if it's too long
    val tldrHistory by nodeLLMCompressHistory<ConversationRequest>("tldr")
    val printCompressedHistory by nodeLLMCPrintCompressedHistory<ConversationRequest>("print_compressed")

    // Start with answer generation if history is short
    edge(nodeStart forwardTo answer onCondition { !isHistoryTooLong() })

    // Compress history first if it's too long
    edge(nodeStart forwardTo tldrHistory onCondition { isHistoryTooLong() })
    edge(tldrHistory forwardTo printCompressedHistory)
    edge(printCompressedHistory forwardTo answer)

    // Finish with the answer
    edge(answer forwardTo nodeFinish)
}
