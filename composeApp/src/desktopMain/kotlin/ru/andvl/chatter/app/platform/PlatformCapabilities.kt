package ru.andvl.chatter.app.platform

actual object PlatformCapabilities {
    actual val supportsDocker: Boolean = true
    actual val supportsEmbeddings: Boolean = true
    actual val supportsVoiceInput: Boolean = true
}
