package ru.andvl.chatter.koog.agents.codemodifier.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codemodifier.*
import ru.andvl.chatter.koog.model.codemodifier.*

private val logger = LoggerFactory.getLogger("codemodifier-response-building")

/**
 * Subgraph: Response Building
 *
 * Flow:
 * 1. Build response - Assemble final CodeModificationResult
 *
 * Input: ValidationCheckResult
 * Output: CodeModificationResult
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphResponseBuilding():
        AIAgentSubgraphDelegate<ValidationCheckResult, CodeModificationResult> =
    subgraph(name = "response-building") {
        val nodeBuildResponse by nodeBuildResponse()

        edge(nodeStart forwardTo nodeBuildResponse)
        edge(nodeBuildResponse forwardTo nodeFinish)
    }

/**
 * Node: Build response
 *
 * Assembles the final result with all metadata.
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, CodeModificationResult>.nodeBuildResponse() =
    node<ValidationCheckResult, CodeModificationResult>("build-response") { validationResult ->
        logger.info("Building final response")

        try {
            val plan = storage.get(modificationPlanKey)
            val syntaxValid = validationResult.syntaxValid
            val breakingChanges = validationResult.breakingChanges

            if (plan == null) {
                logger.error("No modification plan found in storage")
                return@node CodeModificationResult(
                    success = false,
                    errorMessage = "Failed to generate modification plan"
                )
            }

            // Calculate total files affected
            val totalFilesAffected = plan.changes.map { it.filePath }.distinct().size

            // Calculate complexity
            val complexity = calculateComplexity(
                changeCount = plan.changes.size,
                filesAffected = totalFilesAffected,
                hasBreakingChanges = breakingChanges.isNotEmpty(),
                estimatedComplexity = plan.estimatedComplexity
            )

            val result = CodeModificationResult(
                success = syntaxValid,
                modificationPlan = plan,
                validationPassed = syntaxValid,
                breakingChangesDetected = breakingChanges.isNotEmpty(),
                totalFilesAffected = totalFilesAffected,
                totalChanges = plan.changes.size,
                complexity = complexity,
                errorMessage = if (!syntaxValid) {
                    "Syntax validation failed: ${validationResult.errors.joinToString(", ")}"
                } else null
            )

            storage.set(finalResultKey, result)

            logger.info(
                "Response built: success=${result.success}, " +
                        "files=${result.totalFilesAffected}, " +
                        "changes=${result.totalChanges}, " +
                        "breaking=${result.breakingChangesDetected}"
            )

            result
        } catch (e: Exception) {
            logger.error("Error building response", e)
            CodeModificationResult(
                success = false,
                errorMessage = "Error building response: ${e.message}"
            )
        }
    }

/**
 * Calculate overall complexity based on multiple factors
 */
private fun calculateComplexity(
    changeCount: Int,
    filesAffected: Int,
    hasBreakingChanges: Boolean,
    estimatedComplexity: Complexity
): Complexity {
    // Start with LLM's estimate
    var complexityScore = when (estimatedComplexity) {
        Complexity.SIMPLE -> 1
        Complexity.MODERATE -> 2
        Complexity.COMPLEX -> 3
        Complexity.CRITICAL -> 4
    }

    // Adjust based on change count
    if (changeCount > 30) complexityScore += 1
    if (changeCount > 50) complexityScore += 1

    // Adjust based on files affected
    if (filesAffected > 10) complexityScore += 1
    if (filesAffected > 15) complexityScore += 1

    // Breaking changes always increase complexity
    if (hasBreakingChanges) complexityScore += 1

    // Map score back to complexity
    return when (complexityScore) {
        in 0..1 -> Complexity.SIMPLE
        2 -> Complexity.MODERATE
        in 3..4 -> Complexity.COMPLEX
        else -> Complexity.CRITICAL
    }
}
