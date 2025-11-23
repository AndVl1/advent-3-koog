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
import ru.andvl.chatter.koog.service.KoogService
import ru.andvl.chatter.koog.service.KoogServiceFactory
import ru.andvl.chatter.koog.service.Provider
import ru.andvl.chatter.shared.models.codeagent.CodeModificationRequest
import java.io.File
import kotlin.test.*

/**
 * Test for Code Modifier Agent
 *
 * This test verifies the Code Modifier Agent functionality:
 * - Request validation (session validation, file scope normalization)
 * - Code analysis (finding relevant files, extracting context)
 * - Modification planning (generating structured modification plans)
 * - Validation (syntax checking, breaking changes detection)
 * - Response building (assembling final result)
 *
 * Uses the test repository: /tmp/repository-analyzer/AndVl1-test-example
 * LLM Model: qwen/qwen3-coder via OpenRouter
 */
private val logger = LoggerFactory.getLogger("CodeModifierAgentTest")

class CodeModifierAgentTest {

    /**
     * Test simple code modification request
     *
     * This test:
     * - Uses a simple test repository
     * - Requests a simple modification (add comment to main function)
     * - Validates the response structure
     * - Checks logs for errors
     */
    @Test
    fun `test simple code modification`() = runBlocking {
        logger.info("=== Code Modifier Agent Test: Simple Modification ===")

        // Load environment variables from project root
        val dotenv = dotenv {
            directory = "/Users/a.vladislavov/personal/ai-advent-3/chatter"
            ignoreIfMissing = true
        }

        // Get API key from dotenv
        val apiKey = dotenv["OPENROUTER_API_KEY"] ?: System.getenv("OPENROUTER_API_KEY")
            .ifEmpty { null }
            ?: throw IllegalStateException("OPENROUTER_API_KEY not found in .env or environment variables")

        logger.info("API Key loaded successfully")

        // Create prompt executor with Qwen3-Coder
        val promptExecutor = createPromptExecutorWithQwen(apiKey)

        // Create KoogService
        val koogService = KoogServiceFactory.createFromEnv()

        // Test repository path
        val sessionId = "/tmp/repository-analyzer/AndVl1-test-example"

        // Verify repository exists
        val repoDir = File(sessionId)
        assertTrue(repoDir.exists(), "Test repository does not exist at: $sessionId")
        assertTrue(repoDir.isDirectory, "Session path is not a directory: $sessionId")
        logger.info("Test repository found: $sessionId")
        logger.info("Repository contents:")
        repoDir.walkTopDown().filter { it.isFile }.forEach {
            logger.info("  - ${it.relativeTo(repoDir).path}")
        }
        logger.info("")

        // Prepare test request - simple modification
        val request = CodeModificationRequest(
            sessionId = sessionId,
            instructions = "Add a detailed comment to the main function explaining what it does. The comment should describe the function's purpose in a clear, professional way.",
            fileScope = null, // Test with all files
            enableValidation = true,
            maxChanges = 10
        )

        logger.info("Test Request:")
        logger.info("  Session ID: ${request.sessionId}")
        logger.info("  Instructions: ${request.instructions}")
        logger.info("  File Scope: ${request.fileScope ?: "ALL FILES"}")
        logger.info("  Enable Validation: ${request.enableValidation}")
        logger.info("  Max Changes: ${request.maxChanges}")
        logger.info("")

        // Act: Run code modification
        logger.info("Starting code modification agent...")
        val startTime = System.currentTimeMillis()

        val result = koogService.modifyCode(request, promptExecutor, Provider.OPENROUTER)

        val duration = System.currentTimeMillis() - startTime
        logger.info("Code modification completed in ${duration}ms")
        logger.info("")

        // Assert: Verify response structure
        logger.info("=== Results ===")
        logger.info("Success: ${result.success}")
        logger.info("Validation Passed: ${result.validationPassed}")
        logger.info("Breaking Changes Detected: ${result.breakingChangesDetected}")
        logger.info("Total Files Affected: ${result.totalFilesAffected}")
        logger.info("Total Changes: ${result.totalChanges}")
        logger.info("Complexity: ${result.complexity}")
        logger.info("Error Message: ${result.errorMessage ?: "NONE"}")
        logger.info("Model: ${result.model}")
        logger.info("")

        // Check modification plan
        val planForLogging = result.modificationPlan
        if (planForLogging != null) {
            logger.info("=== Modification Plan ===")
            logger.info("Rationale: ${planForLogging.rationale}")
            logger.info("Estimated Complexity: ${planForLogging.estimatedComplexity}")
            logger.info("Dependencies Sorted: ${planForLogging.dependenciesSorted}")
            logger.info("Number of Changes: ${planForLogging.changes.size}")
            logger.info("")

            // Log each change
            planForLogging.changes.forEachIndexed { index, change ->
                logger.info("Change #${index + 1}:")
                logger.info("  ID: ${change.changeId}")
                logger.info("  File: ${change.filePath}")
                logger.info("  Type: ${change.changeType}")
                logger.info("  Description: ${change.description}")
                logger.info("  Lines: ${change.startLine}:${change.endLine}")
                logger.info("  Old Content: ${change.oldContent?.take(100)}...")
                logger.info("  New Content: ${change.newContent.take(100)}...")
                logger.info("  Depends On: ${change.dependsOn}")
                logger.info("  Validation Notes: ${change.validationNotes ?: "NONE"}")
                logger.info("")
            }
        } else {
            logger.warn("Modification plan is NULL")
        }

        // Validate result
        assertTrue(result.success, "Code modification should succeed")
        assertNotNull(result.modificationPlan, "Modification plan should not be null")

        val plan = result.modificationPlan
        if (plan != null) {
            assertTrue(plan.changes.isNotEmpty(), "Changes list should not be empty")
            assertTrue(plan.rationale.isNotEmpty(), "Rationale should not be empty")

            // Validate each change
            plan.changes.forEach { change ->
            assertNotNull(change.changeId, "Change ID should not be null")
            assertTrue(change.changeId.isNotEmpty(), "Change ID should not be empty")

            assertNotNull(change.filePath, "File path should not be null")
            assertTrue(change.filePath.isNotEmpty(), "File path should not be empty")

            assertNotNull(change.description, "Description should not be null")
            assertTrue(change.description.isNotEmpty(), "Description should not be empty")

            assertNotNull(change.newContent, "New content should not be null")
            assertTrue(change.newContent.isNotEmpty(), "New content should not be empty")

            // For MODIFY changes, old content should be present
            if (change.changeType == "MODIFY") {
                assertNotNull(change.oldContent, "Old content should not be null for MODIFY changes in ${change.filePath}")
                assertTrue(change.oldContent!!.isNotEmpty(), "Old content should not be empty for MODIFY changes")
            }

                // Line ranges should be valid
                val startLine = change.startLine
                val endLine = change.endLine
                if (startLine != null && endLine != null) {
                    assertTrue(startLine > 0, "Start line should be positive")
                    assertTrue(endLine >= startLine, "End line should be >= start line")
                }
            }
        } else {
            fail("Modification plan should not be null")
        }

        // Check validation results
        if (request.enableValidation) {
            logger.info("=== Validation Results ===")
            logger.info("Syntax Valid: ${result.validationPassed}")
            logger.info("Breaking Changes: ${result.breakingChangesDetected}")

            // If validation is enabled, we expect validation status
            // Note: validation might fail if the LLM generates invalid code
            // But the result should still contain the validation status
            logger.info("Validation check completed")
        }

        // Check for errors in logs
        val logFile = File("/Users/a.vladislavov/personal/ai-advent-3/chatter/logs/koog_code_modifier_trace.log")
        if (logFile.exists()) {
            logger.info("=== Log Analysis ===")
            val logContent = logFile.readText()
            val lines = logContent.lines()

            // Check for errors
            val errors = lines.filter { it.contains("ERROR", ignoreCase = true) || it.contains("Exception") }
            if (errors.isNotEmpty()) {
                logger.warn("Found ${errors.size} errors in logs:")
                errors.take(10).forEach { logger.warn("  $it") }
            } else {
                logger.info("No errors found in trace logs")
            }

            // Check for FileOperationsToolSet rejections
            val rejections = lines.filter { it.contains("rejected", ignoreCase = true) || it.contains("not allowed", ignoreCase = true) }
            if (rejections.isNotEmpty()) {
                logger.warn("Found ${rejections.size} path rejections:")
                rejections.take(5).forEach { logger.warn("  $it") }
            } else {
                logger.info("No path rejections found")
            }

            // Check subgraph executions
            val subgraphStarts = lines.filter { it.contains("Subgraph", ignoreCase = true) && it.contains("start", ignoreCase = true) }
            val subgraphEnds = lines.filter { it.contains("Subgraph", ignoreCase = true) && it.contains("complete", ignoreCase = true) }
            logger.info("Subgraphs started: ${subgraphStarts.size}")
            logger.info("Subgraphs completed: ${subgraphEnds.size}")

            // Check if all expected subgraphs executed
            val expectedSubgraphs = listOf(
                "request-validation",
                "code-analysis",
                "modification-planning",
                "validation",
                "response-building"
            )
            expectedSubgraphs.forEach { subgraph ->
                val mentioned = lines.any { it.contains(subgraph, ignoreCase = true) }
                logger.info("  Subgraph '$subgraph': ${if (mentioned) "FOUND" else "NOT FOUND"}")
            }
        } else {
            logger.warn("Trace log file not found: ${logFile.absolutePath}")
        }

        logger.info("")
        logger.info("=== Test Completed Successfully ===")
    }

