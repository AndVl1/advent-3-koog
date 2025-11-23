package ru.andvl.chatter.koog.agents.repoanalyzer.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.repoanalyzer.*
import ru.andvl.chatter.koog.model.repoanalyzer.DependencyResult
import ru.andvl.chatter.koog.model.repoanalyzer.SummaryResult

private val logger = LoggerFactory.getLogger("repoanalyzer-summary")

/**
 * Subgraph: Summary Generation
 *
 * Generates a rule-based summary of the repository without using LLM.
 *
 * Flow:
 * 1. Generate summary from collected data
 *
 * Input: DependencyResult
 * Output: SummaryResult (contains summary, keyFeatures, architectureNotes)
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphSummaryGeneration():
        AIAgentSubgraphDelegate<DependencyResult, SummaryResult> =
    subgraph(name = "summary-generation") {
        val nodeGenerateSummary by nodeGenerateSummary()

        edge(nodeStart forwardTo nodeGenerateSummary)
        edge(nodeGenerateSummary forwardTo nodeFinish)
    }

/**
 * Node: Generate summary (rule-based, without LLM)
 *
 * Creates a structured summary from analysis results using simple rules.
 */
private fun AIAgentSubgraphBuilderBase<DependencyResult, SummaryResult>.nodeGenerateSummary() =
    node<DependencyResult, SummaryResult>("generate-summary") { dependencyResult ->
        logger.info("Generating repository summary")

        // Retrieve data from storage
        val structureResult = storage.get(structureResultKey)
            ?: throw IllegalStateException("Structure result not found in storage")

        val owner = storage.get(ownerKey) ?: "unknown"
        val repo = storage.get(repoKey) ?: "unknown"

        // Generate summary components
        val summary = buildSummary(owner, repo, structureResult, dependencyResult)
        val keyFeatures = extractKeyFeatures(structureResult, dependencyResult)
        val architectureNotes = buildArchitectureNotes(dependencyResult)

        logger.info("Summary generated successfully")
        logger.debug("Summary: $summary")
        logger.debug("Key features: $keyFeatures")

        val summaryResult = SummaryResult(
            summary = summary,
            keyFeatures = keyFeatures,
            architectureNotes = architectureNotes
        )

        storage.set(summaryResultKey, summaryResult)
        storage.set(summaryKey, summary)

        summaryResult
    }

/**
 * Build main summary text
 */
private fun buildSummary(
    owner: String,
    repo: String,
    structureResult: ru.andvl.chatter.koog.model.repoanalyzer.StructureResult,
    dependencyResult: DependencyResult
): String {
    val mainLanguage = structureResult.languages
        .maxByOrNull { it.value }
        ?.key ?: "Unknown"

    val languagesList = structureResult.languages.keys
        .take(3)
        .joinToString(", ")

    val buildTool = dependencyResult.buildTools.firstOrNull() ?: "Unknown"

    val frameworks = if (dependencyResult.frameworks.isNotEmpty()) {
        "using ${dependencyResult.frameworks.take(3).joinToString(", ")}"
    } else {
        "with no major frameworks detected"
    }

    return """
        Repository: $owner/$repo

        This is a $mainLanguage project with ${structureResult.totalFiles} files and ${structureResult.totalLines} lines of code.

        Languages used: $languagesList
        Build tool: $buildTool
        Frameworks: $frameworks

        The repository contains ${dependencyResult.dependencies.size} dependencies.
    """.trimIndent()
}

/**
 * Extract key features from analysis
 */
