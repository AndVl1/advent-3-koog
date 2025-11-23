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
 * 1. Build response - Assemble final CodeModificationResult with Docker validation info
 *
 * Input: DockerValidationResult
 * Output: CodeModificationResult
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphResponseBuilding():
        AIAgentSubgraphDelegate<DockerValidationResult, CodeModificationResult> =
    subgraph(name = "response-building") {
        val nodeBuildResponse by nodeBuildResponse()

        edge(nodeStart forwardTo nodeBuildResponse)
        edge(nodeBuildResponse forwardTo nodeFinish)
    }

/**
 * Node: Build response
 *
 * Assembles the final result with all metadata including Docker validation.
 */
private fun AIAgentSubgraphBuilderBase<DockerValidationResult, CodeModificationResult>.nodeBuildResponse() =
    node<DockerValidationResult, CodeModificationResult>("build-response") { dockerValidationResult ->
        logger.info("Building final response with Docker validation info")

        try {
            val plan = storage.get(modificationPlanKey)
            val validationCheckResult = storage.get(validationCheckResultKey)

            if (plan == null) {
                logger.error("No modification plan found in storage")
                return@node CodeModificationResult(
                    success = false,
                    errorMessage = "Failed to generate modification plan"
                )
            }

            if (validationCheckResult == null) {
                logger.error("No validation check result found in storage")
                return@node CodeModificationResult(
                    success = false,
                    errorMessage = "Failed to validate modifications"
                )
            }

            val syntaxValid = validationCheckResult.syntaxValid
            val breakingChanges = validationCheckResult.breakingChanges

            // Calculate total files affected
            val totalFilesAffected = plan.changes.map { it.filePath }.distinct().size

            // Calculate complexity (consider Docker validation results)
            val complexity = calculateComplexity(
                changeCount = plan.changes.size,
                filesAffected = totalFilesAffected,
                hasBreakingChanges = breakingChanges.isNotEmpty(),
                estimatedComplexity = plan.estimatedComplexity,
                dockerValidationFailed = dockerValidationResult.validated &&
                    (dockerValidationResult.buildPassed == false || dockerValidationResult.testsPassed == false)
            )

            val result = CodeModificationResult(
                success = syntaxValid &&
                    (!dockerValidationResult.validated ||
                     (dockerValidationResult.buildPassed != false && dockerValidationResult.testsPassed != false)),
                modificationPlan = plan,
                validationPassed = syntaxValid,
                breakingChangesDetected = breakingChanges.isNotEmpty(),
                totalFilesAffected = totalFilesAffected,
                totalChanges = plan.changes.size,
                complexity = complexity,
                dockerValidationResult = dockerValidationResult,
                errorMessage = buildErrorMessage(
                    syntaxValid = syntaxValid,
                    validationErrors = validationCheckResult.errors,
                    dockerValidationResult = dockerValidationResult
                )
            )

            storage.set(finalResultKey, result)

            logger.info(
                "Response built: success=${result.success}, " +
                        "files=${result.totalFilesAffected}, " +
                        "changes=${result.totalChanges}, " +
                        "breaking=${result.breakingChangesDetected}, " +
                        "docker_validated=${dockerValidationResult.validated}, " +
                        "docker_build=${dockerValidationResult.buildPassed}, " +
                        "docker_tests=${dockerValidationResult.testsPassed}"
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
 * Build comprehensive error message
 */
private fun buildErrorMessage(
    syntaxValid: Boolean,
    validationErrors: List<String>,
    dockerValidationResult: DockerValidationResult
): String? {
    val errors = mutableListOf<String>()

    if (!syntaxValid) {
        errors.add("Syntax validation failed: ${validationErrors.joinToString(", ")}")
    }

    if (dockerValidationResult.validated) {
        if (dockerValidationResult.buildPassed == false) {
            errors.add("Docker build failed: ${dockerValidationResult.errorMessage ?: "Unknown error"}")
        }
        if (dockerValidationResult.testsPassed == false) {
            errors.add("Docker tests failed: ${dockerValidationResult.errorMessage ?: "Unknown error"}")
        }
    }

    return if (errors.isNotEmpty()) errors.joinToString("; ") else null
}

/**
 * Calculate overall complexity based on multiple factors
 */
private fun calculateComplexity(
    changeCount: Int,
    filesAffected: Int,
    hasBreakingChanges: Boolean,
    estimatedComplexity: Complexity,
    dockerValidationFailed: Boolean = false
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

    // Docker validation failure increases complexity
    if (dockerValidationFailed) complexityScore += 1

    // Map score back to complexity
    return when (complexityScore) {
        in 0..1 -> Complexity.SIMPLE
        2 -> Complexity.MODERATE
        in 3..4 -> Complexity.COMPLEX
        else -> Complexity.CRITICAL
    }
}