    /**
     * Test code modification with specific file scope
     *
     * This test:
     * - Specifies a particular file to modify
     * - Validates file scope normalization
     * - Checks that only the specified file is affected
     */
    @Test
    fun `test code modification with file scope`() = runBlocking {
        logger.info("=== Code Modifier Agent Test: With File Scope ===")

        val dotenv = dotenv {
            directory = "/Users/a.vladislavov/personal/ai-advent-3/chatter"
            ignoreIfMissing = true
        }

        val apiKey = dotenv["OPENROUTER_API_KEY"] ?: System.getenv("OPENROUTER_API_KEY")
            .ifEmpty { null }
            ?: throw IllegalStateException("OPENROUTER_API_KEY not found")

        val promptExecutor = createPromptExecutorWithQwen(apiKey)
        val koogService = KoogServiceFactory.createFromEnv()

        val sessionId = "/tmp/repository-analyzer/AndVl1-test-example"

        // Find a specific file to modify
        val repoDir = File(sessionId)
        val testFile = repoDir.walkTopDown()
            .filter { it.isFile && it.extension in listOf("kt", "java", "py", "js", "ts") }
            .firstOrNull()

        if (testFile == null) {
            logger.warn("No code files found in test repository, skipping file scope test")
            return@runBlocking
        }

        val relativeFilePath = testFile.relativeTo(repoDir).path
        logger.info("Target file for modification: $relativeFilePath")

        val request = CodeModificationRequest(
            sessionId = sessionId,
            instructions = "Add a TODO comment at the top of the file with your name",
            fileScope = listOf(relativeFilePath),
            enableValidation = true,
            maxChanges = 5
        )

        logger.info("Test Request:")
        logger.info("  Session ID: ${request.sessionId}")
        logger.info("  Instructions: ${request.instructions}")
        logger.info("  File Scope: ${request.fileScope}")
        logger.info("")

        val result = koogService.modifyCode(request, promptExecutor, Provider.OPENROUTER)

        logger.info("=== Results ===")
        logger.info("Success: ${result.success}")
        logger.info("Total Files Affected: ${result.totalFilesAffected}")
        logger.info("Total Changes: ${result.totalChanges}")
        logger.info("")

        val modPlan = result.modificationPlan
        if (modPlan != null) {
            modPlan.changes.forEach { change ->
                logger.info("Change: ${change.filePath}")
                logger.info("  Type: ${change.changeType}")
                logger.info("  Description: ${change.description}")
            }
        }

        // Validate file scope
        assertTrue(result.success, "Code modification should succeed")
        assertNotNull(result.modificationPlan, "Modification plan should not be null")

        // All changes should be in the specified file
        val plan = result.modificationPlan
        if (plan != null) {
            plan.changes.forEach { change ->
                assertTrue(
                    change.filePath.endsWith(relativeFilePath) || change.filePath.contains(relativeFilePath),
                    "Change should be in the specified file scope: ${change.filePath}"
                )
            }
        } else {
            fail("Modification plan should not be null")
        }

        logger.info("=== Test Completed Successfully ===")
    }

