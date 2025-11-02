package ru.andvl.chatter.shared.models.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DockerEnvDto(
    @SerialName("base_image")
    val baseImage: String,
    @SerialName("build_command")
    val buildCommand: String,
    @SerialName("run_command")
    val runCommand: String,
    @SerialName("port")
    val port: Int? = null,
    @SerialName("additional_notes")
    val additionalNotes: String? = null
)

@Serializable
data class DockerBuildResultDto(
    @SerialName("build_status")
    val buildStatus: String,
    @SerialName("build_logs")
    val buildLogs: List<String> = emptyList(),
    @SerialName("image_size")
    val imageSize: String? = null,
    @SerialName("build_duration_seconds")
    val buildDurationSeconds: Int? = null,
    @SerialName("error_message")
    val errorMessage: String? = null
)

@Serializable
data class DockerInfoDto(
    @SerialName("docker_env")
    val dockerEnv: DockerEnvDto,
    @SerialName("build_result")
    val buildResult: DockerBuildResultDto,
    @SerialName("dockerfile_generated")
    val dockerfileGenerated: Boolean = false
)
