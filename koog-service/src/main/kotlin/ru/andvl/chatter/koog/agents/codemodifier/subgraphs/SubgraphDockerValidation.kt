package ru.andvl.chatter.koog.agents.codemodifier.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codemodifier.*
import ru.andvl.chatter.koog.model.codemodifier.*
import ru.andvl.chatter.koog.tools.DockerToolSet
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = LoggerFactory.getLogger("codemodifier-docker-validation")

private const val DOCKER_OPERATION_TIMEOUT = 300 // 5 minutes

/**
 * Subgraph: Docker Validation
 *
 * Flow:
 * 1. Check Docker availability
 * 2. Setup validation environment (clone to temp, apply modifications)
 * 3. Detect project type
 * 4. Generate Dockerfile
 * 5. Build Docker image
 * 6. Run validation (build + tests)
 * 7. Parse validation results
 * 8. Cleanup resources
 *
 * Input: ValidationCheckResult
 * Output: DockerValidationResult
 *
 * This subgraph is optional and can be skipped if Docker is not available.
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphDockerValidation():
        AIAgentSubgraphDelegate<ValidationCheckResult, DockerValidationResult> =
    subgraph(
        name = "docker-validation",
        tools = ToolRegistry {
            tools(DockerToolSet())
        }.tools
    ) {
        val nodeCheckDockerAvailable by nodeCheckDockerAvailable()
        val nodeSetupValidationEnvironment by nodeSetupValidationEnvironment()
        val nodeDetectProjectType by nodeDetectProjectType()
        val nodeGenerateDockerfile by nodeGenerateDockerfile()
        val nodeBuildImage by nodeBuildImage()
        val nodeRunValidation by nodeRunValidation()
        val nodeParseValidationResults by nodeParseValidationResults()
        val nodeHandleBuildFailure by nodeHandleBuildFailure()
        val nodeCleanupResources by nodeCleanupResources()
        val nodeSkipValidation by nodeSkipValidation()

        // Start -> Check Docker
        edge(nodeStart forwardTo nodeCheckDockerAvailable)

        // If Docker available -> Setup environment
        edge(nodeCheckDockerAvailable forwardTo nodeSetupValidationEnvironment onCondition { available: Boolean ->
            available
        })

        // If Docker not available -> Skip
        edge(nodeCheckDockerAvailable forwardTo nodeSkipValidation onCondition { available: Boolean ->
            !available
        })

        // Setup -> Detect project type
        edge(nodeSetupValidationEnvironment forwardTo nodeDetectProjectType)

        // Detect type -> Generate Dockerfile
        edge(nodeDetectProjectType forwardTo nodeGenerateDockerfile)

        // Generate -> Build image
        edge(nodeGenerateDockerfile forwardTo nodeBuildImage)

        // Build -> Run validation (on success)
        edge(nodeBuildImage forwardTo nodeRunValidation onCondition { buildSuccess: Boolean ->
            buildSuccess
        })

        // Build -> Handle failure (on failure)
        edge(nodeBuildImage forwardTo nodeHandleBuildFailure onCondition { buildSuccess: Boolean ->
            !buildSuccess
        })

        // Handle build failure -> Cleanup
        edge(nodeHandleBuildFailure forwardTo nodeCleanupResources)

        // Run validation -> Parse results
        edge(nodeRunValidation forwardTo nodeParseValidationResults)

        // Parse results -> Cleanup
        edge(nodeParseValidationResults forwardTo nodeCleanupResources)

        // Cleanup -> Finish
        edge(nodeCleanupResources forwardTo nodeFinish)

        // Skip -> Finish
        edge(nodeSkipValidation forwardTo nodeFinish)
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

        if (available) {
            logger.info("Docker is available: ${result.version}")
        } else {
            logger.warn("Docker is not available: ${result.message}")
        }

        available
    }

/**
 * Node: Setup validation environment
 *
 * Clones repository to temp directory and applies modifications
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeSetupValidationEnvironment() =
    node<Boolean, String>("setup-validation-environment") { _ ->
        logger.info("Setting up validation environment")

        try {
            val sessionPath = storage.get(sessionPathKey)
                ?: throw IllegalStateException("Session path not found")
            val proposedChanges = storage.get(proposedChangesKey)
                ?: throw IllegalStateException("Proposed changes not found")

            // Create temp directory for validation
            val tempDir = Files.createTempDirectory("code-modifier-validation-").toFile()
            val validationDir = File(tempDir, "project")
            validationDir.mkdirs()

            logger.info("Created validation directory: ${validationDir.absolutePath}")

            // Copy project to temp directory
            val sessionDir = File(sessionPath)
            copyDirectory(sessionDir, validationDir)

            // Apply modifications (write new content to files)
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
                        // For rename, we assume newContent contains the new path
                        val newFile = File(validationDir, change.newContent)
                        if (targetFile.exists()) {
                            newFile.parentFile.mkdirs()
                            targetFile.renameTo(newFile)
                            logger.debug("Renamed file: ${change.filePath} -> ${change.newContent}")
                        }
                    }
                }
            }

            storage.set(validationDirectoryKey, validationDir.absolutePath)
            logger.info("Validation environment setup complete")

            validationDir.absolutePath
        } catch (e: Exception) {
            logger.error("Failed to setup validation environment", e)
            throw e
        }
    }

/**
 * Node: Detect project type
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeDetectProjectType() =
    node<String, ProjectType>("detect-project-type") { validationDir ->
        logger.info("Detecting project type in: $validationDir")

        val dir = File(validationDir)
        val filesInDir = dir.listFiles()?.map { it.name } ?: emptyList()

        logger.debug("Files in directory: ${filesInDir.joinToString(", ")}")

        // Try to detect project type by checking for marker files
        val detectedType = ProjectType.values()
            .filter { it != ProjectType.UNKNOWN }
            .firstOrNull { projectType ->
                // Check if ALL detection files are present
                projectType.detectionFiles.all { detectionFile ->
                    filesInDir.contains(detectionFile)
                }
            } ?: ProjectType.UNKNOWN

        logger.info("Detected project type: $detectedType")
        storage.set(detectedProjectTypeKey, detectedType)

        detectedType
    }

/**
 * Node: Generate Dockerfile
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeGenerateDockerfile() =
    node<ProjectType, Boolean>("generate-dockerfile") { projectType ->
        logger.info("Generating Dockerfile for project type: $projectType")

        val validationDir = storage.get(validationDirectoryKey)
            ?: throw IllegalStateException("Validation directory not found")

        val dockerToolSet = DockerToolSet()
        val result = dockerToolSet.generateDockerfile(
            directoryPath = validationDir,
            baseImage = projectType.baseImage,
            buildCommand = projectType.buildCommand,
            runCommand = projectType.testCommand ?: "echo 'No tests to run'",
            port = null
        )

        if (result.success) {
            logger.info("Dockerfile generated successfully at: ${result.dockerfilePath}")
            true
        } else {
            logger.error("Failed to generate Dockerfile: ${result.message}")
            false
        }
    }

/**
 * Node: Build Docker image
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeBuildImage() =
    node<Boolean, Boolean>("build-image") { dockerfileGenerated ->
        if (!dockerfileGenerated) {
            logger.error("Cannot build image, Dockerfile generation failed")
            return@node false
        }

        logger.info("Building Docker image")

        val validationDir = storage.get(validationDirectoryKey)
            ?: throw IllegalStateException("Validation directory not found")

        val imageName = "code-modifier-validation-${System.currentTimeMillis()}"
        storage.set(validationImageNameKey, imageName)

        val dockerToolSet = DockerToolSet()
        val result = dockerToolSet.buildDockerImage(
            directoryPath = validationDir,
            imageName = imageName
        )

        if (result.success) {
            logger.info("Docker image built successfully: $imageName (${result.durationSeconds}s)")

            // Store initial validation result with build info
            val partialResult = DockerValidationResult(
                validated = true,
                dockerAvailable = true,
                buildPassed = true,
                buildLogs = result.buildLogs,
                durationSeconds = result.durationSeconds
            )
            storage.set(dockerValidationResultKey, partialResult)

            true
        } else {
            logger.error("Docker image build failed: ${result.message}")

            // Store failure result
            val failureResult = DockerValidationResult(
                validated = true,
                dockerAvailable = true,
                buildPassed = false,
                buildLogs = result.buildLogs,
                errorMessage = result.message,
                durationSeconds = result.durationSeconds
            )
            storage.set(dockerValidationResultKey, failureResult)

            false
        }
    }

/**
 * Node: Run validation (build + tests)
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeRunValidation() =
    node<Boolean, Boolean>("run-validation") { _ ->
        logger.info("Running validation tests")

        val imageName = storage.get(validationImageNameKey)
            ?: throw IllegalStateException("Image name not found")
        val projectType = storage.get(detectedProjectTypeKey)
            ?: ProjectType.UNKNOWN

        if (projectType.testCommand == null) {
            logger.info("No test command for project type: $projectType, skipping tests")

            // Update validation result with test info
            val existingResult = storage.get(dockerValidationResultKey)
                ?: DockerValidationResult(validated = true, dockerAvailable = true)
            val updatedResult = existingResult.copy(testsPassed = null)
            storage.set(dockerValidationResultKey, updatedResult)

            return@node true
        }

        val dockerToolSet = DockerToolSet()
        val result = dockerToolSet.runDockerContainer(
            imageName = imageName,
            command = projectType.testCommand,
            timeoutSeconds = DOCKER_OPERATION_TIMEOUT
        )

        val testsPassed = result.success
        logger.info("Validation tests ${if (testsPassed) "PASSED" else "FAILED"} (exit code: ${result.exitCode})")

        // Update validation result with test info
        val existingResult = storage.get(dockerValidationResultKey)
            ?: DockerValidationResult(validated = true, dockerAvailable = true)
        val updatedResult = existingResult.copy(
            testsPassed = testsPassed,
            testLogs = result.logs,
            errorMessage = if (!testsPassed) result.message else existingResult.errorMessage,
            durationSeconds = existingResult.durationSeconds + result.durationSeconds
        )
        storage.set(dockerValidationResultKey, updatedResult)

        testsPassed
    }

/**
 * Node: Parse validation results
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeParseValidationResults() =
    node<Boolean, DockerValidationResult>("parse-validation-results") { testsPassed ->
        logger.info("Parsing validation results")

        val result = storage.get(dockerValidationResultKey)
            ?: DockerValidationResult(
                validated = true,
                dockerAvailable = true,
                buildPassed = false,
                errorMessage = "Validation result not found in storage"
            )

        logger.info("Validation summary: build=${result.buildPassed}, tests=${result.testsPassed}, duration=${result.durationSeconds}s")

        result
    }

/**
 * Node: Handle build failure
 *
 * Creates a DockerValidationResult for build failures
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeHandleBuildFailure() =
    node<Boolean, DockerValidationResult>("handle-build-failure") { _ ->
        logger.info("Handling build failure")

        val result = storage.get(dockerValidationResultKey)
            ?: DockerValidationResult(
                validated = true,
                dockerAvailable = true,
                buildPassed = false,
                errorMessage = "Build failed"
            )

        logger.warn("Build failed: ${result.errorMessage}")
        result
    }

/**
 * Node: Cleanup resources
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeCleanupResources() =
    node<DockerValidationResult, DockerValidationResult>("cleanup-resources") { validationResult ->
        logger.info("Cleaning up Docker validation resources")

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

        logger.info("Docker validation cleanup complete")
        validationResult
    }

/**
 * Node: Skip validation (when Docker not available)
 */
private fun AIAgentSubgraphBuilderBase<ValidationCheckResult, DockerValidationResult>.nodeSkipValidation() =
    node<Boolean, DockerValidationResult>("skip-validation") { _ ->
        logger.info("Skipping Docker validation (Docker not available)")

        val result = DockerValidationResult(
            validated = false,
            dockerAvailable = false,
            buildPassed = null,
            testsPassed = null,
            errorMessage = "Docker is not available on this system"
        )

        storage.set(dockerValidationResultKey, result)
        result
    }

/**
 * Helper function to copy directory recursively
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
