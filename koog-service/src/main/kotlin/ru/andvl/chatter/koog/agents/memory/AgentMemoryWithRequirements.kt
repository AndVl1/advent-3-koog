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
import ru.andvl.chatter.koog.model.tool.GithubRepositoryAnalysisModel
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

// GitHub Repository Analysis Concepts
internal fun getRepositoryUrlConcept(repoUrl: String) : Concept {
    // Normalize repo URL to use as key (remove https://, trailing slashes, etc.)
    val normalizedRepo = repoUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("github.com/")
        .removeSuffix("/")
        .replace("/", "-")

    return Concept(
        keyword = "repo-url-$normalizedRepo",
        description = "GitHub repository URL for $repoUrl",
        factType = FactType.SINGLE
    )
}

internal fun getRepositoryStructureConcept(repoUrl: String) : Concept {
    val normalizedRepo = repoUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("github.com/")
        .removeSuffix("/")
        .replace("/", "-")

    return Concept(
        keyword = "repo-structure-$normalizedRepo",
        description = "Repository structure and file organization for $repoUrl",
        factType = FactType.SINGLE
    )
}

internal fun getRepositoryDependenciesConcept(repoUrl: String) : Concept {
    val normalizedRepo = repoUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("github.com/")
        .removeSuffix("/")
        .replace("/", "-")

    return Concept(
        keyword = "repo-dependencies-$normalizedRepo",
        description = "Dependencies and build configuration for $repoUrl",
        factType = FactType.MULTIPLE
    )
}

internal fun getRepositoryKeyFindingsConcept(repoUrl: String) : Concept {
    val normalizedRepo = repoUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("github.com/")
        .removeSuffix("/")
        .replace("/", "-")

    return Concept(
        keyword = "repo-key-findings-$normalizedRepo",
        description = "Key findings from analysis of $repoUrl",
        factType = FactType.MULTIPLE
    )
}

internal fun getRepositoryAnalysisSummaryConcept(repoUrl: String) : Concept {
    val normalizedRepo = repoUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("github.com/")
        .removeSuffix("/")
        .replace("/", "-")

    return Concept(
        keyword = "repo-analysis-summary-$normalizedRepo",
        description = "Analysis summary for $repoUrl",
        factType = FactType.SINGLE
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

// GitHub Repository Analysis Memory Functions
@OptIn(InternalAgentsApi::class)
internal suspend fun AgentMemory.saveGithubAnalysisFromModel(
    llm: AIAgentLLMContext,
    subject: MemorySubject,
    scope: MemoryScope,
    model: GithubRepositoryAnalysisModel.SuccessAnalysisModel,
    repoUrl: String
) {
    logger.info { "Saving GitHub analysis to memory for repository: $repoUrl" }

    val timestamp = Clock.System.now().toEpochMilliseconds()

    // Save repository URL
    val repoUrlFact = SingleFact(
        concept = getRepositoryUrlConcept(repoUrl),
        value = repoUrl,
        timestamp = timestamp
    )

    // Save analysis summary (TL;DR)
    val analysisSummaryFact = SingleFact(
        concept = getRepositoryAnalysisSummaryConcept(repoUrl),
        value = model.shortSummary,
        timestamp = timestamp
    )

    // Extract and save key findings from repository review
    val keyFindings = mutableListOf<String>()

    model.repositoryReview?.let { review ->
        keyFindings.add("General: ${review.generalConditionsReview.commentType} - ${review.generalConditionsReview.comment}")

        review.constraintsReview.forEach { constraint ->
            keyFindings.add("Constraint [${constraint.commentType}]: ${constraint.comment}")
        }

        review.advantagesReview.forEach { advantage ->
            keyFindings.add("Advantage [${advantage.commentType}]: ${advantage.comment}")
        }

        review.attentionPointsReview.forEach { point ->
            keyFindings.add("Attention [${point.commentType}]: ${point.comment}")
        }
    }

    val keyFindingsFact = if (keyFindings.isNotEmpty()) {
        MultipleFacts(
            concept = getRepositoryKeyFindingsConcept(repoUrl),
            values = keyFindings,
            timestamp = timestamp
        )
    } else null

    // Save structure info if available (can be extracted from free form answer)
    // For now, we'll save the full analysis as structure
    val structureFact = SingleFact(
        concept = getRepositoryStructureConcept(repoUrl),
        value = model.freeFormAnswer.take(1000), // Limit to 1000 chars
        timestamp = timestamp
    )

    // Save all facts to memory
    listOfNotNull(repoUrlFact, analysisSummaryFact, keyFindingsFact, structureFact).forEach { fact ->
        agentMemory.save(fact, subject, scope)
        logger.info { "Saved fact: ${fact.concept.keyword}" }
    }

    logger.info { "Successfully saved GitHub analysis for: $repoUrl" }
}

@OptIn(InternalAgentsApi::class)
internal fun AIAgentSubgraphBuilderBase<*, *>.nodeSaveGithubAnalysisFromLastMessage(
    name: String? = null,
    subject: MemorySubject,
    scope: MemoryScopeType,
    repoUrl: String
) : AIAgentNodeDelegate<GithubRepositoryAnalysisModel.SuccessAnalysisModel, GithubRepositoryAnalysisModel.SuccessAnalysisModel> = node(name) { input ->
    // Save GitHub analysis results to memory
    withMemory {
        val memoryScope = scopesProfile.getScope(scope)
        if (memoryScope != null) {
            saveGithubAnalysisFromModel(
                llm = llm,
                subject = subject,
                scope = memoryScope,
                model = input,
                repoUrl = repoUrl
            )
        }
    }

    input
}
