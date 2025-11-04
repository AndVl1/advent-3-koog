package ru.andvl.chatter.shared.models

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Shared message model for communication between modules
 */
@Serializable
data class SharedMessage(
    val role: MessageRole,
    val content: String,
    val meta: MessageMeta,
    val timestamp: Instant = Clock.System.now()
)

/**
 * Message role enum
 */
@Serializable
enum class MessageRole {
    @SerialName("user")
    User,

    @SerialName("assistant")
    Assistant,

    @SerialName("system")
    System
}

/**
 * Message metadata
 */
@Serializable
data class MessageMeta(
    val extraData: JsonObject? = null
)
