package ru.andvl.chatter.koog.agents.codemodifier.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codemodifier.*
import ru.andvl.chatter.koog.model.codemodifier.CodeModificationRequest
import ru.andvl.chatter.koog.model.codemodifier.ValidationResult
import java.io.File

private val logger = LoggerFactory.getLogger("codemodifier-request-validation")

/**
 * Subgraph: Request Validation
 *
 * Flow:
 * 1. Validate session existence
 * 2. Normalize file scope (resolve patterns, validate paths)
 *
 * Input: CodeModificationRequest
 * Output: ValidationResult
 */
internal suspend fun AIAgentGraphStrategyBuilder<CodeModificationRequest, *>.subgraphRequestValidation():
        AIAgentSubgraphDelegate<CodeModificationRequest, ValidationResult> =
    subgraph(name = "request-validation") {
        val nodeValidateSession by nodeValidateSession()
        val nodeNormalizeFileScope by nodeNormalizeFileScope()

        edge(nodeStart forwardTo nodeValidateSession)
        edge(nodeValidateSession forwardTo nodeNormalizeFileScope)
        edge(nodeNormalizeFileScope forwardTo nodeFinish)
    }

/**
 * Node: Validate session exists
 *
 * Checks that the session directory exists and is accessible.
 */
private fun AIAgentSubgraphBuilderBase<CodeModificationRequest, ValidationResult>.nodeValidateSession() =
    node<CodeModificationRequest, CodeModificationRequest>("validate-session") { request ->
        logger.info("Validating session: ${request.sessionId}")

        // Store request parameters
        storage.set(sessionIdKey, request.sessionId)
        storage.set(instructionsKey, request.instructions)
        storage.set(enableValidationKey, request.enableValidation)
        storage.set(maxChangesKey, request.maxChanges)
        storage.set(forceSkipDockerKey, request.forceSkipDocker)

        // SessionId is the repository path directly
        val repositoryPath = request.sessionId
        val repoDir = File(repositoryPath)

        if (!repoDir.exists() || !repoDir.isDirectory) {
            val errorMessage = "Session not found: Repository does not exist at path: $repositoryPath"
            logger.error(errorMessage)

            val errorResult = ValidationResult(
                isValid = false,
                errorMessage = errorMessage
            )
            storage.set(validationResultKey, errorResult)
            throw IllegalArgumentException(errorMessage)
        }

        logger.info("Session validated: $repositoryPath")
        storage.set(sessionPathKey, repositoryPath)

        request
    }

/**
 * Node: Normalize file scope
 *
 * Resolves file patterns and validates that all files are within the session directory.
 */
private fun AIAgentSubgraphBuilderBase<CodeModificationRequest, ValidationResult>.nodeNormalizeFileScope() =
    node<CodeModificationRequest, ValidationResult>("normalize-file-scope") { request ->
        logger.info("Normalizing file scope")

        val sessionPath = storage.get(sessionPathKey)!!
        val sessionDir = File(sessionPath)

        val normalizedFiles = if (request.fileScope.isNullOrEmpty()) {
            // No file scope specified - include all files in session
            logger.info("No file scope specified, including all files")
            getAllFilesInDirectory(sessionDir, sessionPath)
        } else {
            // Resolve patterns and validate paths
            logger.info("Resolving ${request.fileScope.size} file patterns")
            resolveFilePatterns(request.fileScope, sessionDir, sessionPath)
        }

        // Validate file count
        if (normalizedFiles.isEmpty()) {
            logger.warn("No files matched the specified scope")
            val errorResult = ValidationResult(
                isValid = false,
                sessionPath = sessionPath,
                errorMessage = "No files matched the specified scope"
            )
            storage.set(validationResultKey, errorResult)
            return@node errorResult
        }

        // Limit file count to prevent excessive processing
        val maxFiles = 20
        if (normalizedFiles.size > maxFiles) {
            logger.warn("File scope too large: ${normalizedFiles.size} files (max: $maxFiles)")
            val errorResult = ValidationResult(
                isValid = false,
                sessionPath = sessionPath,
                normalizedFileScope = normalizedFiles.take(maxFiles),
                errorMessage = "File scope too large: ${normalizedFiles.size} files (max: $maxFiles). Please narrow your scope."
            )
            storage.set(validationResultKey, errorResult)
            return@node errorResult
        }

        logger.info("File scope normalized: ${normalizedFiles.size} files")
        storage.set(normalizedFileScopeKey, normalizedFiles)

        val result = ValidationResult(
            isValid = true,
            sessionPath = sessionPath,
            normalizedFileScope = normalizedFiles
        )
        storage.set(validationResultKey, result)

        result
    }

/**
 * Get all files in directory recursively, excluding hidden and build directories
 */
private fun getAllFilesInDirectory(directory: File, basePath: String): List<String> {
    val skipDirs = setOf(".git", ".idea", ".vscode", "node_modules", "build", "target", "out", "bin", ".gradle")
    val result = mutableListOf<String>()

    fun traverse(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (!file.name.startsWith(".") && file.name !in skipDirs) {
                    traverse(file)
                }
            } else {
                if (!file.name.startsWith(".")) {
                    result.add(file.absolutePath.removePrefix("$basePath/"))
                }
            }
        }
    }

    traverse(directory)
    return result
}

/**
 * Resolve file patterns (glob-like) and validate paths
 */
private fun resolveFilePatterns(patterns: List<String>, sessionDir: File, basePath: String): List<String> {
    val result = mutableSetOf<String>()

    patterns.forEach { pattern ->
        when {
            // Exact file path
            pattern.contains("/") && !pattern.contains("*") -> {
                val file = File(sessionDir, pattern)
                if (file.exists() && file.isFile && file.absolutePath.startsWith(sessionDir.absolutePath)) {
                    result.add(pattern)
                }
            }
            // Simple glob pattern (e.g., "*.kt", "**/*.java")
            pattern.contains("*") -> {
                val regex = pattern
                    .replace(".", "\\.")
                    .replace("**", ".*")
                    .replace("*", "[^/]*")
                    .toRegex()

                getAllFilesInDirectory(sessionDir, basePath).forEach { file ->
                    if (regex.matches(file)) {
                        result.add(file)
                    }
                }
            }
            // Directory - include all files in it
            else -> {
                val dir = File(sessionDir, pattern)
                if (dir.exists() && dir.isDirectory) {
                    result.addAll(getAllFilesInDirectory(dir, basePath))
                }
            }
        }
    }

    return result.toList()
}
