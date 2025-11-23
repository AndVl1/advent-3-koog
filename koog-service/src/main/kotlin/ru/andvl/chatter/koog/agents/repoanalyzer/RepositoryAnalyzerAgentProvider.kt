package ru.andvl.chatter.koog.agents.repoanalyzer

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.repoanalyzer.nodes.nodeResponseBuilder
import ru.andvl.chatter.koog.agents.repoanalyzer.subgraphs.*
import ru.andvl.chatter.koog.model.repoanalyzer.StructureResult
import ru.andvl.chatter.shared.models.codeagent.AnalysisType
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisRequest
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisResult

private val logger = LoggerFactory.getLogger("repository-analyzer-agent")

/**
 * Repository Analyzer Agent Provider
 *
 * Main entry point for the repository analysis agent.
 *
 * This agent analyzes GitHub repositories and provides:
 * - File structure analysis
 * - Language detection
 * - Dependency analysis
 * - Build tool detection
 * - Framework detection
 * - Repository summary
 *
 * Flow:
 * 1. Input Validation - validate GitHub URL and extract owner/repo
 * 2. Repository Setup - clone or reuse existing repository
 * 3. Structure Analysis - analyze file tree and languages
 * 4. Dependency Analysis (optional, based on analysisType) - detect build tools and dependencies
 * 5. Summary Generation (optional, based on analysisType) - generate repository summary
 * 6. Response Builder - assemble final result
 *
 * The agent supports three analysis types:
 * - STRUCTURE: File structure and languages only (fast)
 * - DEPENDENCIES: Structure + dependencies and build tools (moderate)
 * - FULL: Complete analysis including summary (comprehensive)
 *
 * @return Repository analysis result with all collected data
 */
suspend fun getRepositoryAnalyzerStrategy(): AIAgentGraphStrategy<RepositoryAnalysisRequest, RepositoryAnalysisResult> =
    strategy("repository-analyzer-agent") {
        logger.info("Building Repository Analyzer Agent strategy")

        // Subgraphs
        val subgraphInputValidation by subgraphInputValidation()
        val subgraphRepositorySetup by subgraphRepositorySetup()
        val subgraphStructureAnalysis by subgraphStructureAnalysis()
        val subgraphDependencyAnalysis by subgraphDependencyAnalysis()
        val subgraphSummaryGeneration by subgraphSummaryGeneration()

        // Final node
        val nodeResponseBuilder by nodeResponseBuilder()

        // Linear flow: all steps execute sequentially
        // Note: Dependency analysis and summary generation always run
        // Future optimization: add conditional edges to skip steps based on analysisType
        nodeStart then
                subgraphInputValidation then
                subgraphRepositorySetup then
                subgraphStructureAnalysis then
                subgraphDependencyAnalysis then
                subgraphSummaryGeneration then
                nodeResponseBuilder then
                nodeFinish

        logger.info("Repository Analyzer Agent strategy built successfully")
    }
