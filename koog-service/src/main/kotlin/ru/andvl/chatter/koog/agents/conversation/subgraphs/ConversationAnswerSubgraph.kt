package ru.andvl.chatter.koog.agents.conversation.subgraphs

import ai.koog.agents.core.dsl.builder.*
import ai.koog.prompt.message.Message
import ru.andvl.chatter.koog.agents.conversation.ConversationAgentResponse
import ru.andvl.chatter.koog.agents.conversation.getConversationAgentPrompt
import ru.andvl.chatter.koog.model.conversation.ConversationRequest

/**
 * Subgraph for generating conversation answer
 */
internal fun AIAgentGraphStrategyBuilder<ConversationRequest, ConversationAgentResponse>.conversationAnswerSubgraph(
    systemPrompt: String? = null
): AIAgentSubgraphDelegate<ConversationRequest, ConversationAgentResponse> =
    subgraph<ConversationRequest, ConversationAgentResponse>("conversation_answer") {
        val generateAnswer by conversationAnswerNode(systemPrompt)
        edge(nodeStart forwardTo generateAnswer)
        edge(generateAnswer forwardTo nodeFinish)
    }

/**
 * Node for generating text answer
 */
internal fun AIAgentSubgraphBuilderBase<ConversationRequest, ConversationAgentResponse>.conversationAnswerNode(
    systemPrompt: String?
): AIAgentNodeDelegate<ConversationRequest, ConversationAgentResponse> {
    val baseSystemPrompt = systemPrompt ?: buildDefaultSystemPrompt()

    return node<ConversationRequest, ConversationAgentResponse>("generate_text_answer") { request ->
        llm.writeSession {
            appendPrompt {
                prompt = getConversationAgentPrompt(
                    systemPrompt = baseSystemPrompt,
                    request = request,
                    temperature = 0.7
                )
            }
            requestLLM()
        }.let { response ->
            // Convert Message.Response to Message.Assistant
            val assistantMessage = when (response) {
                is Message.Assistant -> response
                else -> Message.Assistant(
                    content = response.content,
                    metaInfo = response.metaInfo
                )
            }
            Result.success(assistantMessage)
        }
    }
}

/**
 * Build default system prompt for conversation
 */
private fun buildDefaultSystemPrompt(): String {
    return """
        You are a helpful AI assistant engaged in a natural conversation with a user.

        Your role is to:
        - Provide clear, accurate, and helpful responses
        - Maintain conversation context across multiple turns
        - Be friendly and professional
        - Answer in the same language as the user's question
        - If you don't know something, admit it honestly

        Guidelines:
        - Keep responses concise but informative
        - Use natural, conversational language
        - Remember and reference previous messages when relevant
        - Ask clarifying questions if needed
    """.trimIndent()
}
