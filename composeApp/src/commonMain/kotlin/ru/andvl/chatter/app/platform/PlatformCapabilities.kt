package ru.andvl.chatter.app.platform

expect object PlatformCapabilities {
    val supportsDocker: Boolean
    val supportsEmbeddings: Boolean
    val supportsVoiceInput: Boolean
}
