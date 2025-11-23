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
import ru.andvl.chatter.koog.tools.DockerToolSet
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = LoggerFactory.getLogger("codemodifier-docker-validation")

private const val MAX_RETRIES = 2
private const val DOCKER_OPERATION_TIMEOUT = 300 // 5 minutes

/**
 * Subgraph: LLM-based Docker Validation
 *
 * This subgraph uses LLM to intelligently validate code modifications in Docker.
 * Unlike the algorithmic approach, the LLM:
 * - Analyzes the project and creates a custom validation strategy
 * - Executes validation commands and analyzes results
 * - Diagnoses errors and suggests fixes
 * - Retries with improvements if validation fails
 * - Generates a comprehensive validation report
 *
 * Flow:
 * 1. Check Docker availability
 * 2. LLM: Analyze project and plan validation strategy
 * 3. Execute validation strategy (build, test)
 * 4. LLM: Analyze results and decide next step (success/retry/fail)
 * 5. Apply fixes if retry needed (up to MAX_RETRIES)
 * 6. LLM: Generate final validation report
 * 7. Cleanup resources
 *
 * Input: ValidationCheckResult
 * Output: DockerValidationResult
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphDockerValidation(
    model: LLModel
): AIAgentSubgraphDelegate<ValidationCheckResult, DockerValidationResult> =
    subgraph(
        name = "docker-validation",
        tools = ToolRegistry {
            tools(DockerToolSet())
        }.tools
    ) {
        val nodeCheckDockerAvailable by nodeCheckDockerAvailable()
        val nodeAnalyzeAndPlanValidation by nodeAnalyzeAndPlanValidation(model)
        val nodeExecuteValidationStrategy by nodeExecuteValidationStrategy()
        val nodeAnalyzeResultsAndDecide by nodeAnalyzeResultsAndDecide(model)
        val nodeApplyFixes by nodeApplyFixes()
        val nodeGenerateFinalReport by nodeGenerateFinalReport(model)
        val nodeSkipValidation by nodeSkipValidation()
        val nodeCleanupAndFinish by nodeCleanupAndFinish()

        // Start -> Check Docker
        edge(nodeStart forwardTo nodeCheckDockerAvailable)

        // If Docker available -> Analyze and plan
        edge(nodeCheckDockerAvailable forwardTo nodeAnalyzeAndPlanValidation onCondition { available: Boolean ->
            available
        })

        // If Docker not available -> Skip validation
        edge(nodeCheckDockerAvailable forwardTo nodeSkipValidation onCondition { available: Boolean ->
            !available
        })

        // Plan -> Execute
        edge(nodeAnalyzeAndPlanValidation forwardTo nodeExecuteValidationStrategy)

        // Execute -> Analyze results
        edge(nodeExecuteValidationStrategy forwardTo nodeAnalyzeResultsAndDecide)

        // Analyze -> Generate report (if success or max retries reached)
        edge(nodeAnalyzeResultsAndDecide forwardTo nodeGenerateFinalReport onCondition { analysis: ValidationAnalysis ->
            analysis.overallStatus == ValidationStatus.SUCCESS ||
            analysis.overallStatus == ValidationStatus.FAILED ||
            (storage.get(dockerRetryCountKey) ?: 0) >= MAX_RETRIES
        })

        // Analyze -> Apply fixes (if retry needed and retries available)
        edge(nodeAnalyzeResultsAndDecide forwardTo nodeApplyFixes onCondition { analysis: ValidationAnalysis ->
            analysis.shouldRetry &&
            (storage.get(dockerRetryCountKey) ?: 0) < MAX_RETRIES
        })

        // Apply fixes -> Execute strategy (retry loop)
        edge(nodeApplyFixes forwardTo nodeExecuteValidationStrategy)

        // Generate report -> Cleanup
        edge(nodeGenerateFinalReport forwardTo nodeCleanupAndFinish)

        // Skip -> Cleanup
        edge(nodeSkipValidation forwardTo nodeCleanupAndFinish)

        // Cleanup -> Finish
        edge(nodeCleanupAndFinish forwardTo nodeFinish)
    }

/**
 * Node: Check Docker availability
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeCheckDockerAvailable() =
    node<ValidationCheckResult, Boolean>("check-docker-available") { _ ->
        logger.info("Checking Docker availability")

        val dockerToolSet = DockerToolSet()
        val result = dockerToolSet.checkDockerAvailability()

        val available = result.available
        storage.set(dockerAvailableKey, available)

        // Initialize retry counter
        storage.set(dockerRetryCountKey, 0)

        if (available) {
            logger.info("Docker is available: ${result.version}")
        } else {
            logger.warn("Docker is not available: ${result.message}")
        }

        available
    }

/**
 * Node: Analyze project and plan validation strategy (LLM)
 *
 * This LLM node:
 * 1. Analyzes the modified files and project structure
 * 2. Determines project type and dependencies
 * 3. Creates a custom Dockerfile and validation strategy
 * 4. Plans build and test commands
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeAnalyzeAndPlanValidation(
    model: LLModel
) =
    node<Boolean, ValidationStrategy>("analyze-and-plan-validation") { _ ->
        logger.info("LLM: Analyzing project and planning validation strategy")

        // Get context from storage
        val sessionPath = storage.get(sessionPathKey)
            ?: throw IllegalStateException("Session path not found")
        val proposedChanges = storage.get(proposedChangesKey)
            ?: throw IllegalStateException("Proposed changes not found")

        // Setup validation environment (copy project, apply modifications)
        val validationDir = setupValidationEnvironment(sessionPath, proposedChanges)
        storage.set(validationDirectoryKey, validationDir)

        // Build analysis prompt
        val projectFiles = listProjectFiles(validationDir)
        val modifiedFilesList = proposedChanges.map { "${it.changeType}: ${it.filePath}" }

        val prompt = buildValidationPlanningPrompt(projectFiles, modifiedFilesList)

        // Call LLM to generate strategy using structured output
        val structuredResponse = llm.writeSession {
            appendPrompt {
                system(prompt)
            }
            requestLLMStructured<ValidationStrategy>()
        }

        logger.debug("LLM structured response received")

        // Extract strategy from structured response
        val strategy = structuredResponse.getOrElse { error ->
            logger.error("Failed to get structured validation strategy from LLM", error)
            throw IllegalStateException("Failed to generate validation strategy: ${error.message}", error)
        }.structure

        logger.info("Generated validation strategy: ${strategy.projectTypeAnalysis}")
        logger.info("Build commands: ${strategy.buildCommands.size}, Test commands: ${strategy.testCommands.size}")
        storage.set(validationStrategyKey, strategy)

        strategy
    }

/**
 * Node: Execute validation strategy (Logic)
 *
 * Executes the validation commands from the LLM-generated strategy:
 * 1. Writes Dockerfile
 * 2. Builds Docker image
 * 3. Runs build commands
 * 4. Runs test commands
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeExecuteValidationStrategy() =
    node<ValidationStrategy, List<CommandExecutionResult>>("execute-validation-strategy") { strategy ->
        logger.info("Executing validation strategy")

        val validationDir = storage.get(validationDirectoryKey)
            ?: throw IllegalStateException("Validation directory not found")

        val executionResults = mutableListOf<CommandExecutionResult>()
        val dockerToolSet = DockerToolSet()
        val imageName = "code-modifier-validation-${System.currentTimeMillis()}"
        storage.set(validationImageNameKey, imageName)

        try {
            // Step 1: Write Dockerfile
            val dockerfilePath = File(validationDir, "Dockerfile")
            dockerfilePath.writeText(strategy.dockerfileContent)
            logger.info("Dockerfile written to: ${dockerfilePath.absolutePath}")

            // Step 2: Build Docker image
            logger.info("Building Docker image: $imageName")
            val buildStart = System.currentTimeMillis()
            val buildResult = dockerToolSet.buildDockerImage(
                directoryPath = validationDir,
                imageName = imageName
            )
            val buildDuration = ((System.currentTimeMillis() - buildStart) / 1000).toInt()

            executionResults.add(
                CommandExecutionResult(
                    command = "docker build",
                    success = buildResult.success,
                    exitCode = if (buildResult.success) 0 else 1,
                    stdout = buildResult.buildLogs,
                    stderr = if (!buildResult.success) listOf(buildResult.message ?: "Build failed") else emptyList(),
                    durationSeconds = buildDuration
                )
            )

            if (!buildResult.success) {
                logger.error("Docker build failed: ${buildResult.message}")
                storage.set(executionResultsKey, executionResults)
                return@node executionResults
            }

            logger.info("Docker image built successfully in ${buildDuration}s")

            // Step 3: Run build commands
            for (buildCommand in strategy.buildCommands) {
                logger.info("Running build command: $buildCommand")
                val cmdStart = System.currentTimeMillis()
                val cmdResult = dockerToolSet.runDockerContainer(
                    imageName = imageName,
                    command = buildCommand,
                    timeoutSeconds = DOCKER_OPERATION_TIMEOUT
                )
                val cmdDuration = ((System.currentTimeMillis() - cmdStart) / 1000).toInt()

                executionResults.add(
                    CommandExecutionResult(
                        command = buildCommand,
                        success = cmdResult.success,
                        exitCode = cmdResult.exitCode,
                        stdout = cmdResult.logs.filter { !it.contains("stderr:") },
                        stderr = cmdResult.logs.filter { it.contains("stderr:") },
                        durationSeconds = cmdDuration
                    )
                )

                if (!cmdResult.success) {
                    logger.warn("Build command failed: $buildCommand (exit code: ${cmdResult.exitCode})")
                }
            }

            // Step 4: Run test commands (if build passed)
            val buildPassed = executionResults.all { it.success || it.command == "docker build" }
            if (buildPassed) {
                for (testCommand in strategy.testCommands) {
                    logger.info("Running test command: $testCommand")
                    val cmdStart = System.currentTimeMillis()
                    val cmdResult = dockerToolSet.runDockerContainer(
                        imageName = imageName,
                        command = testCommand,
                        timeoutSeconds = DOCKER_OPERATION_TIMEOUT
                    )
                    val cmdDuration = ((System.currentTimeMillis() - cmdStart) / 1000).toInt()

                    executionResults.add(
                        CommandExecutionResult(
                            command = testCommand,
                            success = cmdResult.success,
                            exitCode = cmdResult.exitCode,
                            stdout = cmdResult.logs.filter { !it.contains("stderr:") },
                            stderr = cmdResult.logs.filter { it.contains("stderr:") },
                            durationSeconds = cmdDuration
                        )
                    )

                    if (!cmdResult.success) {
                        logger.warn("Test command failed: $testCommand (exit code: ${cmdResult.exitCode})")
                    }
                }
            } else {
                logger.info("Skipping tests due to build failures")
            }

        } catch (e: Exception) {
            logger.error("Error executing validation strategy", e)
            executionResults.add(
                CommandExecutionResult(
                    command = "validation execution",
                    success = false,
                    exitCode = -1,
                    stdout = emptyList(),
                    stderr = listOf(e.message ?: "Unknown error"),
                    durationSeconds = 0
                )
            )
        }

        logger.info("Validation execution complete: ${executionResults.size} commands executed")
        storage.set(executionResultsKey, executionResults)

        executionResults
    }

/**
 * Node: Analyze results and decide next step (LLM)
 *
 * This LLM node:
 * 1. Analyzes execution results (build/test logs, errors)
 * 2. Diagnoses failures
 * 3. Suggests fixes if retry is needed
 * 4. Decides: SUCCESS / RETRY_NEEDED / FAILED
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeAnalyzeResultsAndDecide(
    model: LLModel
) =
    node<List<CommandExecutionResult>, ValidationAnalysis>("analyze-results-and-decide") { executionResults ->
        logger.info("LLM: Analyzing validation results and deciding next step")

        val retryCount = storage.get(dockerRetryCountKey) ?: 0
        val strategy = storage.get(validationStrategyKey)
            ?: throw IllegalStateException("Validation strategy not found")

        // Build analysis prompt
        val prompt = buildResultsAnalysisPrompt(
            strategy = strategy,
            executionResults = executionResults,
            retryCount = retryCount,
            maxRetries = MAX_RETRIES
        )

        // Call LLM to analyze using structured output
        val structuredResponse = llm.writeSession {
            appendPrompt {
                system(prompt)
            }
            requestLLMStructured<ValidationAnalysis>()
        }

        logger.debug("LLM structured analysis response received")

        // Extract analysis from structured response
        val analysis = structuredResponse.getOrElse { error ->
            logger.error("Failed to get structured validation analysis from LLM", error)
            throw IllegalStateException("Failed to analyze validation results: ${error.message}", error)
        }.structure

        logger.info("LLM analysis: ${analysis.overallStatus}, shouldRetry=${analysis.shouldRetry}")
        if (analysis.errorDiagnosis != null) {
            logger.info("Error diagnosis: ${analysis.errorDiagnosis}")
        }

        storage.set(validationAnalysisKey, analysis)

        analysis
    }

/**
 * Node: Apply fixes (Logic)
 *
 * Applies fixes suggested by LLM and increments retry counter.
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeApplyFixes() =
    node<ValidationAnalysis, ValidationStrategy>("apply-fixes") { analysis ->
        logger.info("Applying LLM-suggested fixes")

        val currentStrategy = storage.get(validationStrategyKey)
            ?: throw IllegalStateException("Current validation strategy not found")
        val retryCount = storage.get(dockerRetryCountKey) ?: 0

        // Increment retry counter
        storage.set(dockerRetryCountKey, retryCount + 1)
        logger.info("Retry attempt ${retryCount + 1}/$MAX_RETRIES")

        // Apply fixes from first suggestion
        val updatedStrategy = if (analysis.fixSuggestions.isNotEmpty()) {
            val fix = analysis.fixSuggestions.first()
            logger.info("Applying fix: ${fix.description} (type: ${fix.fixType})")

            currentStrategy.copy(
                dockerfileContent = fix.updatedDockerfile ?: currentStrategy.dockerfileContent,
                buildCommands = fix.updatedBuildCommands ?: currentStrategy.buildCommands,
                testCommands = fix.updatedTestCommands ?: currentStrategy.testCommands,
                approachDescription = "${currentStrategy.approachDescription}\n\nRetry ${retryCount + 1}: ${fix.description}"
            )
        } else {
            logger.warn("No fix suggestions provided, using current strategy")
            currentStrategy
        }

        storage.set(validationStrategyKey, updatedStrategy)

        updatedStrategy
    }

/**
 * Node: Generate final validation report (LLM)
 *
 * This LLM node generates a comprehensive human-readable report.
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeGenerateFinalReport(
    model: LLModel
) =
    node<ValidationAnalysis, FinalValidationReport>("generate-final-report") { analysis ->
        logger.info("LLM: Generating final validation report")

        val executionResults = storage.get(executionResultsKey) ?: emptyList()
        val retryCount = storage.get(dockerRetryCountKey) ?: 0

        // Build report generation prompt
        val prompt = buildReportGenerationPrompt(
            analysis = analysis,
            executionResults = executionResults,
            totalAttempts = retryCount + 1
        )

        // Call LLM to generate report using structured output
        val structuredResponse = llm.writeSession {
            appendPrompt {
                system(prompt)
            }
            requestLLMStructured<FinalValidationReport>()
        }

        logger.debug("LLM structured report response received")

        // Extract report from structured response
        val report = structuredResponse.getOrElse { error ->
            logger.error("Failed to get structured validation report from LLM", error)
            throw IllegalStateException("Failed to generate validation report: ${error.message}", error)
        }.structure

        logger.info("Final validation report generated: ${report.verdict}")
        storage.set(finalReportKey, report)

        report
    }

/**
 * Node: Skip validation (Logic)
 *
 * Creates a skip report when Docker is not available.
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeSkipValidation() =
    node<Boolean, FinalValidationReport>("skip-validation") { _ ->
        logger.info("Skipping Docker validation (Docker not available)")

        val report = FinalValidationReport(
            summary = "Docker validation skipped because Docker is not available on this system",
            buildStatus = "Not validated",
            testStatus = null,
            recommendations = listOf(
                "Install Docker to enable code validation",
                "Manual testing recommended before applying changes"
            ),
            totalAttempts = 0,
            verdict = "SKIPPED"
        )

        storage.set(finalReportKey, report)

        report
    }

/**
 * Node: Cleanup and finish (Logic)
 *
 * Cleans up Docker resources and produces final DockerValidationResult.
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeCleanupAndFinish() =
    node<FinalValidationReport, DockerValidationResult>("cleanup-and-finish") { report ->
        logger.info("Cleaning up Docker validation resources")

        val dockerAvailable = storage.get(dockerAvailableKey) ?: false
        val dockerToolSet = DockerToolSet()

        // Cleanup Docker image
        val imageName = storage.get(validationImageNameKey)
        if (imageName != null) {
            try {
                val cleanupResult = dockerToolSet.cleanupImage(imageName)
                if (cleanupResult.success) {
                    logger.info("Cleaned up Docker image: $imageName")
                } else {
                    logger.warn("Failed to cleanup Docker image: ${cleanupResult.message}")
                }
            } catch (e: Exception) {
                logger.warn("Error cleaning up Docker image: ${e.message}")
            }
        }

        // Cleanup validation directory
        val validationDir = storage.get(validationDirectoryKey)
        if (validationDir != null) {
            try {
                val cleanupResult = dockerToolSet.cleanupDirectory(validationDir)
                if (cleanupResult.success) {
                    logger.info("Cleaned up validation directory: $validationDir")
                } else {
                    logger.warn("Failed to cleanup validation directory: ${cleanupResult.message}")
                }
            } catch (e: Exception) {
                logger.warn("Error cleaning up validation directory: ${e.message}")
            }
        }

        // Build final result
        val result = if (!dockerAvailable) {
            DockerValidationResult(
                validated = false,
                dockerAvailable = false,
                buildPassed = null,
                testsPassed = null,
                errorMessage = "Docker is not available on this system"
            )
        } else {
            val executionResults = storage.get(executionResultsKey) ?: emptyList()
            val analysis = storage.get(validationAnalysisKey)

            val buildResults = executionResults.filter { it.command.contains("docker build") || it.command in storage.get(validationStrategyKey)?.buildCommands.orEmpty() }
            val testResults = executionResults.filter { it.command in storage.get(validationStrategyKey)?.testCommands.orEmpty() }

            val buildPassed = buildResults.all { it.success }
            val testsPassed = if (testResults.isNotEmpty()) testResults.all { it.success } else null

            val allLogs = executionResults.flatMap { it.stdout + it.stderr }
            val totalDuration = executionResults.sumOf { it.durationSeconds }

            DockerValidationResult(
                validated = true,
                dockerAvailable = true,
                buildPassed = buildPassed,
                testsPassed = testsPassed,
                buildLogs = allLogs,
                testLogs = emptyList(),
                errorMessage = analysis?.errorDiagnosis,
                durationSeconds = totalDuration
            )
        }

        logger.info("Docker validation complete: build=${result.buildPassed}, tests=${result.testsPassed}, duration=${result.durationSeconds}s")
        storage.set(dockerValidationResultKey, result)

        result
    }

// Helper functions

/**
 * Setup validation environment: copy project and apply modifications
 */
