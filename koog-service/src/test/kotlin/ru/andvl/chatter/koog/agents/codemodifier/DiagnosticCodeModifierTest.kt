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

/**
 * Diagnostic test to investigate empty newContent issue
 *
 * This test will:
 * 1. Run the Code Modifier agent on real repository
 * 2. Capture and print LLM response
 * 3. Show how parsing extracts content
 * 4. Identify why newContent is empty
 */
private val logger = LoggerFactory.getLogger("DiagnosticCodeModifierTest")

class DiagnosticCodeModifierTest {

    @Test
    fun `diagnose empty newContent issue`() = runBlocking {
        logger.info("=== DIAGNOSTIC TEST: Empty newContent Issue ===")

        // Load environment
        val dotenv = dotenv {
            directory = "/Users/a.vladislavov/personal/ai-advent-3/chatter"
            ignoreIfMissing = true
        }

        val apiKey = dotenv["OPENROUTER_API_KEY"]
            ?: throw IllegalStateException("OPENROUTER_API_KEY not found")

        logger.info("Environment loaded successfully")

        // Create services
        val promptExecutor = createPromptExecutorWithQwen(apiKey)
        val koogService = KoogServiceFactory.createFromEnv()

        // Test repository
        val sessionId = "/tmp/repository-analyzer/AndVl1-test-example"
        val repoDir = File(sessionId)

        if (!repoDir.exists()) {
            logger.error("Test repository not found at: $sessionId")
            logger.info("Please run: git clone https://github.com/AndVl1/test-example /tmp/repository-analyzer/AndVl1-test-example")
            return@runBlocking
        }

        logger.info("Repository found: $sessionId")

        // Simple modification request
        val request = CodeModificationRequest(
            sessionId = sessionId,
            instructions = "Add a comment to the first function you find",
            fileScope = null,
            enableValidation = true,
            maxChanges = 2  // Keep it small for diagnostic
        )

        logger.info("Running Code Modifier Agent...")
        logger.info("Instructions: ${request.instructions}")
        logger.info("")

        // Run agent
        val result = koogService.modifyCode(request, promptExecutor, Provider.OPENROUTER)

        // Analyze results
        logger.info("=== RESULTS ===")
        logger.info("Success: ${result.success}")
        logger.info("Error: ${result.errorMessage ?: "NONE"}")
        logger.info("")

        val plan = result.modificationPlan
        if (plan != null) {
            logger.info("=== MODIFICATION PLAN ===")
            logger.info("Rationale: ${plan.rationale}")
            logger.info("Number of changes: ${plan.changes.size}")
            logger.info("")

            plan.changes.forEachIndexed { index, change ->
                logger.info("Change #${index + 1}:")
                logger.info("  File: ${change.filePath}")
                logger.info("  Type: ${change.changeType}")
                logger.info("  Description: ${change.description}")
                logger.info("  Lines: ${change.startLine}:${change.endLine}")
                logger.info("")

                logger.info("  Old Content Length: ${change.oldContent?.length ?: 0}")
                if (change.oldContent != null && change.oldContent!!.isNotEmpty()) {
                    logger.info("  Old Content (first 100 chars): ${change.oldContent!!.take(100)}")
                } else {
                    logger.warn("  Old Content: EMPTY OR NULL")
                }
                logger.info("")

                logger.info("  New Content Length: ${change.newContent.length}")
                if (change.newContent.isNotEmpty()) {
                    logger.info("  New Content (first 200 chars):")
                    logger.info("${change.newContent.take(200)}")
                } else {
                    logger.error("  New Content: EMPTY!!!")
                }
                logger.info("")
                logger.info("=".repeat(80))
                logger.info("")
            }

            // Check for empty content issue
            val emptyChanges = plan.changes.filter { it.newContent.isEmpty() }
            if (emptyChanges.isNotEmpty()) {
                logger.error("ISSUE FOUND: ${emptyChanges.size} changes have EMPTY newContent!")
                logger.error("This is the bug we're investigating.")
            } else {
                logger.info("All changes have non-empty newContent - issue may be intermittent")
            }
        } else {
            logger.error("Modification plan is NULL!")
        }

        logger.info("")
        logger.info("=== DIAGNOSTIC TEST COMPLETE ===")
        logger.info("")
        logger.info("Next Steps:")
        logger.info("1. Check the trace log at: logs/koog_code_modifier_trace.log")
        logger.info("2. Look for the LLM response in 'generate-modification-plan' node")
        logger.info("3. Check if LLM returned empty new_content fields")
        logger.info("4. Or check if regex parsing failed to extract multi-line content")
    }

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
