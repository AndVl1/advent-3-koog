package ru.andvl.chatter.app.platform

import android.content.Context
import java.io.File

actual object FileSaver {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context =
        appContext ?: error("FileSaver.init(context) must be called before use")

    actual fun saveReport(content: String, fileName: String): Result<String> = runCatching {
        val dir = File(requireContext().filesDir, "reports").also { it.mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        file.absolutePath
    }

    actual fun saveChatHistory(json: String, filePath: String): Result<Unit> = runCatching {
        val file = File(requireContext().filesDir, "chat_history.json")
        file.writeText(json)
    }

    actual fun readChatHistory(filePath: String): Result<String?> = runCatching {
        val file = File(requireContext().filesDir, "chat_history.json")
        if (file.exists()) file.readText() else null
    }

    actual fun createVoiceRecordingPath(timestamp: Long): String {
        val dir = File(requireContext().cacheDir, "recordings").also { it.mkdirs() }
        return File(dir, "recording_$timestamp.wav").absolutePath
    }

    actual fun getDefaultChatHistoryPath(): String {
        return File(requireContext().filesDir, "chat_history.json").absolutePath
    }

    actual fun getLogsDir(): String {
        return File(requireContext().filesDir, "logs").also { it.mkdirs() }.absolutePath
    }
}
