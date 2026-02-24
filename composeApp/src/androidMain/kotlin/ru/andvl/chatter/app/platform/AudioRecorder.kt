package ru.andvl.chatter.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class AudioRecorder actual constructor() {
    private val _recordingDuration = MutableStateFlow(0L)
    actual val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    actual val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    actual suspend fun startRecording(outputPath: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Audio recording not yet implemented on Android"))

    actual suspend fun stopRecording(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Audio recording not yet implemented on Android"))

    actual fun isRecording(): Boolean = false
}
