package ru.andvl.chatter.koog.model.docker

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@LLMDescription("Docker environment configuration for building and running the project")
@Serializable
data class DockerEnvModel(
    @property:LLMDescription("Base Docker image to use for building (e.g., node:18-alpine, python:3.9-slim). Field name: base_image")
    @SerialName("base_image")
    val baseImage: String,
    @property:LLMDescription("Command to build/install dependencies (e.g., npm install, pip install -r requirements.txt). Field name: build_command")
    @SerialName("build_command")
    val buildCommand: String,
    @property:LLMDescription("Command to run the application (e.g., npm start, python app.py). Field name: run_command")
    @SerialName("run_command")
    val runCommand: String,
    @property:LLMDescription("Main application port if it's a web service (e.g., 3000, 8080). Optional. Field name: port")
    @SerialName("port")
    val port: Int? = null,
    @property:LLMDescription("Additional notes about Docker configuration or requirements. Optional. Field name: additional_notes")
    @SerialName("additional_notes")
    val additionalNotes: String? = null
)

@LLMDescription("Result of Docker build attempt with status, logs and metrics")
@Serializable
data class DockerBuildResult(
    @property:LLMDescription("Build status: SUCCESS, FAILED, or NOT_ATTEMPTED. Field name: build_status")
    @SerialName("build_status")
    val buildStatus: String,
    @property:LLMDescription("Last 20 lines of build logs for debugging. Field name: build_logs")
    @SerialName("build_logs")
    val buildLogs: List<String> = emptyList(),
    @property:LLMDescription("Size of the built Docker image if successful. Optional. Field name: image_size")
    @SerialName("image_size")
    val imageSize: String? = null,
    @property:LLMDescription("Build duration in seconds. Optional. Field name: build_duration_seconds")
    @SerialName("build_duration_seconds")
    val buildDurationSeconds: Int? = null,
    @property:LLMDescription("Error message if build failed. Optional. Field name: error_message")
    @SerialName("error_message")
    val errorMessage: String? = null
)

@LLMDescription("Complete Docker build information including environment and results")
@Serializable
data class DockerInfoModel(
    @property:LLMDescription("Docker environment configuration used for build. Field name: docker_env")
    @SerialName("docker_env")
    val dockerEnv: DockerEnvModel,
    @property:LLMDescription("Build execution result with status and logs. Field name: build_result")
    @SerialName("build_result")
    val buildResult: DockerBuildResult,
    @property:LLMDescription("Whether Dockerfile was auto-generated or already existed. Field name: dockerfile_generated")
    @SerialName("dockerfile_generated")
    val dockerfileGenerated: Boolean = false,
    @property:LLMDescription("Content of generated Dockerfile if applicable. Optional. Field name: generated_dockerfile_content")
    @SerialName("generated_dockerfile_content")
    val generatedDockerfileContent: String? = null
)
