package ru.andvl.chatter.koog.agents.codemodifier

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryablePattern
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.service.KoogServiceFactory
import ru.andvl.chatter.koog.service.Provider
import ru.andvl.chatter.shared.models.codeagent.CodeModificationRequest
import java.io.File
import kotlin.test.*

/**
 * DIAGNOSTIC TEST: Template Text Issue in Code Modifier Agent
 *
 * Problem:
 * When the Code Modifier agent processes requests, it returns template text instead of actual code:
 *
 * ```
 * // MODIFICATION REQUIRED:
 * // Instructions: <user instructions>
 * // File: <file-path>
 * // Language: <project-language>
 * //
 * // Original code below - apply modifications as instructed:
 * //
 * <original code>
 * ```
 *
 * This is clearly a placeholder/template instead of actual modified code.
 *
 * Root Cause Analysis:
 * File: SubgraphModificationPlanning.kt
 * Function: generateModifiedContent() (lines 370-387)
 *
 * Current Implementation:
 * ```kotlin
 * private fun generateModifiedContent(
 *     oldContent: String,
 *     instructions: String,
 *     ctx: FileContext
 * ): String {
 *     val lines = oldContent.lines()
 *
 *     return buildString {
 *         appendLine("// MODIFICATION REQUIRED:")
 *         appendLine("// Instructions: $instructions")
 *         appendLine("// File: ${ctx.filePath}")
 *         appendLine("// Language: ${ctx.language}")
 *         appendLine()
 *         appendLine("// Original code below - apply modifications as instructed:")
 *         appendLine()
 *         append(oldContent)
 *     }
 * }
 * ```
 *
 * This function ALWAYS returns template text. It's supposed to be a placeholder for LLM-generated code,
 * but the LLM integration was never implemented.
 *
 * Expected Behavior:
 * The function should call an LLM to generate actual modified code based on:
 * - The original code content
 * - The user's instructions
 * - The file context (language, structure)
 *
 * Verification Points:
 * 1. Check that newContent does NOT contain "MODIFICATION REQUIRED" template text
 * 2. Check that LLM is called in nodeGenerateModificationPlan
 * 3. Verify that actual code modifications are generated
 * 4. Ensure the modifications are syntactically valid
 *
 * Test Strategy:
 * 1. Run the agent with a simple request (e.g., "Add unit tests")
 * 2. Capture the modification plan
 * 3. Inspect newContent field of each ProposedChange
 * 4. Assert that it contains actual code, not template text
 * 5. Provide detailed diagnostic report with exact line numbers for fixing
 */
private val logger = LoggerFactory.getLogger("TemplateTextIssueDiagnosticTest")

class TemplateTextIssueDiagnosticTest {

