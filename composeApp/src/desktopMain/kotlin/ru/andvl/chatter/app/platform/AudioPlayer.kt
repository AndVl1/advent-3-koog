package ru.andvl.chatter.app.platform

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

actual class AudioPlayer actual constructor() {
    private var clip: Clip? = null
    private var playbackThread: Thread? = null

    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    actual val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    actual val duration: StateFlow<Long> = _duration.asStateFlow()

    actual suspend fun play(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stop()

            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist: $filePath"))
            }

            val audioInputStream = AudioSystem.getAudioInputStream(file)
            val format = audioInputStream.format
            val info = DataLine.Info(Clip::class.java, format)

            if (!AudioSystem.isLineSupported(info)) {
                return@withContext Result.failure(IllegalStateException("Audio format not supported"))
            }

            clip = (AudioSystem.getLine(info) as Clip).apply {
                open(audioInputStream)
                _duration.value = microsecondLength / 1000

                addLineListener { event ->
                    if (event.type == LineEvent.Type.STOP) {
                        _isPlaying.value = false
                        _currentPosition.value = 0
                    }
                }

                start()
                _isPlaying.value = true
            }

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

    actual fun pause() {
        clip?.apply {
            if (isRunning) {
                stop()
                _isPlaying.value = false
            }
        }
    }

    actual fun resume() {
        clip?.apply {
            if (!isRunning) {
                start()
                _isPlaying.value = true
            }
        }
    }

    actual fun stop() {
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
}
