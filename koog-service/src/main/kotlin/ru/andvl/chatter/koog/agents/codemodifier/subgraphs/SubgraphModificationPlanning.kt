package ru.andvl.chatter.koog.agents.codemodifier.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.prompt.llm.LLModel
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codemodifier.*
import ru.andvl.chatter.koog.model.codemodifier.*
import ru.andvl.chatter.koog.tools.FileOperationsToolSet
import java.util.*

private val logger = LoggerFactory.getLogger("codemodifier-modification-planning")

/**
 * Subgraph: Modification Planning
 *
 * Flow:
 * 1. Generate modification plan (LLM + Tools) - Create structured plan with proposed changes
 * 2. Assign change IDs - Give each change a unique ID
 * 3. Sort changes by dependencies - Topological sort
 *
 * Input: CodeAnalysisResult
 * Output: PlanningResult
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphModificationPlanning(
    model: LLModel
): AIAgentSubgraphDelegate<CodeAnalysisResult, PlanningResult> =
    subgraph(
        name = "modification-planning",
        tools = ToolRegistry {
            tools(FileOperationsToolSet())
        }.tools
    ) {
        val nodeGenerateModificationPlan by nodeGenerateModificationPlan(model)
        val nodeAssignChangeIds by nodeAssignChangeIds()
        val nodeSortChangesByDependencies by nodeSortChangesByDependencies()

        edge(nodeStart forwardTo nodeGenerateModificationPlan)
        edge(nodeGenerateModificationPlan forwardTo nodeAssignChangeIds)
        edge(nodeAssignChangeIds forwardTo nodeSortChangesByDependencies)
        edge(nodeSortChangesByDependencies forwardTo nodeFinish)
    }

/**
 * Node: Generate modification plan
 *
 * Generates a detailed modification plan from analysis result using LLM.
 *
 * This node:
 * 1. Builds a comprehensive prompt with file contexts and patterns
 * 2. Calls LLM to generate structured modification plan (text-only, no tools)
 * 3. Uses Koog structured output API to parse the JSON response into ModificationPlan
 * 4. Stores the plan for further processing
 */
private fun AIAgentSubgraphBuilderBase<CodeAnalysisResult, PlanningResult>.nodeGenerateModificationPlan(
    model: LLModel
) =
    node<CodeAnalysisResult, ModificationPlan>("generate-modification-plan") { analysisResult ->
        logger.info("Generating modification plan using LLM")

        val instructions = storage.get(instructionsKey)!!
        val maxChanges = storage.get(maxChangesKey) ?: 50

        // Build prompt for LLM
        val prompt = buildModificationPrompt(analysisResult, instructions, maxChanges)
        logger.debug("Prompt built with ${analysisResult.fileContexts.size} file contexts")

        // Call LLM to generate modification plan using structured output
        val structuredResponse = llm.writeSession {
            appendPrompt {
                system(prompt)
            }

            requestLLMStructured<ModificationPlan>()
        }

        logger.debug("LLM structured response received")

        // Extract plan from structured response
        val plan = structuredResponse.getOrElse { error ->
            logger.error("Failed to get structured modification plan from LLM", error)
            throw IllegalStateException("Failed to generate valid modification plan: ${error.message}", error)
        }.structure

        // Validate and limit changes
        val validatedPlan = if (plan.changes.size > maxChanges) {
            logger.warn("Plan has ${plan.changes.size} changes, limiting to $maxChanges")
            plan.copy(changes = plan.changes.take(maxChanges))
        } else {
            plan
        }

        logger.info("Generated plan with ${validatedPlan.changes.size} changes")
        logger.info("Estimated complexity: ${validatedPlan.estimatedComplexity}")
        storage.set(modificationPlanKey, validatedPlan)

        validatedPlan
    }

/**
 * Node: Assign change IDs
 *
 * Assigns unique UUIDs to each change for tracking.
 * Also converts index-based dependencies to ID-based dependencies.
 */
private fun AIAgentSubgraphBuilderBase<CodeAnalysisResult, PlanningResult>.nodeAssignChangeIds() =
    node<ModificationPlan, ModificationPlan>("assign-change-ids") { plan ->
        logger.info("Assigning change IDs")

        // First pass: assign UUIDs to all changes
        val changesWithIds = plan.changes.map { change ->
            change.copy(changeId = UUID.randomUUID().toString())
        }

        // Create index -> ID mapping for converting depends_on
        val indexToId = changesWithIds.mapIndexed { index, change -> index.toString() to change.changeId }.toMap()

        // Second pass: convert index-based dependencies to ID-based dependencies
        val changesWithConvertedDeps = changesWithIds.map { change ->
            if (change.dependsOn.isNotEmpty()) {
                val convertedDeps = change.dependsOn.mapNotNull { dep ->
                    // Try to convert index to ID, or keep as-is if already an ID
                    indexToId[dep] ?: dep.takeIf { it.isNotBlank() }
                }
                change.copy(dependsOn = convertedDeps)
            } else {
                change
            }
        }

        val updatedPlan = plan.copy(changes = changesWithConvertedDeps)
        storage.set(modificationPlanKey, updatedPlan)
        storage.set(proposedChangesKey, changesWithConvertedDeps)

        logger.info("Assigned IDs to ${changesWithConvertedDeps.size} changes and converted dependencies")

        updatedPlan
    }

