package ru.andvl.chatter.app.platform

actual object PlatformCapabilities {
    actual val supportsDocker: Boolean = false
    actual val supportsEmbeddings: Boolean = false
    actual val supportsVoiceInput: Boolean = false  // stubs for now
}
