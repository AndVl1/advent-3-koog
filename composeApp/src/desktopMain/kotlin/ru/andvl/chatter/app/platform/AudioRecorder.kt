package ru.andvl.chatter.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.sqrt

actual class AudioRecorder actual constructor() {
    private var targetDataLine: TargetDataLine? = null
    private var audioThread: Thread? = null
    @Volatile
    private var isRecordingInternal = false

    private val _recordingDuration = MutableStateFlow(0L)
    actual val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    actual val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    companion object {
        private const val SAMPLE_RATE = 16000f
        private const val SAMPLE_SIZE_IN_BITS = 16
        private const val CHANNELS = 1
        private const val SIGNED = true
        private const val BIG_ENDIAN = false

        fun getAudioFormat(): AudioFormat {
            return AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN)
        }
    }

    actual suspend fun startRecording(outputPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isRecordingInternal) {
                return@withContext Result.failure(IllegalStateException("Already recording"))
            }

            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            val audioFormat = getAudioFormat()
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)

            if (!AudioSystem.isLineSupported(info)) {
                return@withContext Result.failure(IllegalStateException("Microphone not supported"))
            }

            targetDataLine = (AudioSystem.getLine(info) as TargetDataLine).apply {
                open(audioFormat)
                start()
            }

            isRecordingInternal = true
            _recordingDuration.value = 0L

            audioThread = Thread {
                try {
                    val buffer = ByteArray(4096)
                    val byteArrayOutputStream = ByteArrayOutputStream()

                    while (isRecordingInternal) {
                        val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                        if (bytesRead > 0) {
                            byteArrayOutputStream.write(buffer, 0, bytesRead)
                            _audioLevel.value = calculateAudioLevel(buffer, bytesRead)
                        }
                    }

                    byteArrayOutputStream.close()
                    val audioBytes = byteArrayOutputStream.toByteArray()
                    val format = getAudioFormat()
                    val audioInputStream = AudioInputStream(
                        audioBytes.inputStream(),
                        format,
                        audioBytes.size.toLong() / format.frameSize
                    )
                    AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile)
                    audioInputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }.apply {
                isDaemon = true
                start()
            }

            Thread {
                val startTime = System.currentTimeMillis()
                while (isRecordingInternal) {
                    _recordingDuration.value = System.currentTimeMillis() - startTime
                    Thread.sleep(100)
                }
            }.apply {
                isDaemon = true
                start()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    actual suspend fun stopRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isRecordingInternal) {
                return@withContext Result.failure(IllegalStateException("Not recording"))
            }

            isRecordingInternal = false

            targetDataLine?.apply {
                stop()
                close()
            }
            targetDataLine = null

            audioThread?.join(1000)
            audioThread = null

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    actual fun isRecording(): Boolean = isRecordingInternal

    private fun calculateAudioLevel(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var samples = 0

        for (i in 0 until length - 1 step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += (sample * sample).toDouble()
            samples++
        }

        if (samples == 0) return 0f

        val rms = sqrt(sum / samples)
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        return if (normalized > 0) (normalized * 3).coerceIn(0f, 1f) else 0f
    }
}
