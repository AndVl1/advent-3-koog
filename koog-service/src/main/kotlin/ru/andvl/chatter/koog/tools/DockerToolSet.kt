package ru.andvl.chatter.koog.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File

@LLMDescription("Tools for Docker operations: checking availability, building images, managing containers")
internal class DockerToolSet : ToolSet {
    private val logger = LoggerFactory.getLogger(DockerToolSet::class.java)
    private val workDir = File(System.getProperty("java.io.tmpdir"), "docker-builds")

    init {
        workDir.mkdirs()
    }

    @Tool("check-docker-availability")
    @LLMDescription("Check if Docker daemon is running and available on the system")
    fun checkDockerAvailability(): DockerAvailabilityResult {
        return try {
            // First check if Docker daemon is running with 'docker info'
            val infoProcess = ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}")
                .redirectErrorStream(true)
                .start()

            val serverVersion = infoProcess.inputStream.bufferedReader().readText().trim()
            val infoExitCode = infoProcess.waitFor()

            if (infoExitCode == 0 && serverVersion.isNotEmpty()) {
                logger.info("Docker daemon is running, version: $serverVersion")
                DockerAvailabilityResult(
                    available = true,
                    version = serverVersion,
                    message = "Docker daemon is running (version: $serverVersion)"
                )
            } else {
                // Docker CLI might be installed but daemon is not running
                val errorOutput = infoProcess.errorStream.bufferedReader().readText().trim()
                logger.warn("Docker daemon is not running. Exit code: $infoExitCode, Error: $errorOutput")
                DockerAvailabilityResult(
                    available = false,
                    version = null,
                    message = "Docker daemon is not running. Make sure Docker Desktop or Docker Engine is started."
                )
            }
        } catch (e: Exception) {
            logger.warn("Docker not available: ${e.message}")
            DockerAvailabilityResult(
                available = false,
                version = null,
                message = "Docker not available: ${e.message}. Make sure Docker is installed and running."
            )
        }
    }

    @Tool("clone-repository")
    @LLMDescription("Clone a GitHub repository to a temporary directory. Returns the path where repository was cloned.")
    fun cloneRepository(
        @LLMDescription("GitHub repository URL (e.g., https://github.com/user/repo)")
        repositoryUrl: String
    ): CloneResult {
        val repoName = repositoryUrl.substringAfterLast("/").removeSuffix(".git")
        val targetDir = File(workDir, "repo-$repoName-${System.currentTimeMillis()}")

        return try {
            logger.info("Cloning repository: $repositoryUrl to ${targetDir.absolutePath}")

            val process = ProcessBuilder("git", "clone", "--depth", "1", repositoryUrl, targetDir.absolutePath)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                logger.info("Repository cloned successfully to ${targetDir.absolutePath}")
                CloneResult(
                    success = true,
                    path = targetDir.absolutePath,
                    message = "Repository cloned successfully",
                    logs = output.lines().takeLast(5)
                )
            } else {
                logger.error("Failed to clone repository. Exit code: $exitCode")
                // Cleanup on failure
                targetDir.deleteRecursively()
                CloneResult(
                    success = false,
                    path = null,
                    message = "Failed to clone repository with exit code $exitCode",
                    logs = output.lines().takeLast(10)
                )
            }
        } catch (e: Exception) {
            logger.error("Clone error", e)
            targetDir.deleteRecursively()
            CloneResult(
                success = false,
                path = null,
                message = "Exception during clone: ${e.message}",
                logs = listOf(e.stackTraceToString().lines().take(5)).flatten()
            )
        }
    }

    @Tool("generate-dockerfile")
    @LLMDescription("Generate a Dockerfile in the specified directory based on project type configuration")
    fun generateDockerfile(
        @LLMDescription("Directory path where Dockerfile should be created")
        directoryPath: String,
        @LLMDescription("Base Docker image (e.g., node:18-alpine, python:3.9-slim)")
        baseImage: String,
        @LLMDescription("Build command to install dependencies (e.g., npm install, pip install -r requirements.txt)")
        buildCommand: String,
        @LLMDescription("Command to run the application (e.g., npm start, python app.py)")
        runCommand: String,
        @LLMDescription("Port to expose (optional)")
        port: Int? = null
    ): GenerateDockerfileResult {
        val dir = File(directoryPath)

        if (!dir.exists() || !dir.isDirectory) {
            return GenerateDockerfileResult(
                success = false,
                dockerfilePath = null,
                message = "Directory does not exist: $directoryPath"
            )
        }

        val dockerfilePath = File(dir, "Dockerfile")

        // Check if Dockerfile already exists
        if (dockerfilePath.exists()) {
            logger.info("Dockerfile already exists at ${dockerfilePath.absolutePath}")
            return GenerateDockerfileResult(
                success = true,
                dockerfilePath = dockerfilePath.absolutePath,
                message = "Dockerfile already exists, using existing file",
                generated = false
            )
        }

        val dockerfileContent = buildString {
            appendLine("FROM $baseImage")
            appendLine()
            appendLine("WORKDIR /app")
            appendLine()
            appendLine("# Copy project files")
            appendLine("COPY . .")
            appendLine()
            appendLine("# Build command")
            appendLine("RUN $buildCommand")
            appendLine()
            if (port != null) {
                appendLine("# Expose port")
                appendLine("EXPOSE $port")
                appendLine()
            }
            appendLine("# Run command")
            appendLine("CMD $runCommand")
        }

        return try {
            dockerfilePath.writeText(dockerfileContent)
            logger.info("Generated Dockerfile at ${dockerfilePath.absolutePath}")
            GenerateDockerfileResult(
                success = true,
                dockerfilePath = dockerfilePath.absolutePath,
                message = "Dockerfile generated successfully",
                generated = true
            )
        } catch (e: Exception) {
            logger.error("Failed to generate Dockerfile", e)
            GenerateDockerfileResult(
                success = false,
                dockerfilePath = null,
                message = "Failed to generate Dockerfile: ${e.message}"
            )
        }
    }

    @Tool("build-docker-image")
    @LLMDescription("Build a Docker image from a Dockerfile in the specified directory")
    fun buildDockerImage(
        @LLMDescription("Directory path containing the Dockerfile")
        directoryPath: String,
        @LLMDescription("Image name/tag (e.g., my-app:latest)")
        imageName: String = "chatter-build-${System.currentTimeMillis()}"
    ): BuildResult {
        val dir = File(directoryPath)

        if (!dir.exists() || !dir.isDirectory) {
            return BuildResult(
                success = false,
                imageName = null,
                message = "Directory does not exist: $directoryPath",
                buildLogs = emptyList(),
                durationSeconds = 0
            )
        }

        val dockerfilePath = File(dir, "Dockerfile")
        if (!dockerfilePath.exists()) {
            return BuildResult(
                success = false,
                imageName = null,
                message = "Dockerfile not found in directory",
                buildLogs = emptyList(),
                durationSeconds = 0
            )
        }

        return try {
            logger.info("Starting Docker build in $directoryPath with image name: $imageName")
            val startTime = System.currentTimeMillis()

            val process = ProcessBuilder(
                "docker", "build",
                "-t", imageName,
                "--no-cache",
                "."
            )
                .directory(dir)
                .redirectErrorStream(true)
                .start()

            val buildLogs = mutableListOf<String>()
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        buildLogs.add(it)
                        if (buildLogs.size % 20 == 0) {
                            logger.info("Docker build progress: ${buildLogs.size} lines")
                        }
                    }
                }
            }

            val exitCode = process.waitFor()
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()

            logger.info("Docker build completed with exit code: $exitCode, duration: ${duration}s")

            if (exitCode == 0) {
                BuildResult(
                    success = true,
                    imageName = imageName,
                    message = "Docker image built successfully",
                    buildLogs = buildLogs.takeLast(30),
                    durationSeconds = duration
                )
            } else {
                BuildResult(
                    success = false,
                    imageName = null,
                    message = "Docker build failed with exit code $exitCode",
                    buildLogs = buildLogs.takeLast(30),
                    durationSeconds = duration
                )
            }
        } catch (e: Exception) {
            logger.error("Docker build error", e)
            BuildResult(
                success = false,
                imageName = null,
                message = "Exception during Docker build: ${e.message}",
                buildLogs = listOf(e.stackTraceToString().lines().take(10)).flatten(),
                durationSeconds = 0
            )
        }
    }

    @Tool("get-image-size")
    @LLMDescription("Get the size of a Docker image")
    fun getImageSize(
        @LLMDescription("Docker image name to check size")
        imageName: String
    ): ImageSizeResult {
        return try {
            val process = ProcessBuilder("docker", "images", "--format", "{{.Size}}", imageName).start()
            val size = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && size.isNotEmpty()) {
                logger.info("Image $imageName size: $size")
                ImageSizeResult(
                    success = true,
                    size = size,
                    message = "Successfully retrieved image size"
                )
            } else {
                ImageSizeResult(
                    success = false,
                    size = null,
                    message = "Failed to get image size"
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to get image size: ${e.message}")
            ImageSizeResult(
                success = false,
                size = null,
                message = "Exception: ${e.message}"
            )
        }
    }

    @Tool("cleanup-image")
    @LLMDescription("Remove a Docker image to free up space")
    fun cleanupImage(
        @LLMDescription("Docker image name to remove")
        imageName: String
    ): CleanupResult {
        return try {
            logger.info("Cleaning up Docker image: $imageName")
            val process = ProcessBuilder("docker", "rmi", imageName).start()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                logger.info("Successfully removed Docker image: $imageName")
                CleanupResult(
                    success = true,
                    message = "Image removed successfully"
                )
            } else {
                CleanupResult(
                    success = false,
                    message = "Failed to remove image with exit code $exitCode"
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to cleanup image: ${e.message}")
            CleanupResult(
                success = false,
                message = "Exception: ${e.message}"
            )
        }
    }

    @Tool("run-docker-container")
    @LLMDescription("Run a command inside a Docker container and return the results")
    fun runDockerContainer(
        @LLMDescription("Docker image name to run")
        imageName: String,
        @LLMDescription("Command to execute inside the container (e.g., './gradlew test', 'npm test')")
        command: String,
        @LLMDescription("Timeout in seconds (default 300)")
        timeoutSeconds: Int = 300
    ): RunResult {
        return try {
            logger.info("Running Docker container with image: $imageName, command: $command")
            val startTime = System.currentTimeMillis()

            // Run container with --rm to automatically remove it after execution
            val process = ProcessBuilder(
                "docker", "run",
                "--rm",  // Automatically remove container when it exits
                imageName,
                "sh", "-c", command
            )
                .redirectErrorStream(true)
                .start()

            val runLogs = mutableListOf<String>()
            val reader = process.inputStream.bufferedReader()

            // Read output with timeout
            val timeoutMillis = timeoutSeconds * 1000L
            var line: String?
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                if (reader.ready()) {
                    line = reader.readLine()
                    if (line == null) break
                    runLogs.add(line)
                    if (runLogs.size % 50 == 0) {
                        logger.info("Docker run progress: ${runLogs.size} lines")
                    }
                } else if (!process.isAlive) {
                    // Process finished, read remaining output
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { runLogs.add(it) }
                    }
                    break
                } else {
                    Thread.sleep(100)
                }
            }

            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()

            // Check if timeout occurred
            if (process.isAlive) {
                logger.warn("Docker container timed out after ${timeoutSeconds}s")
                process.destroyForcibly()
                return RunResult(
                    success = false,
                    exitCode = -1,
                    logs = runLogs.takeLast(50),
                    message = "Container execution timed out after ${timeoutSeconds}s",
                    durationSeconds = duration
                )
            }

            val exitCode = process.waitFor()
            logger.info("Docker container completed with exit code: $exitCode, duration: ${duration}s")

            RunResult(
                success = exitCode == 0,
                exitCode = exitCode,
                logs = runLogs.takeLast(100),
                message = if (exitCode == 0) "Command executed successfully" else "Command failed with exit code $exitCode",
                durationSeconds = duration
            )
        } catch (e: Exception) {
            logger.error("Docker run error", e)
            RunResult(
                success = false,
                exitCode = -1,
                logs = listOf(e.stackTraceToString().lines().take(10)).flatten(),
                message = "Exception during container execution: ${e.message}",
                durationSeconds = 0
            )
        }
    }

    @Tool("cleanup-directory")
    @LLMDescription("Remove a temporary directory created during Docker operations")
    fun cleanupDirectory(
        @LLMDescription("Directory path to remove")
        directoryPath: String
    ): CleanupResult {
        val dir = File(directoryPath)

        // Safety check: only allow cleanup of directories in our work directory
        if (!dir.absolutePath.startsWith(workDir.absolutePath)) {
            return CleanupResult(
                success = false,
                message = "Cannot cleanup directory outside of Docker work directory"
            )
        }

        return try {
            if (dir.exists()) {
                dir.deleteRecursively()
                logger.info("Cleaned up directory: $directoryPath")
                CleanupResult(
                    success = true,
                    message = "Directory removed successfully"
                )
            } else {
                CleanupResult(
                    success = true,
                    message = "Directory does not exist, nothing to clean"
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to cleanup directory: ${e.message}")
            CleanupResult(
                success = false,
                message = "Exception: ${e.message}"
            )
        }
    }
}

// Result classes

@Serializable
data class DockerAvailabilityResult(
    @SerialName("available")
    val available: Boolean,
    @SerialName("version")
    val version: String? = null,
    @SerialName("message")
    val message: String
)

@Serializable
data class CloneResult(
    @SerialName("success")
    val success: Boolean,
    @SerialName("path")
    val path: String? = null,
    @SerialName("message")
    val message: String,
    @SerialName("logs")
    val logs: List<String> = emptyList()
)

@Serializable
data class GenerateDockerfileResult(
    @SerialName("success")
    val success: Boolean,
    @SerialName("dockerfile_path")
    val dockerfilePath: String? = null,
    @SerialName("message")
    val message: String,
    @SerialName("generated")
    val generated: Boolean = true
)

@Serializable
data class BuildResult(
    @SerialName("success")
    val success: Boolean,
    @SerialName("image_name")
    val imageName: String? = null,
    @SerialName("message")
    val message: String,
    @SerialName("build_logs")
    val buildLogs: List<String> = emptyList(),
    @SerialName("duration_seconds")
    val durationSeconds: Int
)

@Serializable
data class ImageSizeResult(
    @SerialName("success")
    val success: Boolean,
    @SerialName("size")
    val size: String? = null,
    @SerialName("message")
    val message: String
)

@Serializable
data class CleanupResult(
    @SerialName("success")
    val success: Boolean,
    @SerialName("message")
    val message: String
)

@Serializable
data class RunResult(
    @SerialName("success")
    val success: Boolean,
    @SerialName("exit_code")
    val exitCode: Int,
    @SerialName("logs")
    val logs: List<String>,
    @SerialName("message")
    val message: String,
    @SerialName("duration_seconds")
    val durationSeconds: Int
)