private fun extractKeyFeatures(
    structureResult: ru.andvl.chatter.koog.model.repoanalyzer.StructureResult,
    dependencyResult: DependencyResult
): List<String> {
    val features = mutableListOf<String>()

    // Add primary language
    val mainLanguage = structureResult.languages.maxByOrNull { it.value }?.key
    if (mainLanguage != null) {
        features.add("Primary language: $mainLanguage")
    }

    // Add multi-language support if applicable
    if (structureResult.languages.size > 3) {
        features.add("Multi-language codebase (${structureResult.languages.size} languages)")
    }

    // Add build tool
    dependencyResult.buildTools.forEach { tool ->
        features.add("Build system: $tool")
    }

    // Add major frameworks
    dependencyResult.frameworks.take(3).forEach { framework ->
        features.add("Framework: $framework")
    }

    // Add code size category
    when {
        structureResult.totalLines > 50000 -> features.add("Large codebase (50k+ lines)")
        structureResult.totalLines > 10000 -> features.add("Medium codebase (10k-50k lines)")
        else -> features.add("Small codebase (<10k lines)")
    }

    // Add dependency info
    when {
        dependencyResult.dependencies.size > 50 -> features.add("Heavy dependency usage (50+ dependencies)")
        dependencyResult.dependencies.size > 20 -> features.add("Moderate dependency usage (20-50 dependencies)")
        dependencyResult.dependencies.size > 0 -> features.add("Light dependency usage (<20 dependencies)")
    }

    return features
}

/**
 * Build architecture notes from detected tools and frameworks
 */
private fun buildArchitectureNotes(dependencyResult: DependencyResult): String {
    val notes = mutableListOf<String>()

    // Build tool notes
    if (dependencyResult.buildTools.isNotEmpty()) {
        notes.add("Build Tools:")
        dependencyResult.buildTools.forEach { tool ->
            notes.add("  - $tool")
        }
    }

    // Framework notes
    if (dependencyResult.frameworks.isNotEmpty()) {
        notes.add("\nFrameworks & Libraries:")
        dependencyResult.frameworks.forEach { framework ->
            notes.add("  - $framework")
        }
    }

    // Add architecture patterns based on frameworks
    val patterns = detectArchitecturePatterns(dependencyResult.frameworks)
    if (patterns.isNotEmpty()) {
        notes.add("\nDetected Patterns:")
        patterns.forEach { pattern ->
            notes.add("  - $pattern")
        }
    }

    return if (notes.isNotEmpty()) {
        notes.joinToString("\n")
    } else {
        "No specific architecture patterns detected."
    }
}

/**
 * Detect architecture patterns from frameworks
 */
private fun detectArchitecturePatterns(frameworks: List<String>): List<String> {
    val patterns = mutableListOf<String>()

    val frameworkSet = frameworks.map { it.lowercase() }.toSet()

    // Backend patterns
    when {
        frameworkSet.any { it.contains("spring") } -> patterns.add("Spring-based backend (likely MVC or WebFlux)")
        frameworkSet.any { it.contains("ktor") } -> patterns.add("Ktor backend (Kotlin async)")
        frameworkSet.any { it.contains("express") } -> patterns.add("Express.js backend (Node.js)")
        frameworkSet.any { it.contains("django") } -> patterns.add("Django backend (Python MVC)")
        frameworkSet.any { it.contains("flask") } -> patterns.add("Flask backend (Python micro-framework)")
        frameworkSet.any { it.contains("fastapi") } -> patterns.add("FastAPI backend (Python async)")
        frameworkSet.any { it.contains("rails") } -> patterns.add("Ruby on Rails (Ruby MVC)")
        frameworkSet.any { it.contains("laravel") } -> patterns.add("Laravel (PHP MVC)")
        frameworkSet.any { it.contains("asp.net") } -> patterns.add("ASP.NET (C# backend)")
    }

    // Frontend patterns
    when {
        frameworkSet.any { it.contains("react") } -> patterns.add("React frontend (component-based UI)")
        frameworkSet.any { it.contains("vue") } -> patterns.add("Vue.js frontend (progressive framework)")
        frameworkSet.any { it.contains("angular") } -> patterns.add("Angular frontend (TypeScript SPA)")
    }

    // Architecture styles
    if (frameworkSet.any { it.contains("spring boot") } || frameworkSet.any { it.contains("fastapi") }) {
        patterns.add("RESTful API architecture")
    }

    return patterns
}
