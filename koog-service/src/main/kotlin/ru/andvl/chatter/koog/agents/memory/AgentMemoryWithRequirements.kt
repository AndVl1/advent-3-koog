package ru.andvl.chatter.koog.agents.memory

import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.feature.withMemory
import ai.koog.agents.memory.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import ru.andvl.chatter.koog.model.tool.InitialPromptAnalysisModel

private const val HistoryWrapperTag = "conversation_to_extract_facts"
private val logger = KotlinLogging.logger {}

internal fun getGeneralConditionsConcept(doc: String?) : Concept {
    return Concept(
        keyword = "general-conditions-$doc",
        description = "General conditions from requirements document for Google Doc $doc",
        factType = FactType.SINGLE
    )
}

internal fun getImportantConstraintsConcept(doc: String?) : Concept {
    return Concept(
        keyword = "important-constraints-$doc",
        description = "Important constraints from requirements document for Google Doc $doc",
        factType = FactType.MULTIPLE
    )
}

internal fun getAdditionalAdvantagesConcept(doc: String?) : Concept {
    return Concept(
        keyword = "additional-advantages-$doc",
        description = "Additional advantages from requirements document for Google Doc $doc",
        factType = FactType.MULTIPLE
    )
}

internal fun getAttentionPointsConcept(doc: String?) : Concept {
    return Concept(
        keyword = "attention-points-$doc",
        description = "Attention points from requirements document for Google Doc $doc",
        factType = FactType.MULTIPLE
    )
}

@OptIn(InternalAgentsApi::class)
internal suspend fun AgentMemory.saveRequirementsFromModel(
    llm: AIAgentLLMContext,
    subject: MemorySubject,
    scope: MemoryScope,
    model: InitialPromptAnalysisModel.SuccessAnalysisModel
) {
    model.requirements?.let { requirements ->
        logger.info { "Saving requirements to memory: ${requirements.generalConditions}" }

        val timestamp = Clock.System.now().toEpochMilliseconds()

        // Save general conditions
        val generalConditionsFact = SingleFact(
            concept = getGeneralConditionsConcept(model.googleDocsUrl),
            value = requirements.generalConditions,
            timestamp = timestamp
        )

        // Save constraints
        val constraintsFact = MultipleFacts(
            concept = getImportantConstraintsConcept(model.googleDocsUrl),
            values = requirements.importantConstraints,
            timestamp = timestamp
        )

        // Save advantages
        val advantagesFact = MultipleFacts(
            concept = getAdditionalAdvantagesConcept(model.googleDocsUrl),
            values = requirements.additionalAdvantages,
            timestamp = timestamp
        )

        // Save attention points
        val attentionPointsFact = MultipleFacts(
            concept = getAttentionPointsConcept(model.googleDocsUrl),
            values = requirements.attentionPoints,
            timestamp = timestamp
        )

        // Save all facts to memory
        listOf(generalConditionsFact, constraintsFact, advantagesFact, attentionPointsFact).forEach { fact ->
            agentMemory.save(fact, subject, scope)
            logger.info { "Saved fact: ${fact.concept.keyword}" }
        }
    } ?: run {
        logger.warn { "No requirements to save in model" }
    }
}

@OptIn(InternalAgentsApi::class)
internal fun AIAgentSubgraphBuilderBase<*, *>.nodeSaveRequirementsFromLastMessage(
    name: String? = null,
    subject: MemorySubject,
    scope: MemoryScopeType
) : AIAgentNodeDelegate<InitialPromptAnalysisModel.SuccessAnalysisModel, InitialPromptAnalysisModel.SuccessAnalysisModel> = node(name) { input ->
    // Try to extract requirements from the storage or directly from the last successful analysis
    withMemory {
        val memoryScope = scopesProfile.getScope(scope)
        if (memoryScope != null) {
            saveRequirementsFromModel(
                llm = llm,
                subject = subject,
                scope = memoryScope,
                model = input
            )
        }
    }

    input
}
