package ru.andvl.chatter.koog.agents.repoanalyzer.nodes

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.repoanalyzer.*
import ru.andvl.chatter.koog.model.repoanalyzer.SummaryResult
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisResult

private val logger = LoggerFactory.getLogger("repoanalyzer-response")

/**
 * Node: Response Builder
 *
 * Assembles the final RepositoryAnalysisResult from all collected data in storage.
 *
 * This node collects data from:
 * - ValidatedRequest (owner, repo, github_url)
 * - SetupResult (repository_path)
 * - StructureResult (file_tree, languages, totalFiles, totalLines)
 * - DependencyResult (buildTools, dependencies, frameworks) - optional
 * - SummaryResult (summary) - optional
 *
 * Input: SummaryResult
 * Output: RepositoryAnalysisResult
 */
internal fun AIAgentGraphStrategyBuilder<*, RepositoryAnalysisResult>.nodeResponseBuilder() =
    node<SummaryResult, RepositoryAnalysisResult>("response-builder") { summaryResult ->
        logger.info("Building final repository analysis result")

        // Required data
        val validatedRequest = storage.get(validatedRequestKey)
            ?: throw IllegalStateException("ValidatedRequest not found in storage")

        val setupResult = storage.get(setupResultKey)
            ?: throw IllegalStateException("SetupResult not found in storage")

        val structureResult = storage.get(structureResultKey)
            ?: throw IllegalStateException("StructureResult not found in storage")

        // Optional data (may not be present for STRUCTURE-only analysis)
        val dependencyResult = storage.get(dependencyResultKey)
        val summary = storage.get(summaryKey) ?: "Repository analyzed successfully."

        // Extract main languages (sorted by usage count)
        val mainLanguages = structureResult.languages
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        // Extract dependencies list
        val dependencies = dependencyResult?.dependencies?.map { dep ->
            if (dep.version != null) {
                "${dep.name}:${dep.version}"
            } else {
                dep.name
            }
        } ?: emptyList()

        // Get build tool
        val buildTool = dependencyResult?.buildTools?.firstOrNull()

        logger.info("Assembling result for repository: ${validatedRequest.owner}/${validatedRequest.repo}")
        logger.info("  - Path: ${setupResult.repositoryPath}")
        logger.info("  - Files: ${structureResult.totalFiles}")
        logger.info("  - Languages: ${mainLanguages.joinToString(", ")}")
        logger.info("  - Dependencies: ${dependencies.size}")
        logger.info("  - Build tool: $buildTool")

        val result = RepositoryAnalysisResult(
            repositoryPath = setupResult.repositoryPath,
            repositoryName = "${validatedRequest.owner}/${validatedRequest.repo}",
            summary = summary,
            fileCount = structureResult.totalFiles,
            mainLanguages = mainLanguages,
            structureTree = structureResult.fileTree,
            dependencies = dependencies,
            buildTool = buildTool,
            errorMessage = null
        )

        logger.info("Repository analysis result built successfully")

        result
    }
