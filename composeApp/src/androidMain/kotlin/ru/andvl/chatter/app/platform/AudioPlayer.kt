package ru.andvl.chatter.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class AudioPlayer actual constructor() {
    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    actual val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    actual val duration: StateFlow<Long> = _duration.asStateFlow()

    actual suspend fun play(filePath: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Audio playback not yet implemented on Android"))

    actual fun pause() {}

    actual fun resume() {}

    actual fun stop() {}
}
