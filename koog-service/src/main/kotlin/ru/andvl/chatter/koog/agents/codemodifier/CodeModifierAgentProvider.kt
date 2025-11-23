package ru.andvl.chatter.koog.agents.codemodifier

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.llm.LLModel
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codemodifier.subgraphs.*
import ru.andvl.chatter.koog.model.codemodifier.CodeModificationRequest
import ru.andvl.chatter.koog.model.codemodifier.CodeModificationResult

private val logger = LoggerFactory.getLogger("code-modifier-agent")

/**
 * Code Modifier Agent Provider
 *
 * Main entry point for the code modification agent.
 *
 * This agent generates code modification proposals based on user instructions:
 * - Validates session and file scope
 * - Analyzes code context using RAG and file operations
 * - Generates structured modification plan
 * - Validates syntax and detects breaking changes
 * - Optionally validates with Docker (build + tests)
 * - Returns detailed modification proposals (does NOT apply changes)
 *
 * Flow:
 * 1. Request Validation - validate session and normalize file scope
 * 2. Code Analysis - search relevant files, analyze context, detect patterns
 * 3. Modification Planning - generate modification plan with structured LLM output
 * 4. Validation - validate syntax and detect breaking changes (with retry logic)
 * 5. Docker Validation - optional Docker-based validation (build + tests)
 * 6. Response Building - assemble final result
 *
 * Safety features:
 * - File scope validation (max 20 files)
 * - Change limit (max 50 changes)
 * - Syntax validation with retry (max 2 retries)
 * - Breaking change detection
 * - Optional Docker validation (skipped if Docker not available)
 * - No automatic application of changes
 *
 * @param model LLM model to use for code analysis and planning
 * @return Code modification result with proposed changes and metadata
 */
suspend fun getCodeModifierStrategy(model: LLModel): AIAgentGraphStrategy<CodeModificationRequest, CodeModificationResult> =
    strategy("code-modifier-agent") {
        logger.info("Building Code Modifier Agent strategy")

        // Subgraphs
        val subgraphRequestValidation by subgraphRequestValidation()
        val subgraphCodeAnalysis by subgraphCodeAnalysis(model)
        val subgraphModificationPlanning by subgraphModificationPlanning(model)
        val subgraphValidation by subgraphValidation(model)
        val subgraphDockerValidation by subgraphDockerValidation(model)
        val subgraphResponseBuilding by subgraphResponseBuilding()

        // Linear flow through all subgraphs
        nodeStart then
                subgraphRequestValidation then
                subgraphCodeAnalysis then
                subgraphModificationPlanning then
                subgraphValidation then
                subgraphDockerValidation then
                subgraphResponseBuilding then
                nodeFinish

        logger.info("Code Modifier Agent strategy built successfully")
    }