    /**
     * DIAGNOSTIC TEST: Reproduce template text issue
     *
     * This test reproduces the exact issue reported by the user:
     * - Repository: /tmp/repository-analyzer/AndVl1-test-example
     * - Request: "Add unit tests for all functions"
     * - Expected: Real unit test code
     * - Actual: Template text placeholders
     */
    @Test
    fun `DIAGNOSTIC - Template text instead of real modifications`() = runBlocking {
        logger.error("╔════════════════════════════════════════════════════════════════════════════╗")
        logger.error("║                   TEMPLATE TEXT ISSUE DIAGNOSTIC TEST                      ║")
        logger.error("╚════════════════════════════════════════════════════════════════════════════╝")
        logger.error("")

        // Load environment
        val dotenv = dotenv {
            directory = "/Users/a.vladislavov/personal/ai-advent-3/chatter"
            ignoreIfMissing = true
        }

        val apiKey = dotenv["OPENROUTER_API_KEY"] ?: System.getenv("OPENROUTER_API_KEY")
            .ifEmpty { null }
            ?: throw IllegalStateException("OPENROUTER_API_KEY not found")

        val promptExecutor = createPromptExecutorWithQwen(apiKey)
        val koogService = KoogServiceFactory.createFromEnv()

        // Test repository
        val sessionId = "/tmp/repository-analyzer/AndVl1-test-example"
        val repoDir = File(sessionId)

        if (!repoDir.exists()) {
            logger.error("❌ Test repository not found: $sessionId")
            logger.error("Please clone the test repository first:")
            logger.error("  mkdir -p /tmp/repository-analyzer")
            logger.error("  cd /tmp/repository-analyzer")
            logger.error("  git clone https://github.com/AndVl1/test-example AndVl1-test-example")
            fail("Test repository not found")
        }

        logger.info("✅ Test repository found: $sessionId")
        logger.info("")

        // Reproduce the exact request from the user
        val request = CodeModificationRequest(
            sessionId = sessionId,
            instructions = "Add unit tests for all functions",
            fileScope = null,
            enableValidation = true,
            maxChanges = 5
        )

        logger.info("📋 Test Request:")
        logger.info("  Session ID: ${request.sessionId}")
        logger.info("  Instructions: ${request.instructions}")
        logger.info("  File Scope: ${request.fileScope ?: "ALL FILES"}")
        logger.info("  Max Changes: ${request.maxChanges}")
        logger.info("")

        // Execute
        logger.info("🚀 Starting Code Modifier Agent...")
        val startTime = System.currentTimeMillis()

        val result = koogService.modifyCode(request, promptExecutor, Provider.OPENROUTER)

        val duration = System.currentTimeMillis() - startTime
        logger.info("⏱️  Agent completed in ${duration}ms")
        logger.info("")

        // Analyze the result
        logger.error("╔════════════════════════════════════════════════════════════════════════════╗")
        logger.error("║                           DIAGNOSTIC RESULTS                               ║")
        logger.error("╚════════════════════════════════════════════════════════════════════════════╝")
        logger.error("")

        logger.info("Success: ${result.success}")
        logger.info("Total Changes: ${result.totalChanges}")
        logger.info("Files Affected: ${result.totalFilesAffected}")
        logger.info("")

        val plan = result.modificationPlan
        assertNotNull(plan, "Modification plan should not be null")

        logger.info("📝 Modification Plan:")
        logger.info("  Changes: ${plan.changes.size}")
        logger.info("  Complexity: ${plan.estimatedComplexity}")
        logger.info("")

        // CRITICAL CHECK: Inspect each change for template text
        var templateTextFound = false
        val templateTextChanges = mutableListOf<String>()

        plan.changes.forEachIndexed { index, change ->
            logger.info("─────────────────────────────────────────────────────────────────────────────")
            logger.info("Change #${index + 1}:")
            logger.info("  File: ${change.filePath}")
            logger.info("  Type: ${change.changeType}")
            logger.info("  Description: ${change.description}")
            logger.info("  Lines: ${change.startLine}:${change.endLine}")
            logger.info("")

            // Check for template text markers
            val newContent = change.newContent
            val hasTemplateText = newContent.contains("MODIFICATION REQUIRED:")

            if (hasTemplateText) {
                templateTextFound = true
                templateTextChanges.add(change.filePath)

                logger.error("  ❌ TEMPLATE TEXT DETECTED!")
                logger.error("  New Content Preview (first 300 chars):")
                logger.error("  ${newContent.take(300)}")
                logger.error("")
                logger.error("  This is NOT actual modified code!")
                logger.error("  It's a placeholder template from generateModifiedContent()")
                logger.error("")
            } else {
                logger.info("  ✅ New Content appears to be actual code (first 200 chars):")
                logger.info("  ${newContent.take(200)}")
                logger.info("")
            }

            // Also log old content for comparison
            val oldContent = change.oldContent
            if (oldContent != null) {
                logger.info("  Old Content Preview (first 200 chars):")
                logger.info("  ${oldContent.take(200)}")
                logger.info("")
            }
        }

        logger.info("─────────────────────────────────────────────────────────────────────────────")
        logger.error("")

        // DIAGNOSTIC REPORT
        logger.error("╔════════════════════════════════════════════════════════════════════════════╗")
        logger.error("║                        ROOT CAUSE ANALYSIS                                 ║")
        logger.error("╚════════════════════════════════════════════════════════════════════════════╝")
        logger.error("")

        if (templateTextFound) {
            logger.error("🔴 ISSUE CONFIRMED: Template text found in ${templateTextChanges.size} changes")
            logger.error("")
            logger.error("Files affected:")
            templateTextChanges.forEach { logger.error("  - $it") }
            logger.error("")

            logger.error("ROOT CAUSE:")
            logger.error("  File: koog-service/src/main/kotlin/ru/andvl/chatter/koog/agents/codemodifier/subgraphs/SubgraphModificationPlanning.kt")
            logger.error("  Function: generateModifiedContent()")
            logger.error("  Lines: 370-387")
            logger.error("")

            logger.error("PROBLEM:")
            logger.error("  The generateModifiedContent() function ALWAYS returns template text.")
            logger.error("  It was meant to be a placeholder for LLM-generated code,")
            logger.error("  but the LLM integration was never implemented.")
            logger.error("")

            logger.error("CURRENT IMPLEMENTATION:")
            logger.error("""
  private fun generateModifiedContent(
      oldContent: String,
      instructions: String,
      ctx: FileContext
  ): String {
      return buildString {
          appendLine("// MODIFICATION REQUIRED:")
          appendLine("// Instructions: ${'$'}instructions")
          appendLine("// File: ${'$'}{ctx.filePath}")
          appendLine("// Language: ${'$'}{ctx.language}")
          appendLine()
          appendLine("// Original code below - apply modifications as instructed:")
          appendLine()
          append(oldContent)
      }
  }
            """.trimIndent())
            logger.error("")

            logger.error("╔════════════════════════════════════════════════════════════════════════════╗")
            logger.error("║                     INSTRUCTIONS FOR DEVELOPER AGENT                       ║")
            logger.error("╚════════════════════════════════════════════════════════════════════════════╝")
            logger.error("")

            logger.error("FILE TO FIX:")
            logger.error("  /Users/a.vladislavov/personal/ai-advent-3/chatter/koog-service/src/main/kotlin/ru/andvl/chatter/koog/agents/codemodifier/subgraphs/SubgraphModificationPlanning.kt")
            logger.error("")

            logger.error("REQUIRED CHANGES:")
            logger.error("")
            logger.error("1. Replace generateModifiedContent() function (lines 370-387)")
            logger.error("   Current: Returns hardcoded template text")
            logger.error("   Required: Call LLM to generate actual modified code")
            logger.error("")

            logger.error("2. Update nodeGenerateModificationPlan (lines 64-80)")
            logger.error("   Current: Only calls createDetailedPlan() with template generation")
            logger.error("   Required: Use PromptExecutor to call LLM with buildModificationPrompt()")
            logger.error("")

            logger.error("3. Implementation strategy:")
            logger.error("   a) In nodeGenerateModificationPlan:")
            logger.error("      - Get model from storage")
            logger.error("      - Build prompt using buildModificationPrompt()")
            logger.error("      - Call promptExecutor.execute(model, prompt)")
            logger.error("      - Parse LLM response using parseModificationPlan()")
            logger.error("")
            logger.error("   b) Replace generateModifiedContent() with LLM-based generation:")
            logger.error("      - Build a prompt for each individual change")
            logger.error("      - Include: oldContent, instructions, fileContext")
            logger.error("      - Call LLM to generate actual modified code")
            logger.error("      - Return the LLM-generated code (not template text)")
            logger.error("")

            logger.error("ALTERNATIVE APPROACH (simpler):")
            logger.error("   Use buildModificationPrompt() at the node level to generate")
            logger.error("   ALL modifications in one LLM call, then parse the response")
            logger.error("   into individual ProposedChange objects.")
            logger.error("")
            logger.error("   This is BETTER because:")
            logger.error("   - Single LLM call instead of N calls")
            logger.error("   - Prompt is already written (buildModificationPrompt)")
            logger.error("   - Parser is already written (parseModificationPlan)")
            logger.error("   - Just need to wire them together in nodeGenerateModificationPlan")
            logger.error("")

            logger.error("SPECIFIC CODE CHANGE REQUIRED:")
            logger.error("")
            logger.error("Replace lines 64-80 in SubgraphModificationPlanning.kt:")
            logger.error("")
            logger.error("FROM:")
            logger.error("""
  node<CodeAnalysisResult, ModificationPlan>("generate-modification-plan") { analysisResult ->
      logger.info("Generating modification plan")

      val instructions = storage.get(instructionsKey)!!
      val maxChanges = storage.get(maxChangesKey) ?: 50

      // Generate detailed plan with actual code extraction
      val plan = createDetailedPlan(analysisResult, instructions, maxChanges)

      logger.info("Generated plan with ${'$'}{plan.changes.size} changes")
      storage.set(modificationPlanKey, plan)

      plan
  }
            """.trimIndent())
            logger.error("")

            logger.error("TO:")
            logger.error("""
  node<CodeAnalysisResult, ModificationPlan>("generate-modification-plan") { analysisResult ->
      logger.info("Generating modification plan with LLM")

      val instructions = storage.get(instructionsKey)!!
      val maxChanges = storage.get(maxChangesKey) ?: 50

      // Build comprehensive prompt for LLM
      val prompt = buildModificationPrompt(analysisResult, instructions, maxChanges)

      logger.info("Calling LLM to generate modification plan...")
      logger.debug("Prompt length: ${'$'}{prompt.length} chars")

      // Call LLM
      val response = promptExecutor.execute(model, prompt)

      logger.info("LLM response received, parsing...")

      // Parse LLM response into structured plan
      val plan = parseModificationPlan(response.content, maxChanges)

      logger.info("Generated plan with ${'$'}{plan.changes.size} changes")
      storage.set(modificationPlanKey, plan)

      plan
  }
            """.trimIndent())
            logger.error("")

            logger.error("4. Remove or deprecate:")
            logger.error("   - createDetailedPlan() function (lines 219-282)")
            logger.error("   - generateModifiedContent() function (lines 370-387)")
            logger.error("   These are no longer needed when using LLM-based generation")
            logger.error("")

            logger.error("5. Verify the fix:")
            logger.error("   - Run this diagnostic test again")
            logger.error("   - Check that newContent contains ACTUAL code, not template text")
            logger.error("   - Check logs for LLM call and response")
            logger.error("")

            logger.error("╔════════════════════════════════════════════════════════════════════════════╗")
            logger.error("║                              SUMMARY                                       ║")
            logger.error("╚════════════════════════════════════════════════════════════════════════════╝")
            logger.error("")
            logger.error("The Code Modifier agent is generating template text because:")
            logger.error("1. nodeGenerateModificationPlan calls createDetailedPlan()")
            logger.error("2. createDetailedPlan() calls generateModifiedContent()")
            logger.error("3. generateModifiedContent() ALWAYS returns hardcoded template text")
            logger.error("4. LLM is NEVER called to generate actual modifications")
            logger.error("")
            logger.error("Fix: Replace createDetailedPlan() with LLM-based generation using")
            logger.error("     the already-written buildModificationPrompt() and parseModificationPlan() functions.")
            logger.error("")

            // Fail the test to ensure visibility
            fail(
                """
                ❌ TEMPLATE TEXT ISSUE CONFIRMED

                Template text found in ${templateTextChanges.size} file(s):
                ${templateTextChanges.joinToString("\n") { "  - $it" }}

                See diagnostic report above for detailed fix instructions.

                Key fix: Replace createDetailedPlan() with LLM-based generation
                in SubgraphModificationPlanning.kt, nodeGenerateModificationPlan function.
                """.trimIndent()
            )
        } else {
            logger.info("✅ NO TEMPLATE TEXT FOUND!")
            logger.info("All modifications appear to contain actual code.")
            logger.info("")
            logger.info("This means the issue has been FIXED.")
        }
    }

    /**
     * Create PromptExecutor with Qwen3-Coder via OpenRouter
     */
    private fun createPromptExecutorWithQwen(apiKey: String): PromptExecutor {
        val timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 180_000,
            connectTimeoutMillis = 30_000
        )

        val baseClient = OpenRouterLLMClient(
            apiKey = apiKey,
            settings = OpenRouterClientSettings(
                timeoutConfig = timeoutConfig
            )
        )

        val resilientClient = RetryingLLMClient(
            delegate = baseClient,
            config = RetryConfig.CONSERVATIVE.copy(
                retryablePatterns = buildList {
                    addAll(RetryConfig.DEFAULT_PATTERNS)
                    add(RetryablePattern.Regex(Regex("Field \\'.*\\' is required for.*")))
                    add(RetryablePattern.Regex(Regex("rate limit", RegexOption.IGNORE_CASE)))
                    add(RetryablePattern.Regex(Regex("timeout", RegexOption.IGNORE_CASE)))
                }
            )
        )

        return SingleLLMPromptExecutor(resilientClient)
    }
}
