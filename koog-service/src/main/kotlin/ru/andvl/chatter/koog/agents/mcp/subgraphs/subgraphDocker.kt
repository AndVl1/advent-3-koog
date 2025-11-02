package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.model.docker.*
import ru.andvl.chatter.koog.model.tool.*
import java.io.File

private val dockerAnalysisKey = createStorageKey<GithubRepositoryAnalysisModel.SuccessAnalysisModel>("docker-analysis")
private val logger = LoggerFactory.getLogger("docker-subgraph")

internal fun AIAgentGraphStrategyBuilder<GithubRepositoryAnalysisModel.SuccessAnalysisModel, ToolChatResponse>.subgraphDocker():
        AIAgentSubgraphDelegate<GithubRepositoryAnalysisModel.SuccessAnalysisModel, ToolChatResponse> =
    subgraph("docker-build") {
        val nodeDockerSystemCheck by nodeDockerSystemCheck()
        val nodeDockerBuild by nodeDockerBuild()
        val nodeDockerResult by nodeDockerResult()

        edge(nodeStart forwardTo nodeDockerSystemCheck)
        edge(nodeDockerSystemCheck forwardTo nodeDockerBuild)
        edge(nodeDockerBuild forwardTo nodeDockerResult)
        edge(nodeDockerResult forwardTo nodeFinish)
    }

private fun AIAgentSubgraphBuilderBase<GithubRepositoryAnalysisModel.SuccessAnalysisModel, ToolChatResponse>.nodeDockerSystemCheck() =
    node<GithubRepositoryAnalysisModel.SuccessAnalysisModel, Boolean>("docker-system-check") { analysisResult ->
        storage.set(dockerAnalysisKey, analysisResult)

        logger.info("Checking Docker system availability...")

        val dockerAvailable = try {
            val process = ProcessBuilder("docker", "--version").start()
            val exitCode = process.waitFor()
            logger.info("Docker version check exit code: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            logger.warn("Docker not available: ${e.message}")
            false
        }

        logger.info("Docker available: $dockerAvailable")
        dockerAvailable
    }

private fun AIAgentSubgraphBuilderBase<GithubRepositoryAnalysisModel.SuccessAnalysisModel, ToolChatResponse>.nodeDockerBuild() =
    node<Boolean, DockerBuildResult>("docker-build") { dockerAvailable ->
        val analysisResult = storage.get(dockerAnalysisKey)!!

        if (!dockerAvailable || analysisResult.dockerEnv == null) {
            return@node DockerBuildResult(
                buildStatus = "NOT_ATTEMPTED",
                errorMessage = "Docker not available or not configured"
            )
        }

        val dockerEnv = analysisResult.dockerEnv
        logger.info("Starting Docker build with base image: ${dockerEnv.baseImage}")

        var tempDir: File? = null
        try {
            // 1. Создать временную директорию
            tempDir = createTempDir("docker-build-")
            logger.info("Created temp directory: ${tempDir.absolutePath}")

            // 2. Извлечь URL репозитория
            val repoUrl = extractRepoUrl(analysisResult.freeFormAnswer)
            if (repoUrl == null) {
                logger.error("Could not extract repository URL from analysis")
                return@node DockerBuildResult(
                    buildStatus = "FAILED",
                    errorMessage = "Could not extract repository URL from analysis"
                )
            }

            logger.info("Cloning repository: $repoUrl")

            // 3. Клонировать репозиторий
            val cloneProcess = ProcessBuilder("git", "clone", "--depth", "1", repoUrl, tempDir.absolutePath)
                .redirectErrorStream(true)
                .start()

            val cloneOutput = cloneProcess.inputStream.bufferedReader().readText()
            val cloneExitCode = cloneProcess.waitFor()

            if (cloneExitCode != 0) {
                logger.error("Failed to clone repository. Exit code: $cloneExitCode")
                return@node DockerBuildResult(
                    buildStatus = "FAILED",
                    errorMessage = "Failed to clone repository",
                    buildLogs = cloneOutput.lines().takeLast(5)
                )
            }

            logger.info("Repository cloned successfully")

            // 4. Создать Dockerfile если отсутствует
            val dockerFile = tempDir.resolve("Dockerfile")
            val dockerfileGenerated = !dockerFile.exists()

            if (dockerfileGenerated) {
                val dockerfileContent = generateDockerfile(dockerEnv)
                dockerFile.writeText(dockerfileContent)
                logger.info("Generated Dockerfile:\n$dockerfileContent")
            } else {
                logger.info("Using existing Dockerfile")
            }

            // 5. Запустить Docker build
            val startTime = System.currentTimeMillis()
            val imageName = "chatter-test-${System.currentTimeMillis()}"

            logger.info("Starting Docker build with image name: $imageName")

            val buildProcess = ProcessBuilder(
                "docker", "build",
                "-t", imageName,
                "--no-cache",
                "."
            )
                .directory(tempDir)
                .redirectErrorStream(true)
                .start()

            val buildLogs = mutableListOf<String>()
            buildProcess.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        buildLogs.add(it)
                        logger.info("Docker build: $it")
                    }
                }
            }

            val buildExitCode = buildProcess.waitFor()
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()

            logger.info("Docker build completed with exit code: $buildExitCode, duration: ${duration}s")

            // 6. Получить размер образа (если успешно)
            val imageSize = if (buildExitCode == 0) {
                getImageSize(imageName)
            } else null

            // 7. Cleanup образа
            if (buildExitCode == 0) {
                try {
                    ProcessBuilder("docker", "rmi", imageName).start().waitFor()
                    logger.info("Cleaned up Docker image: $imageName")
                } catch (e: Exception) {
                    logger.warn("Failed to cleanup Docker image: ${e.message}")
                }
            }

            // 8. Вернуть результат
            DockerBuildResult(
                buildStatus = if (buildExitCode == 0) "SUCCESS" else "FAILED",
                buildLogs = buildLogs.takeLast(20),
                imageSize = imageSize,
                buildDurationSeconds = duration,
                errorMessage = if (buildExitCode != 0) {
                    "Docker build failed with exit code $buildExitCode. Check logs for details."
                } else null
            )

        } catch (e: Exception) {
            logger.error("Docker build error", e)
            DockerBuildResult(
                buildStatus = "FAILED",
                errorMessage = "Exception during Docker build: ${e.message}",
                buildLogs = listOf(e.stackTraceToString().lines().take(10)).flatten()
            )
        } finally {
            // Cleanup temp directory
            tempDir?.let {
                try {
                    it.deleteRecursively()
                    logger.info("Cleaned up temp directory")
                } catch (e: Exception) {
                    logger.warn("Failed to cleanup temp directory: ${e.message}")
                }
            }
        }
    }

