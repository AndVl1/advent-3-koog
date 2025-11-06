package ru.andvl.chatter.koog.agents.memory

import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.extension.dropTrailingToolCalls
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.feature.withMemory
import ai.koog.agents.memory.model.*
import ai.koog.agents.memory.prompts.MemoryPrompts
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.json.JsonStructuredData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

private const val HistoryWrapperTag = "conversation_to_extract_facts"
private val logger = KotlinLogging.logger { }

@OptIn(InternalAgentsApi::class)
internal inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeSaveToMemoryFromLastMessage(
    name: String? = null,
    subject: MemorySubject,
    scope: MemoryScopeType,
    concepts: List<Concept>,
    retrievalModel: LLModel? = null
) : AIAgentNodeDelegate<T, T> = node(name) { input ->
    withMemory {
        concepts.forEach { concept ->
            saveFactsFromLastMessage(
                llm = llm,
                concept = concept,
                subject = subject,
                scope = scopesProfile.getScope(scope) ?: return@forEach,
                retrievalModel = retrievalModel
            )
        }
    }

    input
}

@OptIn(InternalAgentsApi::class)
internal suspend fun AgentMemory.saveFactsFromLastMessage(
    llm: AIAgentLLMContext,
    concept: Concept,
    subject: MemorySubject,
    scope: MemoryScope,
    retrievalModel: LLModel? = null
) {
    llm.writeSession {
        val initialModel = model
        if (retrievalModel != null) {
            model = retrievalModel
            logger.info { "Using model: ${retrievalModel.id}" }
        }
        val facts = retrieveFactsFromLastMessage(concept)

        // Save facts to memory
        agentMemory.save(facts, subject, scope)
        logger.info { "Saved fact for concept '${concept.keyword}' in scope $scope: $facts" }
        if (retrievalModel != null) {
            model = initialModel
            logger.info { "Switching back to model: ${initialModel.id}" }
        }
    }
}

@OptIn(InternalAgentsApi::class)
internal suspend fun AIAgentLLMWriteSession.retrieveFactsFromLastMessage(
    concept: Concept,
    clock: Clock = Clock.System,
): Fact {
    @Serializable
    @LLMDescription("Fact text")
    data class FactStructure(
        val fact: String
    )

    @Serializable
    @LLMDescription("Facts list")
    data class FactListStructure(
        val facts: List<FactStructure>
    )

    // Add a message asking to retrieve facts about the concept
    val promptForCompression = when (concept.factType) {
        FactType.SINGLE -> MemoryPrompts.singleFactPrompt(concept)
        FactType.MULTIPLE -> MemoryPrompts.multipleFactsPrompt(concept)
    }

    // remove tailing tool calls as we didn't provide any result for them
    dropTrailingToolCalls()

    val oldPrompt = this.prompt

    rewritePrompt {
        // Combine all history into one message with XML tags
        // to prevent LLM from continuing answering in a tool_call -> tool_result pattern
        val combinedMessage = buildString {
            append("<${HistoryWrapperTag}>\n")
            oldPrompt.messages.last().let { message ->
                when (message) {
                    is Message.System -> append("<user>\n${message.content}\n</user>\n")
                    is Message.User -> append("<user>\n${message.content}\n</user>\n")
                    is Message.Assistant -> append("<assistant>\n${message.content}\n</assistant>\n")
                    is Message.Tool.Call -> append(
                        "<tool_call tool=${message.tool}>\n${message.content}\n</tool_call>\n"
                    )
                    is Message.Tool.Result -> append(
                        "<tool_result tool=${message.tool}>\n${message.content}\n</tool_result>\n"
                    )
                }
            }
            append("</${HistoryWrapperTag}>\n")
        }

        // Put Compression prompt as a System instruction
        val newPrompt = Prompt.build(id = oldPrompt.id) {
            system(promptForCompression)
            user(combinedMessage)
        }

        return@rewritePrompt newPrompt
    }

    val timestamp = clock.now().toEpochMilliseconds()

    val facts = when (concept.factType) {
        FactType.SINGLE -> {
            val response = requestLLMStructured(
                config = StructuredOutputConfig(default = StructuredOutput.Manual(JsonStructuredData.createJsonStructure<FactStructure>()))
            )

            SingleFact(
                concept = concept,
                value = response.getOrNull()?.structure?.fact ?: "No facts extracted",
                timestamp = timestamp
            )
        }

        FactType.MULTIPLE -> {
            val response = requestLLMStructured(
                config = StructuredOutputConfig(default = StructuredOutput.Manual(JsonStructuredData.createJsonStructure<FactListStructure>()))
            )
            val factsList = response.getOrNull()?.structure?.facts ?: emptyList()
            MultipleFacts(concept = concept, values = factsList.map { it.fact }, timestamp = timestamp)
        }
    }

    logger.info { "ABCQWE $facts" }

    // Restore the original prompt
    rewritePrompt { oldPrompt }

    return facts
}