/**
 * Node: Sort changes by dependencies
 *
 * Performs topological sort on changes based on dependencies.
 */
private fun AIAgentSubgraphBuilderBase<CodeAnalysisResult, PlanningResult>.nodeSortChangesByDependencies() =
    node<ModificationPlan, PlanningResult>("sort-changes-by-dependencies") { plan ->
        logger.info("Sorting changes by dependencies")

        val sortedChanges = topologicalSort(plan.changes)

        val sortedPlan = plan.copy(
            changes = sortedChanges,
            dependenciesSorted = true
        )

        storage.set(modificationPlanKey, sortedPlan)

        val result = PlanningResult(
            plan = sortedPlan,
            planValid = true
        )

        storage.set(planningResultKey, result)
        logger.info("Planning completed with ${sortedChanges.size} changes (sorted)")

        result
    }

// Helper functions

/**
 * Build comprehensive prompt for LLM to generate modification plan
 */
private fun buildModificationPrompt(
    analysisResult: CodeAnalysisResult,
    instructions: String,
    maxChanges: Int
): String {
    val fileContextsSection = analysisResult.fileContexts.joinToString("\n\n") { ctx ->
        """
        File: ${ctx.filePath}
        Language: ${ctx.language}
        Total Lines: ${ctx.totalLines}
        ${if (ctx.imports.isNotEmpty()) "Imports: ${ctx.imports.joinToString(", ")}" else ""}
        ${if (ctx.classes.isNotEmpty()) "Classes: ${ctx.classes.joinToString(", ")}" else ""}
        ${if (ctx.functions.isNotEmpty()) "Functions: ${ctx.functions.joinToString(", ")}" else ""}

        Content (first 100 lines):
        ```${ctx.language}
        ${ctx.content.lines().take(100).joinToString("\n")}
        ```
        """.trimIndent()
    }

    val patternsSection = with(analysisResult.detectedPatterns) {
        """
        Code Style Patterns:
        - Indentation: $indentation
        - Naming Convention: $namingConvention
        - Code Style: $codeStyle
        ${if (commonPatterns.isNotEmpty()) "- Common Patterns: ${commonPatterns.joinToString(", ")}" else ""}
        """.trimIndent()
    }

    return """
        You are a code modification assistant. Generate a detailed modification plan based on the user's instructions.

        User Instructions: $instructions

        Files to Analyze:
        $fileContextsSection

        $patternsSection

        Generate a modification plan in valid JSON format with the following structure:
        {
          "changes": [
            {
              "file_path": "path/to/file.kt",
              "change_type": "MODIFY",
              "description": "Brief description of what this change does",
              "start_line": 10,
              "end_line": 20,
              "old_content": "// Original code that will be replaced (actual code from the file)",
              "new_content": "// New code to insert (actual modified code)",
              "depends_on": []
            }
          ],
          "rationale": "Overall explanation of why these changes are needed",
          "estimated_complexity": "SIMPLE"
        }

        Rules:
        - Maximum $maxChanges changes
        - change_type must be one of: CREATE, MODIFY, DELETE, RENAME, REFACTOR
        - Provide ACTUAL code in new_content and old_content fields (not comments or placeholders)
        - Be specific about line numbers (start_line and end_line)
        - old_content should contain the exact code from the file that will be replaced
        - new_content should contain the complete modified code
        - Explain dependencies between changes using depends_on (array of change indices)
        - estimated_complexity must be one of: SIMPLE, MODERATE, COMPLEX, CRITICAL
        - Follow the detected code style patterns in the modifications
        - Ensure all changes are necessary and aligned with the user's instructions

        IMPORTANT OUTPUT FORMAT:
        - Return ONLY valid JSON
        - Do NOT wrap the response in markdown code blocks (no ```json or ```)
        - Do NOT include any explanatory text before or after the JSON
        - Ensure all strings are properly escaped (use \" for quotes inside strings)
        - Use \n for newlines in code content
        - Make sure new_content and old_content contain ACTUAL code, not placeholders

        Example of correct response format:
        {
          "changes": [...],
          "rationale": "...",
          "estimated_complexity": "SIMPLE"
        }
    """.trimIndent()
}



/**
 * Topological sort for change dependencies
 */
private fun topologicalSort(changes: List<ProposedChange>): List<ProposedChange> {
    val changeMap = changes.associateBy { it.changeId }
    val visited = mutableSetOf<String>()
    val result = mutableListOf<ProposedChange>()

    fun visit(changeId: String) {
        if (changeId in visited) return
        visited.add(changeId)

        val change = changeMap[changeId] ?: return

        // Visit dependencies first
        change.dependsOn.forEach { depId ->
            visit(depId)
        }

        result.add(change)
    }

    // Visit all changes
    changes.forEach { change ->
        visit(change.changeId)
    }

    return result
}
