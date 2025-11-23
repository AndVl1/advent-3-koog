package ru.andvl.chatter.koog.agents.codemodifier.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codemodifier.*
import ru.andvl.chatter.koog.model.codemodifier.*
import ru.andvl.chatter.koog.tools.FileOperationsToolSet
import java.util.*

private val logger = LoggerFactory.getLogger("codemodifier-modification-planning")

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

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
 * 3. Parses the JSON response into ModificationPlan
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

        // Call LLM to generate modification plan (text-only, no tools)
        val responseContent = llm.writeSession {
            appendPrompt {
                system(prompt)
            }

            val response = requestLLM()
            response.content
        }

        logger.debug("LLM response received: ${responseContent.take(200)}...")

        // Clean markdown artifacts from LLM response (e.g., ```json wrappers)
        val cleanedResponse = responseContent
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Parse JSON response
        val plan = try {
            parseModificationPlan(cleanedResponse, maxChanges)
        } catch (e: Exception) {
            logger.error("Failed to parse modification plan from LLM response", e)
            logger.error("Response was: $responseContent")
            throw IllegalStateException("Failed to generate valid modification plan: ${e.message}", e)
        }

        logger.info("Generated plan with ${plan.changes.size} changes")
        logger.info("Estimated complexity: ${plan.estimatedComplexity}")
        storage.set(modificationPlanKey, plan)

        plan
    }

/**
 * Node: Assign change IDs
 *
 * Assigns unique UUIDs to each change for tracking.
 */
private fun AIAgentSubgraphBuilderBase<CodeAnalysisResult, PlanningResult>.nodeAssignChangeIds() =
    node<ModificationPlan, ModificationPlan>("assign-change-ids") { plan ->
        logger.info("Assigning change IDs")

        val changesWithIds = plan.changes.map { change ->
            change.copy(changeId = UUID.randomUUID().toString())
        }

        val updatedPlan = plan.copy(changes = changesWithIds)
        storage.set(modificationPlanKey, updatedPlan)
        storage.set(proposedChangesKey, changesWithIds)

        logger.info("Assigned IDs to ${changesWithIds.size} changes")

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
 * Estimate complexity based on number of changes
 */
private fun estimateComplexity(changesCount: Int): Complexity {
    return when {
        changesCount <= 5 -> Complexity.SIMPLE
        changesCount <= 15 -> Complexity.MODERATE
        changesCount <= 30 -> Complexity.COMPLEX
        else -> Complexity.CRITICAL
    }
}

/**
 * Parse modification plan from JSON
 *
 * Uses kotlinx.serialization for proper JSON parsing.
 * Handles multi-line code content in old_content and new_content fields.
 */
private fun parseModificationPlan(jsonContent: String, maxChanges: Int): ModificationPlan {
    try {
        val changes = mutableListOf<ProposedChange>()

        // Parse using kotlinx.serialization only - no regex fallback!
        val jsonElement = json.parseToJsonElement(jsonContent)

        if (jsonElement !is JsonObject) {
            logger.error("Expected JSON object, got: ${jsonElement::class.simpleName}")
            throw IllegalStateException("Invalid JSON structure")
        }

        // Extract rationale
        val rationale = jsonElement["rationale"]?.jsonPrimitive?.contentOrNull
            ?: "Modification plan generated"

        // Extract complexity
        val complexityStr = jsonElement["estimated_complexity"]?.jsonPrimitive?.contentOrNull
        val complexity = when (complexityStr?.uppercase()) {
            "SIMPLE" -> Complexity.SIMPLE
            "MODERATE" -> Complexity.MODERATE
            "COMPLEX" -> Complexity.COMPLEX
            "CRITICAL" -> Complexity.CRITICAL
            else -> Complexity.MODERATE
        }

        // Parse changes array
        val changesArray = jsonElement["changes"] as? JsonArray
        if (changesArray == null) {
            logger.error("No 'changes' array found in JSON")
            throw IllegalStateException("Missing 'changes' array in modification plan")
        }

        changesArray.take(maxChanges).forEach { changeElement ->
            val changeObj = changeElement as? JsonObject
            if (changeObj != null) {
                val change = parseChangeFromJson(changeObj)
                if (change != null) {
                    changes.add(change)
                } else {
                    logger.warn("Failed to parse change object: $changeObj")
                }
            }
        }

        if (changes.isEmpty()) {
            logger.error("No changes extracted from plan. JSON: ${jsonContent.take(500)}")
            throw IllegalStateException("No changes extracted from modification plan")
        }

        logger.info("Successfully parsed ${changes.size} changes from LLM response")

        return ModificationPlan(
            changes = changes,
            rationale = rationale,
            estimatedComplexity = complexity
        )
    } catch (e: Exception) {
        logger.error("Failed to parse modification plan JSON", e)
        logger.error("JSON content (first 500 chars): ${jsonContent.take(500)}")
        throw IllegalStateException("Failed to parse modification plan: ${e.message}", e)
    }
}

/**
 * Parse a single change from JsonObject
 */
private fun parseChangeFromJson(changeObj: kotlinx.serialization.json.JsonObject): ProposedChange? {
    try {
        val filePath = changeObj["file_path"]?.jsonPrimitive?.content ?: return null
        val changeType = parseChangeType(changeObj["change_type"]?.jsonPrimitive?.content ?: "MODIFY")
        val description = changeObj["description"]?.jsonPrimitive?.content ?: ""
        val startLine = changeObj["start_line"]?.jsonPrimitive?.intOrNull
        val endLine = changeObj["end_line"]?.jsonPrimitive?.intOrNull
        val oldContent = changeObj["old_content"]?.jsonPrimitive?.contentOrNull
        val newContent = changeObj["new_content"]?.jsonPrimitive?.content ?: ""
        val dependsOn = (changeObj["depends_on"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        return ProposedChange(
            changeId = "", // Will be assigned later
            filePath = filePath,
            changeType = changeType,
            description = description,
            startLine = startLine,
            endLine = endLine,
            oldContent = oldContent,
            newContent = newContent,
            dependsOn = dependsOn
        )
    } catch (e: Exception) {
        logger.warn("Failed to parse change from JSON object", e)
        return null
    }
}

private fun parseChangeType(typeStr: String): ChangeType {
    return when (typeStr.uppercase()) {
        "CREATE" -> ChangeType.CREATE
        "MODIFY" -> ChangeType.MODIFY
        "DELETE" -> ChangeType.DELETE
        "RENAME" -> ChangeType.RENAME
        "REFACTOR" -> ChangeType.REFACTOR
        else -> ChangeType.MODIFY
    }
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
