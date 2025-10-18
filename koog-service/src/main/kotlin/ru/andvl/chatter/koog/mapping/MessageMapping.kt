package ru.andvl.chatter.koog.mapping

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ru.andvl.chatter.shared.models.MessageRole
import ru.andvl.chatter.shared.models.SharedMessage

/**
 * Mapper functions for converting between shared models and Koog models
 */

/**
 * Convert SharedMessage to Koog Message
 */
fun SharedMessage.toKoogMessage(): Message {
    return when (role) {
        MessageRole.User -> Message.User(
            content = content,
            metaInfo = RequestMetaInfo(timestamp)
        )
        MessageRole.Assistant -> Message.Assistant(
            content = content,
            metaInfo = ResponseMetaInfo(timestamp)
        )
        MessageRole.System -> Message.System(
            content = content,
            metaInfo = RequestMetaInfo(timestamp)
        )
    }
}

/**
 * Convert Koog Message to SharedMessage
 */
fun Message.toSharedMessage(): SharedMessage {
    return when (this) {
        is Message.User -> SharedMessage(
            role = MessageRole.User,
            content = content,
            meta = ru.andvl.chatter.shared.models.MessageMeta()
        )
        is Message.Assistant -> SharedMessage(
            role = MessageRole.Assistant,
            content = content,
            meta = ru.andvl.chatter.shared.models.MessageMeta()
        )
        is Message.System -> SharedMessage(
            role = MessageRole.System,
            content = content,
            meta = ru.andvl.chatter.shared.models.MessageMeta()
        )
        else -> SharedMessage(
            role = MessageRole.System,
            content = content.toString(),
            meta = ru.andvl.chatter.shared.models.MessageMeta()
        )
    }
}

/**
 * Convert list of SharedMessage to list of Koog Message
 */
fun List<SharedMessage>.toKoogMessages(): List<Message> {
    return map { it.toKoogMessage() }
}

/**
 * Convert list of Koog Message to list of SharedMessage
 */
fun List<Message>.toSharedMessages(): List<SharedMessage> {
    return map { it.toSharedMessage() }
}