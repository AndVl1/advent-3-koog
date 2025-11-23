# Template Text Issue - Diagnostic Report

## Executive Summary

**Issue**: Code Modifier agent returns template text instead of actual code modifications
**Status**: CONFIRMED via automated test
**Severity**: CRITICAL - Agent is non-functional for end users
**Root Cause**: Missing LLM integration in modification planning
**Location**: `SubgraphModificationPlanning.kt`, lines 370-387

---

## Problem Description

When users request code modifications (e.g., "Add unit tests"), the agent returns placeholder template text instead of actual modified code:

```kotlin
// MODIFICATION REQUIRED:
// Instructions: Add unit tests for all functions
// File: utils/src/main/kotlin/Calculator.kt
// Language: Kotlin
//
// Original code below - apply modifications as instructed:
//
<original code here>
```

**Expected**: Real unit test code
**Actual**: Template comments with original code

---

## Test Evidence

### Diagnostic Test Results

**Test**: `TemplateTextIssueDiagnosticTest.kt`
**Repository**: `/tmp/repository-analyzer/AndVl1-test-example`
**Request**: "Add unit tests for all functions"
**Result**: FAILED - Template text detected in 5 files

**Files Affected**:
- app/build.gradle.kts
- app/src/main/kotlin/App.kt
- utils/build.gradle.kts
- utils/src/main/kotlin/Calculator.kt
- utils/src/main/kotlin/CollectionUtils.kt

**Sample Output** (from Calculator.kt):
```
Change #4:
  File: utils/src/main/kotlin/Calculator.kt
  Type: MODIFY
  Description: Apply modifications to utils/src/main/kotlin/Calculator.kt: Add unit tests for all functions
  Lines: 1:20

  ❌ TEMPLATE TEXT DETECTED!
  New Content Preview (first 300 chars):
  // MODIFICATION REQUIRED:
  // Instructions: Add unit tests for all functions
  // File: utils/src/main/kotlin/Calculator.kt
  // Language: Kotlin

  // Original code below - apply modifications as instructed:

  package org.example.utils

  class Calculator {
      fun add(a: Int, b: Int): Int = a + b
```

---

## Root Cause Analysis

### File
`/Users/a.vladislavov/personal/ai-advent-3/chatter/koog-service/src/main/kotlin/ru/andvl/chatter/koog/agents/codemodifier/subgraphs/SubgraphModificationPlanning.kt`

### Problem Function: `generateModifiedContent()` (Lines 370-387)

```kotlin
private fun generateModifiedContent(
    oldContent: String,
    instructions: String,
    ctx: FileContext
): String {
    val lines = oldContent.lines()

    return buildString {
        appendLine("// MODIFICATION REQUIRED:")
        appendLine("// Instructions: $instructions")
        appendLine("// File: ${ctx.filePath}")
        appendLine("// Language: ${ctx.language}")
        appendLine()
        appendLine("// Original code below - apply modifications as instructed:")
        appendLine()
        append(oldContent)
    }
}
```

**Analysis**: This function ALWAYS returns hardcoded template text. It does not call any LLM or generate actual code modifications.

### Call Chain

1. `nodeGenerateModificationPlan` (line 67) calls `createDetailedPlan()`
2. `createDetailedPlan()` (line 219) calls `generateModifiedContent()` (line 252)
3. `generateModifiedContent()` (line 370) returns template text
4. Result: All `ProposedChange.newContent` fields contain template text

### Why LLM is Never Called

The code has two sets of functions:

**Used (returns templates)**:
- `nodeGenerateModificationPlan` → calls `createDetailedPlan()`
- `createDetailedPlan()` → calls `generateModifiedContent()`
- `generateModifiedContent()` → returns template text

**Unused (would work correctly)**:
- `buildModificationPrompt()` (line 138) - Creates LLM prompt ✅ Written but not used
- `parseModificationPlan()` (line 407) - Parses LLM response ✅ Written but not used

### TODO Comment Found

Line 59-62 in SubgraphModificationPlanning.kt:
```kotlin
/**
 * TODO: Integrate with LLM for intelligent modification generation.
 * Current implementation creates structured plans based on file analysis and user instructions.
 * Future enhancement: Use PromptExecutor to call LLM with buildModificationPrompt() and
 * parse response with parseModificationPlan().
 */
```

This TODO was never implemented.

---

## Instructions for Developer Agent

### File to Fix
`/Users/a.vladislavov/personal/ai-advent-3/chatter/koog-service/src/main/kotlin/ru/andvl/chatter/koog/agents/codemodifier/subgraphs/SubgraphModificationPlanning.kt`

### Required Changes

#### 1. Replace `nodeGenerateModificationPlan` Implementation (Lines 64-80)

**Current (incorrect)**:
```kotlin
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

**Required (correct)**:
```kotlin
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

#### 2. Remove or Deprecate Unused Functions

These functions are no longer needed when using LLM-based generation:

- **Line 219-282**: `createDetailedPlan()` - Delete or mark as deprecated
- **Line 370-387**: `generateModifiedContent()` - Delete or mark as deprecated

The helper functions used by `createDetailedPlan()` can also be removed:
- `findFunctionLines()` (line 287)
- `findClassLines()` (line 309)
- `findBlockEnd()` (line 331)
- `findImportEndLine()` (line 362)

