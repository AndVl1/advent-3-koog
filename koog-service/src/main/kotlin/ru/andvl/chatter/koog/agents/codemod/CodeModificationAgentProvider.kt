package ru.andvl.chatter.koog.agents.codemod

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codemod.subgraphs.*
import ru.andvl.chatter.koog.model.codemod.*

private val logger = LoggerFactory.getLogger("codemod-strategy")

/**
 * Creates Code Modification Agent strategy with retry mechanism
 *
 * Flow:
 * 1. Repository Setup - Clone/reuse repo, create feature branch
 * 2. Code Analysis - Analyze codebase and generate modification plan
 * 3. Modification Loop (max 5 iterations):
 *    a. Code Modification - Apply changes
 *    b. Docker Verification - Verify changes work
 *    c. If failed: Analyze error and retry
 *    d. If success: Continue to Git operations
 * 4. Git Operations - Commit and push changes
 * 5. Finalize - Create PR or save diff
 */
internal suspend fun getCodeModificationStrategy(
    model: LLModel,
    fixingModel: LLModel
): AIAgentGraphStrategy<CodeModificationRequest, CodeModificationResponse> =
    strategy("code-modification-agent") {
        // Create subgraphs
        val repositorySetup by subgraphRepositorySetup()
        val codeAnalysis by subgraphCodeAnalysis(model, fixingModel)
        val codeModification by subgraphCodeModification(model, fixingModel)
        val dockerVerification by subgraphDockerVerification(model, fixingModel)
        val gitOperations by subgraphGitOperations(model)
        val finalize by subgraphFinalize(model)

        // Retry logic nodes
        val nodeCheckVerificationResult by nodeCheckVerificationResult()
        val nodeAnalyzeError by nodeAnalyzeError(model)
        val nodeCheckRetryLimit by nodeCheckRetryLimit()

        // Error handling node
        val nodeCreateErrorResponse by nodeCreateErrorResponse()

        // Main flow
        edge(nodeStart forwardTo repositorySetup)
        edge(repositorySetup forwardTo codeAnalysis)
        edge(codeAnalysis forwardTo codeModification)
        edge(codeModification forwardTo dockerVerification)
        edge(dockerVerification forwardTo nodeCheckVerificationResult)

        // If verification succeeds, continue to git operations
        edge(nodeCheckVerificationResult forwardTo gitOperations onCondition { result: VerificationResult ->
            result.success
        })

        // If verification fails, check retry limit
        edge(nodeCheckVerificationResult forwardTo nodeCheckRetryLimit onCondition { result: VerificationResult ->
            !result.success
        })

        // If under retry limit, analyze error and retry
        edge(nodeCheckRetryLimit forwardTo nodeAnalyzeError onCondition {
            val currentIteration = storage.get(createStorageKey<Int>("iterations-used")) ?: 0
            currentIteration < 2
        })
        edge(nodeAnalyzeError forwardTo codeModification) // Retry modification

        // If max retries exceeded, create error response
        edge(nodeCheckRetryLimit forwardTo nodeCreateErrorResponse onCondition {
            val currentIteration = storage.get(createStorageKey<Int>("iterations-used")) ?: 0
            currentIteration >= 2
        })

        // Finalize after successful git operations
        edge(gitOperations forwardTo finalize)
        edge(finalize forwardTo nodeFinish)
        edge(nodeCreateErrorResponse forwardTo nodeFinish)
    }

/**
 * Node: Check verification result (success or failure)
 */
private fun AIAgentGraphStrategyBuilder<CodeModificationRequest, CodeModificationResponse>.nodeCheckVerificationResult() =
    node<VerificationResult, VerificationResult>("check-verification-result") { verificationResult ->
        storage.set(createStorageKey<Boolean>("verification-success"), verificationResult.success)
        storage.set(verificationResultKey, verificationResult)

        if (verificationResult.success) {
            logger.info("Verification succeeded")
        } else {
            logger.warn("Verification failed: ${verificationResult.errorMessage}")

            // Increment iteration counter
            val currentIteration = storage.get(createStorageKey<Int>("iterations-used")) ?: 0
            storage.set(createStorageKey<Int>("iterations-used"), currentIteration + 1)

            logger.info("Iteration ${currentIteration + 1}/5 failed")
        }

        verificationResult
    }

/**
 * Node: Analyze error and generate fix plan with structured parsing
 */