private fun setupValidationEnvironment(sessionPath: String, proposedChanges: List<ProposedChange>): String {
    logger.info("Setting up validation environment")

    try {
        // Create temp directory for validation
        val tempDir = Files.createTempDirectory("code-modifier-validation-").toFile()
        val validationDir = File(tempDir, "project")
        validationDir.mkdirs()

        logger.info("Created validation directory: ${validationDir.absolutePath}")

        // Copy project to temp directory
        val sessionDir = File(sessionPath)
        copyDirectory(sessionDir, validationDir)

        // Apply modifications
        logger.info("Applying ${proposedChanges.size} modifications")
        for (change in proposedChanges) {
            val targetFile = File(validationDir, change.filePath)

            when (change.changeType) {
                ChangeType.CREATE -> {
                    targetFile.parentFile.mkdirs()
                    targetFile.writeText(change.newContent)
                    logger.debug("Created file: ${change.filePath}")
                }
                ChangeType.MODIFY, ChangeType.REFACTOR -> {
                    if (targetFile.exists()) {
                        targetFile.writeText(change.newContent)
                        logger.debug("Modified file: ${change.filePath}")
                    } else {
                        logger.warn("File to modify not found: ${change.filePath}")
                    }
                }
                ChangeType.DELETE -> {
                    if (targetFile.exists()) {
                        targetFile.delete()
                        logger.debug("Deleted file: ${change.filePath}")
                    }
                }
                ChangeType.RENAME -> {
                    val newFile = File(validationDir, change.newContent)
                    if (targetFile.exists()) {
                        newFile.parentFile.mkdirs()
                        targetFile.renameTo(newFile)
                        logger.debug("Renamed file: ${change.filePath} -> ${change.newContent}")
                    }
                }
            }
        }

        logger.info("Validation environment setup complete")
        return validationDir.absolutePath

    } catch (e: Exception) {
        logger.error("Failed to setup validation environment", e)
        throw e
    }
}

