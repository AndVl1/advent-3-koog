package ru.andvl.chatter.koog.agents.codeqa

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.llm.LLModel
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codeqa.subgraphs.*
import ru.andvl.chatter.shared.models.codeagent.CodeQARequest
import ru.andvl.chatter.shared.models.codeagent.CodeQAResponse

private val logger = LoggerFactory.getLogger("code-qa-agent")

/**
 * Code QA Agent Provider
 *
 * Main entry point for the Code QA (Question Answering) agent.
 *
 * This agent answers questions about code repositories by:
 * - Validating session and checking RAG availability
 * - Analyzing question intent and extracting search parameters
 * - Searching for relevant code using RAG and/or file operations
 * - Generating comprehensive answers with code references
 * - Formatting responses and maintaining conversation history
 *
 * Flow:
 * 1. Session Validation - validate session ID and check RAG availability
 * 2. Question Analysis - analyze question intent and extract search query
 * 3. Code Search - search for relevant code (RAG + file operations)
 * 4. Answer Generation - generate comprehensive answer with LLM
 * 5. Response Formatting - format response and update conversation history
 *
 * The agent supports:
 * - Semantic code search via RAG (when available)
 * - File-based search as fallback
 * - Conversation history for context
 * - Confidence scoring
 * - Follow-up suggestions
 * - Code modification detection
 *
 * @param model LLM model to use for question analysis and answer generation
 * @return AIAgentGraphStrategy configured for Code QA
 */
suspend fun getCodeQaStrategy(model: LLModel): AIAgentGraphStrategy<CodeQARequest, CodeQAResponse> =
    strategy("code-qa-agent") {
        logger.info("Building Code QA Agent strategy")

        // Define all 5 subgraphs
        val subgraphSessionValidation by subgraphSessionValidation()
        val subgraphQuestionAnalysis by subgraphQuestionAnalysis(model)
        val subgraphCodeSearch by subgraphCodeSearch()
        val subgraphAnswerGeneration by subgraphAnswerGeneration(model)
        val subgraphResponseFormatting by subgraphResponseFormatting()

        // Linear flow: all subgraphs execute sequentially
        nodeStart then
                subgraphSessionValidation then
                subgraphQuestionAnalysis then
                subgraphCodeSearch then
                subgraphAnswerGeneration then
                subgraphResponseFormatting then
                nodeFinish

        logger.info("Code QA Agent strategy built successfully")
    }