private fun AIAgentGraphStrategyBuilder<CodeModificationRequest, CodeModificationResponse>.nodeAnalyzeError(model: LLModel) =
    node<VerificationResult, AnalysisResult>("analyze-error") { verificationResult ->
        val previousModification = storage.get(modificationsAppliedKey)
        val previousAnalysis = storage.get(createStorageKey<AnalysisResult>("previous-analysis"))
        val currentIteration = storage.get(createStorageKey<Int>("iterations-used")) ?: 1

        logger.info("Analyzing error from iteration $currentIteration")

        // Use structured parsing for guaranteed JSON correctness
        val analysisResult = llm.writeSession {
            appendPrompt {
                system(
                    """
You are an expert at analyzing code modification failures and proposing fixes.

The previous code modification failed verification. Your task is to analyze the error and propose fixes in structured JSON format.

**Output Structure**:
```json
{
  "modification_plan": "Updated plan describing what needs to be fixed and how",
  "files_to_modify": ["path/to/file.kt"],
  "dependencies_identified": ["dep1", "dep2"],
  "docker_env": null
}
```

**Important**:
- Focus on the specific error shown in the logs
- Make minimal changes to fix the issue
- Consider compilation errors, missing imports, syntax errors
- Don't repeat the same mistake from previous iteration
- Set docker_env to null (it's already configured)
""".trimIndent()
                )
                user(
                    """
**Iteration**: $currentIteration / 2

**Previous Modification Plan**: ${previousAnalysis?.modificationPlan ?: "Unknown"}

**Modifications Applied**:
- Files Modified: ${previousModification?.filesModified?.joinToString(", ") ?: "None"}
- Files Created: ${previousModification?.filesCreated?.joinToString(", ") ?: "None"}
- Patches Applied: ${previousModification?.patchesApplied ?: 0}

**Docker Verification Logs**:
```
${verificationResult.logs.joinToString("\n")}
```

**Exit Code**: ${verificationResult.exitCode}
**Error Summary**: ${verificationResult.errorMessage ?: "Unknown error"}

Analyze the error and provide an updated modification plan.
""".trimIndent()
                )
            }

            requestLLMStructured<AnalysisResult>(
                examples = listOf(
                    AnalysisResult(
                        modificationPlan = "Fix import statement in UserServiceTest.kt - add missing import for @Test annotation",
                        filesToModify = listOf("src/test/kotlin/com/example/UserServiceTest.kt"),
                        dependenciesIdentified = listOf("JUnit 5"),
                        dockerEnv = null
                    )
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = model,
                    retries = 3
                )
            )
        }.getOrThrow().structure

        storage.set(createStorageKey<AnalysisResult>("previous-analysis"), analysisResult)
        logger.info("Error analysis completed, will retry with updated plan")
        analysisResult
    }

/**
 * Node: Check if retry limit is reached
 */
private fun AIAgentGraphStrategyBuilder<CodeModificationRequest, CodeModificationResponse>.nodeCheckRetryLimit() =
    node<VerificationResult, VerificationResult>("check-retry-limit") { verificationResult ->
        val currentIteration = storage.get(createStorageKey<Int>("iterations-used")) ?: 0

        logger.info("Checking retry limit: $currentIteration / 2")

        if (currentIteration >= 2) {
            logger.error("Max retry limit reached")
        }

        verificationResult
    }

/**
 * Node: Create error response when max retries exceeded
 */
private fun AIAgentGraphStrategyBuilder<CodeModificationRequest, CodeModificationResponse>.nodeCreateErrorResponse() =
    node<VerificationResult, CodeModificationResponse>("create-error-response") { verificationResult ->
        val modifications = storage.get(modificationsAppliedKey)
        val featureBranch = storage.get(featureBranchKey) ?: "unknown"
        val iterations = storage.get(createStorageKey<Int>("iterations-used")) ?: 5

        logger.error("Creating error response after $iterations failed iterations")

        CodeModificationResponse(
            success = false,
            prUrl = null,
            prNumber = null,
            diff = null,
            commitSha = null,
            branchName = featureBranch,
            filesModified = modifications?.filesModified ?: emptyList(),
            verificationStatus = VerificationStatus.FAILED_VERIFICATION,
            iterationsUsed = iterations,
            errorMessage = "Verification failed after $iterations attempts: ${verificationResult.errorMessage}",
            message = "Code modifications failed verification after maximum retries"
        )
    }
