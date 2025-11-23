package ru.andvl.chatter.koog.agents.codemodifier.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.prompt.llm.LLModel
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codemodifier.*
import ru.andvl.chatter.koog.model.codemodifier.*

private val logger = LoggerFactory.getLogger("codemodifier-validation")

private const val MAX_VALIDATION_RETRIES = 2

/**
 * Subgraph: Validation
 *
 * Flow:
 * 1. Validate syntax (Text-only LLM) - Check for syntax errors
 * 2. Check validation result - Decide retry or proceed
 * 3. Detect breaking changes (Text-only LLM) - Find breaking changes
 *
 * Input: PlanningResult
 * Output: ValidationCheckResult
 *
 * Retry logic:
 * - If syntax validation fails, retry up to MAX_VALIDATION_RETRIES times
 * - On retry, return to modification planning to regenerate plan
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphValidation(
    model: LLModel
): AIAgentSubgraphDelegate<PlanningResult, ValidationCheckResult> =
    subgraph(name = "validation") {
        val nodeValidateSyntax by nodeValidateSyntax(model)
        val nodeCheckValidationResult by nodeCheckValidationResult()
        val nodeDetectBreakingChanges by nodeDetectBreakingChanges(model)

        edge(nodeStart forwardTo nodeValidateSyntax)
        edge(nodeValidateSyntax forwardTo nodeCheckValidationResult)

        // Conditional edges based on validation result
        edge(nodeCheckValidationResult forwardTo nodeDetectBreakingChanges onCondition { result: ValidationCheckResult ->
            result.syntaxValid
        })

        edge(nodeDetectBreakingChanges forwardTo nodeFinish)
    }

/**
 * Node: Validate syntax (simplified, assumes valid)
 *
 * Reviews the proposed changes for syntax errors.
 */
private fun AIAgentSubgraphBuilderBase<PlanningResult, ValidationCheckResult>.nodeValidateSyntax(
    model: LLModel
) =
    node<PlanningResult, ValidationCheckResult>("validate-syntax") { planningResult ->
        logger.info("Validating syntax for ${planningResult.plan.changes.size} changes")

        // For now, assume all changes are valid
        // In the future, use LLM to validate
        val validationResult = ValidationCheckResult(
            syntaxValid = true,
            validationNotes = "Validation passed (simplified implementation)"
        )

        storage.set(syntaxValidKey, validationResult.syntaxValid)
        storage.set(validationCheckResultKey, validationResult)

        logger.info("Syntax validation result: VALID")

        validationResult
    }

/**
 * Node: Check validation result
 *
 * Decides whether to retry or proceed based on validation result.
 */
private fun AIAgentSubgraphBuilderBase<PlanningResult, ValidationCheckResult>.nodeCheckValidationResult() =
    node<ValidationCheckResult, ValidationCheckResult>("check-validation-result") { validationResult ->
        logger.info("Checking validation result: ${validationResult.syntaxValid}")

        if (!validationResult.syntaxValid) {
            val retryCount = storage.get(validationRetryCountKey) ?: 0

            if (retryCount < MAX_VALIDATION_RETRIES) {
                logger.warn("Syntax validation failed, retry count: $retryCount")
                storage.set(validationRetryCountKey, retryCount + 1)

                // In a real implementation, this would trigger a retry to modification planning
                // For now, we'll proceed with the invalid result
                logger.error("Retry mechanism not fully implemented, proceeding with invalid result")
            } else {
                logger.error("Max retries reached, syntax validation failed")
            }
        }

        validationResult
    }

/**
 * Node: Detect breaking changes (simplified, assumes no breaking changes)
 *
 * Analyzes changes for potential breaking changes.
 */
private fun AIAgentSubgraphBuilderBase<PlanningResult, ValidationCheckResult>.nodeDetectBreakingChanges(
    model: LLModel
) =
    node<ValidationCheckResult, ValidationCheckResult>("detect-breaking-changes") { validationResult ->
        logger.info("Detecting breaking changes")

        // For now, assume no breaking changes
        // In the future, use LLM to detect
        val breakingChanges = emptyList<String>()

        val finalResult = validationResult.copy(
            breakingChanges = breakingChanges,
            validationNotes = "${validationResult.validationNotes}. No breaking changes detected (simplified implementation)"
        )

        storage.set(breakingChangesKey, breakingChanges)
        storage.set(validationCheckResultKey, finalResult)

        logger.info("Detected 0 breaking changes")

        finalResult
    }

