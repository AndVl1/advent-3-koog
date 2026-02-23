package ru.andvl.chatter.app.platform

import java.io.File

actual object FileSaver {
    actual fun saveReport(content: String, fileName: String): Result<String> {
        return try {
            val downloadsDir = File(System.getProperty("user.home"), "Downloads")
            val reportsDir = File(downloadsDir, "ChatterReports")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            val file = File(reportsDir, fileName)
            file.writeText(content)
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    actual fun saveChatHistory(json: String, filePath: String): Result<Unit> {
        return try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(json)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    actual fun readChatHistory(filePath: String): Result<String?> {
        return try {
            val file = File(filePath)
            if (file.exists()) Result.success(file.readText()) else Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    actual fun createVoiceRecordingPath(timestamp: Long): String {
        val voiceDir = File("./voice_recordings")
        voiceDir.mkdirs()
        return "${voiceDir.absolutePath}/recording_$timestamp.wav"
    }

    actual fun getDefaultChatHistoryPath(): String = "./chat_history.json"

    actual fun getLogsDir(): String = "./logs"
}
