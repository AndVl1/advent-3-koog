package ru.andvl.chatter.desktop.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.sound.sampled.*
import kotlin.math.sqrt

/**
 * Audio recorder for capturing audio from microphone
 */
class AudioRecorder {
    private var targetDataLine: TargetDataLine? = null
    private var audioThread: Thread? = null
    private var isRecording = false

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    companion object {
        // Audio format configuration
        private const val SAMPLE_RATE = 16000f // 16 kHz - good for speech
        private const val SAMPLE_SIZE_IN_BITS = 16
        private const val CHANNELS = 1 // Mono
        private const val SIGNED = true
        private const val BIG_ENDIAN = false

        /**
         * Get audio format for recording
         */
        fun getAudioFormat(): AudioFormat {
            return AudioFormat(
                SAMPLE_RATE,
                SAMPLE_SIZE_IN_BITS,
                CHANNELS,
                SIGNED,
                BIG_ENDIAN
            )
        }
    }

    /**
     * Start recording audio
     */
    suspend fun startRecording(outputFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                return@withContext Result.failure(IllegalStateException("Already recording"))
            }

            val audioFormat = getAudioFormat()
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)

            if (!AudioSystem.isLineSupported(info)) {
                return@withContext Result.failure(
                    IllegalStateException("Microphone not supported")
                )
            }

            targetDataLine = (AudioSystem.getLine(info) as TargetDataLine).apply {
                open(audioFormat)
                start()
            }

            isRecording = true
            _recordingDuration.value = 0L

            // Start recording thread with audio level monitoring
            audioThread = Thread {
                try {
                    val buffer = ByteArray(4096)
                    val byteArrayOutputStream = ByteArrayOutputStream()

                    while (isRecording) {
                        val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                        if (bytesRead > 0) {
                            byteArrayOutputStream.write(buffer, 0, bytesRead)

                            // Calculate audio level (RMS)
                            val level = calculateAudioLevel(buffer, bytesRead)
                            _audioLevel.value = level
                        }
                    }

                    // Write to file
                    byteArrayOutputStream.close()
                    val audioBytes = byteArrayOutputStream.toByteArray()
                    val audioFormat = getAudioFormat()
                    val audioInputStream = AudioInputStream(
                        audioBytes.inputStream(),
                        audioFormat,
                        audioBytes.size.toLong() / audioFormat.frameSize
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

            // Start duration tracking thread
            Thread {
                val startTime = System.currentTimeMillis()
                while (isRecording) {
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

    /**
     * Stop recording audio
     */
    suspend fun stopRecording(): Result<File?> = withContext(Dispatchers.IO) {
        try {
            if (!isRecording) {
                return@withContext Result.failure(IllegalStateException("Not recording"))
            }

            isRecording = false

            targetDataLine?.apply {
                stop()
                close()
            }
            targetDataLine = null

            audioThread?.join(1000)
            audioThread = null

            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Cancel recording and delete file
     */
    suspend fun cancelRecording(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        stopRecording()
        if (file.exists()) {
            file.delete()
        }
        Result.success(Unit)
    }

    /**
     * Calculate audio level (RMS - Root Mean Square) from buffer
     * Returns value between 0.0 and 1.0
     */
    private fun calculateAudioLevel(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var samples = 0

        // Process 16-bit samples (2 bytes per sample)
        for (i in 0 until length - 1 step 2) {
            // Convert two bytes to a 16-bit sample (little endian)
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += (sample * sample).toDouble()
            samples++
        }

        if (samples == 0) return 0f

        // Calculate RMS
        val rms = sqrt(sum / samples)

        // Normalize to 0.0 - 1.0 range (max value for 16-bit is 32768)
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)

        // Apply logarithmic scaling for better visual representation
        return if (normalized > 0) {
            (normalized * 3).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
}