/**
 * Copy directory recursively
 */
private fun copyDirectory(source: File, target: File) {
    if (source.isDirectory) {
        target.mkdirs()
        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                copyDirectory(file, targetFile)
            } else {
                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    } else {
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * List project files for analysis
 */
private fun listProjectFiles(directory: String): List<String> {
    val dir = File(directory)
    return dir.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(dir).path }
        .filter { !it.startsWith(".git") && !it.contains("/node_modules/") && !it.contains("/build/") }
        .take(50) // Limit to avoid overwhelming LLM
        .toList()
}

/**
 * Build validation planning prompt for LLM
 */
private fun buildValidationPlanningPrompt(
    projectFiles: List<String>,
    modifiedFiles: List<String>
): String {
    return """
You are a Docker validation expert. Analyze this project and create a validation strategy.

**Project Files:**
${projectFiles.joinToString("\n")}

**Modified Files:**
${modifiedFiles.joinToString("\n")}

**Your Task:**
1. Analyze the project type (Kotlin/Gradle, Java/Maven, Node/npm, Python, etc.)
2. Determine what dependencies are needed
3. Create a Dockerfile that can build and test this project
4. Specify build commands to run (compile, build)
5. Specify test commands to run (unit tests, integration tests)

**Output Format (JSON only, no markdown):**
{
  "approach_description": "Description of your validation approach",
  "project_type_analysis": "Detailed analysis of project type and structure",
  "dockerfile_content": "Complete Dockerfile content as a string with \n for newlines",
  "build_commands": ["command1", "command2"],
  "test_commands": ["test command1", "test command2"],
  "expected_outcomes": "What you expect to happen"
}

**Important:**
- Return ONLY valid JSON, no markdown code blocks
- dockerfile_content should be a single string with \n for line breaks
- Include all necessary dependencies and build tools
- Keep commands simple and robust
- If tests are not applicable, use empty array for test_commands
""".trim()
}

/**
 * Build results analysis prompt for LLM
 */
private fun buildResultsAnalysisPrompt(
    strategy: ValidationStrategy,
    executionResults: List<CommandExecutionResult>,
    retryCount: Int,
    maxRetries: Int
): String {
    val resultsFormatted = executionResults.joinToString("\n\n") { result ->
        """
Command: ${result.command}
Success: ${result.success}
Exit Code: ${result.exitCode}
Duration: ${result.durationSeconds}s
STDOUT:
${result.stdout.joinToString("\n")}
STDERR:
${result.stderr.joinToString("\n")}
        """.trim()
    }

    return """
You are a Docker validation expert. Analyze these validation results and decide the next step.

**Original Strategy:**
${strategy.approachDescription}

**Execution Results:**
$resultsFormatted

**Current Retry Count:** $retryCount
**Max Retries:** $maxRetries

**Your Task:**
1. Analyze the build and test results
2. Diagnose any errors
3. Decide if validation passed, needs retry, or failed
4. If retry is needed and retries available, suggest specific fixes

**Output Format (JSON only, no markdown):**
{
  "overall_status": "SUCCESS" or "RETRY_NEEDED" or "FAILED",
  "build_analysis": "Analysis of build results",
  "test_analysis": "Analysis of test results (or null if no tests)",
  "error_diagnosis": "Diagnosis of errors (or null if success)",
  "fix_suggestions": [
    {
      "description": "What to fix",
      "fix_type": "DOCKERFILE_MODIFICATION" or "BUILD_COMMAND_CHANGE" or "TEST_COMMAND_CHANGE" or "DEPENDENCY_FIX" or "CONFIGURATION_CHANGE",
      "updated_dockerfile": "Updated Dockerfile content (or null if not changing)",
      "updated_build_commands": ["new commands"] or null,
      "updated_test_commands": ["new commands"] or null
    }
  ],
  "should_retry": true or false,
  "retry_reason": "Why retry is needed (or null)"
}

**Decision Rules:**
- If all commands succeeded: status = SUCCESS, should_retry = false
- If errors can be fixed and retries available: status = RETRY_NEEDED, should_retry = true, provide fix_suggestions
- If no retries left or errors unfixable: status = FAILED, should_retry = false
- Return ONLY valid JSON, no markdown code blocks
""".trim()
}

/**
 * Build report generation prompt for LLM
 */
private fun buildReportGenerationPrompt(
    analysis: ValidationAnalysis,
    executionResults: List<CommandExecutionResult>,
    totalAttempts: Int
): String {
    return """
You are a Docker validation expert. Generate a final validation report.

**Validation Analysis:**
Status: ${analysis.overallStatus}
Build Analysis: ${analysis.buildAnalysis}
Test Analysis: ${analysis.testAnalysis ?: "N/A"}
Error Diagnosis: ${analysis.errorDiagnosis ?: "None"}

**Execution Summary:**
Total Attempts: $totalAttempts
Commands Executed: ${executionResults.size}

**Your Task:**
Generate a comprehensive, human-readable validation report.

**Output Format (JSON only, no markdown):**
{
  "summary": "Brief summary of validation outcome",
  "build_status": "Description of build status",
  "test_status": "Description of test status (or null if no tests)",
  "recommendations": ["Recommendation 1", "Recommendation 2"],
  "total_attempts": $totalAttempts,
  "verdict": "PASSED" or "FAILED" or "SKIPPED"
}

**Guidelines:**
- Be concise and actionable
- Highlight key issues if validation failed
- Suggest next steps for developers
- Return ONLY valid JSON, no markdown code blocks
""".trim()
}

