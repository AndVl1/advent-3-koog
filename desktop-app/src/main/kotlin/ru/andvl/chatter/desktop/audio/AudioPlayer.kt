package ru.andvl.chatter.desktop.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineEvent

/**
 * Audio player for playing audio files
 */
class AudioPlayer {
    private var clip: Clip? = null
    private var playbackThread: Thread? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    /**
     * Play audio file
     */
    suspend fun play(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Stop any current playback
            stop()

            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist: ${file.absolutePath}"))
            }

            val audioInputStream = AudioSystem.getAudioInputStream(file)
            val format = audioInputStream.format
            val info = DataLine.Info(Clip::class.java, format)

            if (!AudioSystem.isLineSupported(info)) {
                return@withContext Result.failure(IllegalStateException("Audio format not supported"))
            }

            clip = (AudioSystem.getLine(info) as Clip).apply {
                open(audioInputStream)

                // Set duration
                _duration.value = microsecondLength / 1000

                // Add listener for when playback completes
                addLineListener { event ->
                    if (event.type == LineEvent.Type.STOP) {
                        _isPlaying.value = false
                        _currentPosition.value = 0
                    }
                }

                start()
                _isPlaying.value = true
            }

            // Start position tracking
            playbackThread = Thread {
                while (_isPlaying.value && clip?.isRunning == true) {
                    _currentPosition.value = clip?.microsecondPosition?.div(1000) ?: 0
                    Thread.sleep(50)
                }
            }.apply {
                isDaemon = true
                start()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            _isPlaying.value = false
            Result.failure(e)
        }
    }

    /**
     * Pause playback
     */
    fun pause() {
        clip?.apply {
            if (isRunning) {
                stop()
                _isPlaying.value = false
            }
        }
    }

    /**
     * Resume playback
     */
    fun resume() {
        clip?.apply {
            if (!isRunning) {
                start()
                _isPlaying.value = true
            }
        }
    }

    /**
     * Stop playback
     */
    fun stop() {
        playbackThread?.interrupt()
        playbackThread = null

        clip?.apply {
            stop()
            close()
        }
        clip = null

        _isPlaying.value = false
        _currentPosition.value = 0
        _duration.value = 0
    }

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = _isPlaying.value

    /**
     * Seek to position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        clip?.apply {
            microsecondPosition = positionMs * 1000
            _currentPosition.value = positionMs
        }
    }
}
