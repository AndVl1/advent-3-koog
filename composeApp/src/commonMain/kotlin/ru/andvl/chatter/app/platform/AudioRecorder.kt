package ru.andvl.chatter.app.platform

import kotlinx.coroutines.flow.StateFlow

expect class AudioRecorder() {
    val recordingDuration: StateFlow<Long>
    val audioLevel: StateFlow<Float>
    suspend fun startRecording(outputPath: String): Result<Unit>
    suspend fun stopRecording(): Result<Unit>
    fun isRecording(): Boolean
}