    /**
     * Test code modification with invalid session
     *
     * This test verifies error handling for invalid session paths
     */
    @Test
    fun `test code modification with invalid session`() = runBlocking {
        logger.info("=== Code Modifier Agent Test: Invalid Session ===")

        val dotenv = dotenv {
            directory = "/Users/a.vladislavov/personal/ai-advent-3/chatter"
            ignoreIfMissing = true
        }

        val apiKey = dotenv["OPENROUTER_API_KEY"] ?: System.getenv("OPENROUTER_API_KEY")
            .ifEmpty { null }
            ?: throw IllegalStateException("OPENROUTER_API_KEY not found")

        val promptExecutor = createPromptExecutorWithQwen(apiKey)
        val koogService = KoogServiceFactory.createFromEnv()

        val request = CodeModificationRequest(
            sessionId = "/tmp/non-existent-repository",
            instructions = "Add a comment",
            fileScope = null,
            enableValidation = true,
            maxChanges = 10
        )

        logger.info("Test Request with invalid session:")
        logger.info("  Session ID: ${request.sessionId}")
        logger.info("")

        val result = koogService.modifyCode(request, promptExecutor, Provider.OPENROUTER)

        logger.info("=== Results ===")
        logger.info("Success: ${result.success}")
        logger.info("Error Message: ${result.errorMessage}")
        logger.info("")

        // Should fail due to invalid session
        assertFalse(result.success, "Should fail with invalid session")
        assertNotNull(result.errorMessage, "Should have error message")
        val errorMsg = result.errorMessage
        if (errorMsg != null) {
            assertTrue(
                errorMsg.contains("session", ignoreCase = true) ||
                        errorMsg.contains("not found", ignoreCase = true) ||
                        errorMsg.contains("does not exist", ignoreCase = true),
                "Error message should mention session validation issue"
            )
        } else {
            fail("Error message should not be null")
        }

        logger.info("=== Test Completed Successfully ===")
    }

