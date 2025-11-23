# Quick Fix Instructions - Template Text Issue

## Problem
Code Modifier agent returns template text instead of actual code modifications.

## Location
File: `SubgraphModificationPlanning.kt`
Lines: 64-80 (nodeGenerateModificationPlan)

## Fix

Replace this:
```kotlin
private fun AIAgentSubgraphBuilderBase<CodeAnalysisResult, PlanningResult>.nodeGenerateModificationPlan(
    model: LLModel
) =
    node<CodeAnalysisResult, ModificationPlan>("generate-modification-plan") { analysisResult ->
        logger.info("Generating modification plan")

        val instructions = storage.get(instructionsKey)!!
        val maxChanges = storage.get(maxChangesKey) ?: 50

        // Generate detailed plan with actual code extraction
        val plan = createDetailedPlan(analysisResult, instructions, maxChanges)

        logger.info("Generated plan with ${plan.changes.size} changes")
        storage.set(modificationPlanKey, plan)

        plan
    }
```

With this:
```kotlin
private fun AIAgentSubgraphBuilderBase<CodeAnalysisResult, PlanningResult>.nodeGenerateModificationPlan(
    model: LLModel
) =
    node<CodeAnalysisResult, ModificationPlan>("generate-modification-plan") { analysisResult ->
        logger.info("Generating modification plan with LLM")

        val instructions = storage.get(instructionsKey)!!
        val maxChanges = storage.get(maxChangesKey) ?: 50

        // Build comprehensive prompt for LLM
        val prompt = buildModificationPrompt(analysisResult, instructions, maxChanges)

        logger.info("Calling LLM to generate modification plan...")
        logger.debug("Prompt length: ${prompt.length} chars")

        // Call LLM
        val response = promptExecutor.execute(model, prompt)

        logger.info("LLM response received (${response.content.length} chars), parsing...")

        // Parse LLM response into structured plan
        val plan = try {
            parseModificationPlan(response.content, maxChanges)
        } catch (e: Exception) {
            logger.error("Failed to parse LLM response", e)
            logger.debug("LLM response was: ${response.content.take(500)}...")
            throw IllegalStateException("Failed to generate modification plan: ${e.message}", e)
        }

        logger.info("Generated plan with ${plan.changes.size} changes")
        storage.set(modificationPlanKey, plan)

        plan
    }
```

## Test the Fix

```bash
./gradlew :koog-service:test --tests "TemplateTextIssueDiagnosticTest"
```

Expected output: `✅ NO TEMPLATE TEXT FOUND!`

## What This Does

- **Before**: Calls `createDetailedPlan()` which returns hardcoded template text
- **After**: Calls LLM with `buildModificationPrompt()` and parses response with `parseModificationPlan()`

Both helper functions already exist and are fully implemented - they just weren't being used!

## Optional Cleanup

After verifying the fix works, you can delete these unused functions:
- `createDetailedPlan()` (line 219-282)
- `generateModifiedContent()` (line 370-387)
- `findFunctionLines()` (line 287)
- `findClassLines()` (line 309)
- `findBlockEnd()` (line 331)
- `findImportEndLine()` (line 362)

## Root Cause

The developer wrote the LLM integration functions but then implemented a temporary fallback that became permanent. The TODO comment on line 59-62 confirms this was meant to be temporary.
