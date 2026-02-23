package ru.andvl.chatter.app.platform

import kotlinx.coroutines.flow.StateFlow

expect class AudioPlayer() {
    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    suspend fun play(filePath: String): Result<Unit>
    fun pause()
    fun resume()
    fun stop()
}