private fun AIAgentSubgraphBuilderBase<GithubRepositoryAnalysisModel.SuccessAnalysisModel, ToolChatResponse>.nodeDockerResult() =
    node<DockerBuildResult, ToolChatResponse>("docker-result") { buildResult ->
        val analysisResult = storage.get(dockerAnalysisKey)!!

        val dockerInfo = if (analysisResult.dockerEnv != null) {
            DockerInfoModel(
                dockerEnv = analysisResult.dockerEnv,
                buildResult = buildResult,
                dockerfileGenerated = buildResult.buildStatus != "NOT_ATTEMPTED"
            )
        } else null

        ToolChatResponse(
            response = analysisResult.freeFormAnswer +
                if (dockerInfo != null) "\n\n${formatDockerResults(dockerInfo)}" else "",
            shortSummary = analysisResult.shortSummary,
            toolCalls = emptyList(),
            originalMessage = null,
            tokenUsage = null,
            repositoryReview = analysisResult.repositoryReview,
            requirements = null,
            dockerInfo = dockerInfo
        )
    }

private fun generateDockerfile(dockerEnv: DockerEnvModel): String {
    return """
FROM ${dockerEnv.baseImage}

WORKDIR /app

# Copy project files
COPY . .

# Build command
RUN ${dockerEnv.buildCommand}

# Expose port if specified
${if (dockerEnv.port != null) "EXPOSE ${dockerEnv.port}" else "# No port specified"}

# Run command
CMD ${dockerEnv.runCommand}
    """.trimIndent()
}

private fun extractRepoUrl(analysis: String): String? {
    val pattern = "https://github\\.com/[\\w.-]+/[\\w.-]+".toRegex()
    return pattern.find(analysis)?.value
}

private fun getImageSize(imageName: String): String? {
    return try {
        val process = ProcessBuilder("docker", "images", "--format", "{{.Size}}", imageName).start()
        if (process.waitFor() == 0) {
            process.inputStream.bufferedReader().readText().trim()
        } else null
    } catch (e: Exception) {
        logger.warn("Failed to get image size: ${e.message}")
        null
    }
}

private fun formatDockerResults(dockerInfo: DockerInfoModel): String {
    val result = dockerInfo.buildResult
    return buildString {
        appendLine("## 🐳 Docker Build Results")
        appendLine()
        appendLine("**Docker Environment:**")
        appendLine("- Base Image: `${dockerInfo.dockerEnv.baseImage}`")
        appendLine("- Build Command: `${dockerInfo.dockerEnv.buildCommand}`")
        appendLine("- Run Command: `${dockerInfo.dockerEnv.runCommand}`")
        if (dockerInfo.dockerEnv.port != null) {
            appendLine("- Port: `${dockerInfo.dockerEnv.port}`")
        }
        if (dockerInfo.dockerEnv.additionalNotes != null) {
            appendLine("- Notes: ${dockerInfo.dockerEnv.additionalNotes}")
        }
        appendLine()

        appendLine("**Build Status:** ${result.buildStatus}")
        if (result.buildDurationSeconds != null) {
            appendLine("**Duration:** ${result.buildDurationSeconds}s")
        }
        if (result.imageSize != null) {
            appendLine("**Image Size:** ${result.imageSize}")
        }
        if (dockerInfo.dockerfileGenerated) {
            appendLine("**Dockerfile:** Generated automatically")
        }

        if (result.errorMessage != null) {
            appendLine()
            appendLine("**Error:**")
            appendLine("```")
            appendLine(result.errorMessage)
            appendLine("```")
        }

        if (result.buildLogs.isNotEmpty()) {
            appendLine()
            appendLine("<details>")
            appendLine("<summary>Build Logs (last 20 lines)</summary>")
            appendLine()
            appendLine("```")
            result.buildLogs.forEach { log ->
                appendLine(log)
            }
            appendLine("```")
            appendLine("</details>")
        }
    }
}