    /**
     * Create PromptExecutor with OpenRouter using qwen/qwen3-coder model
     *
     * As specified in the QA Agent requirements, we must use:
     * - Qwen3-Coder as the model
     * - OpenRouter as the LLM provider
     * - API keys from .env file
     */
    private fun createPromptExecutorWithQwen(apiKey: String): PromptExecutor {
        val timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 180_000, // 3 minutes for code modification
            connectTimeoutMillis = 30_000   // 30 seconds connect timeout
        )

        val baseClient = OpenRouterLLMClient(
            apiKey = apiKey,
            settings = OpenRouterClientSettings(
                timeoutConfig = timeoutConfig
            )
        )

        // Add retry logic for robustness
        val resilientClient = RetryingLLMClient(
            delegate = baseClient,
            config = RetryConfig.CONSERVATIVE.copy(
                retryablePatterns = buildList {
                    addAll(RetryConfig.DEFAULT_PATTERNS)
                    // Add pattern for missing field errors
                    add(RetryablePattern.Regex(Regex("Field \\'.*\\' is required for.*")))
                    // Add pattern for rate limiting
                    add(RetryablePattern.Regex(Regex("rate limit", RegexOption.IGNORE_CASE)))
                    // Add pattern for timeout
                    add(RetryablePattern.Regex(Regex("timeout", RegexOption.IGNORE_CASE)))
                }
            )
        )

        return SingleLLMPromptExecutor(resilientClient)
    }
}