#### 3. Update the TODO Comment

Replace the TODO comment (lines 59-62) with:
```kotlin
/**
 * Node: Generate modification plan
 *
 * Uses LLM to generate intelligent code modifications based on:
 * - File context and structure
 * - User instructions
 * - Detected code patterns
 *
 * The LLM receives a comprehensive prompt via buildModificationPrompt()
 * and returns a structured JSON plan parsed by parseModificationPlan().
 */
```

#### 4. Verify `promptExecutor` and `model` are Available

Check that the node has access to:
- `promptExecutor: PromptExecutor` - Should be available from storage or parameter
- `model: LLModel` - Should be available from storage or parameter

If not available, they must be passed to the subgraph or stored in storage before this node runs.

#### 5. Add Error Handling

Ensure the node handles LLM failures gracefully:
- Timeout errors
- Rate limiting
- Invalid JSON responses
- Empty responses

---

## Verification Steps

After implementing the fix:

1. **Run the diagnostic test**:
   ```bash
   ./gradlew :koog-service:test --tests "TemplateTextIssueDiagnosticTest"
   ```

2. **Check for success**:
   - Test should PASS
   - Log should show: "✅ NO TEMPLATE TEXT FOUND!"
   - All modifications should contain actual code

3. **Check logs**:
   ```bash
   tail -f logs/koog_code_modifier_trace.log
   ```

   Should see:
   - "Calling LLM to generate modification plan..."
   - "LLM response received"
   - NO "MODIFICATION REQUIRED:" template text

4. **Manual verification**:
   ```kotlin
   val result = koogService.modifyCode(
       CodeModificationRequest(
           sessionId = "/tmp/repository-analyzer/AndVl1-test-example",
           instructions = "Add unit tests for all functions",
           fileScope = null,
           enableValidation = true,
           maxChanges = 5
       ),
       promptExecutor,
       Provider.OPENROUTER
   )

   // Check newContent in changes
   result.modificationPlan?.changes?.forEach { change ->
       assertFalse(change.newContent.contains("MODIFICATION REQUIRED:"))
   }
   ```

---

## Impact Analysis

### What Works Currently
- ✅ Request validation
- ✅ Code analysis and file discovery
- ✅ Topological sorting of dependencies
- ✅ Response building

### What's Broken
- ❌ Modification planning (generates template text)
- ❌ Code generation (no LLM call)
- ❌ End-to-end user flow (unusable)

### After Fix
- ✅ Complete end-to-end flow
- ✅ Real code modifications
- ✅ LLM-powered intelligent changes
- ✅ User-ready functionality

---

## Additional Notes

### Why buildModificationPrompt() Exists But Isn't Used

The developer wrote `buildModificationPrompt()` and `parseModificationPlan()` as preparation for LLM integration but then implemented a fallback `createDetailedPlan()` that doesn't use them. The fallback was meant to be temporary but became the permanent implementation.

### Alternative Approaches Considered

**Approach 1: Per-file LLM calls**
- Call LLM once per file
- More granular control
- ❌ Too many API calls (expensive, slow)

**Approach 2: Single LLM call for all changes** ✅ RECOMMENDED
- Use `buildModificationPrompt()` with all files
- Parse response with `parseModificationPlan()`
- ✅ Efficient, already implemented, just needs wiring

**Approach 3: Hybrid**
- Use template for simple changes
- Use LLM for complex changes
- ❌ Over-engineered for current needs

### Related Files

- **Models**: `/koog-service/src/main/kotlin/ru/andvl/chatter/koog/model/codemodifier/CodeModifierModels.kt`
- **Agent Provider**: `/koog-service/src/main/kotlin/ru/andvl/chatter/koog/agents/codemodifier/CodeModifierAgentProvider.kt`
- **Test**: `/koog-service/src/test/kotlin/ru/andvl/chatter/koog/agents/codemodifier/TemplateTextIssueDiagnosticTest.kt`

---

## Timeline

- **Issue Reported**: User tested with "написать юнит-тесты" request
- **Issue Confirmed**: Diagnostic test run (this report)
- **Root Cause Found**: `generateModifiedContent()` returns template text
- **Fix Required**: Wire up existing `buildModificationPrompt()` and `parseModificationPlan()`
- **Estimated Fix Time**: 15-30 minutes
- **Testing Time**: 5-10 minutes

---

## Conclusion

The Code Modifier agent has a complete LLM integration infrastructure (`buildModificationPrompt`, `parseModificationPlan`) but it's not being used. Instead, a placeholder function `generateModifiedContent()` returns hardcoded template text.

**Fix**: Replace lines 64-80 in `nodeGenerateModificationPlan` to use the LLM-based approach instead of the template-based approach.

**Test**: Run `TemplateTextIssueDiagnosticTest` to verify the fix.

---

**Report Generated**: 2025-11-23
**Test Location**: `/Users/a.vladislavov/personal/ai-advent-3/chatter/koog-service/src/test/kotlin/ru/andvl/chatter/koog/agents/codemodifier/TemplateTextIssueDiagnosticTest.kt`
**Log Location**: `/Users/a.vladislavov/personal/ai-advent-3/chatter/logs/koog_code_modifier_trace.log`
