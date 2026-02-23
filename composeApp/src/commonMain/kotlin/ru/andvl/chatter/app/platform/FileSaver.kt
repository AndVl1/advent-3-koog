package ru.andvl.chatter.app.platform

expect object FileSaver {
    fun saveReport(content: String, fileName: String): Result<String>
    fun saveChatHistory(json: String, filePath: String): Result<Unit>
    fun readChatHistory(filePath: String): Result<String?>
    fun createVoiceRecordingPath(timestamp: Long): String
    fun getDefaultChatHistoryPath(): String
    fun getLogsDir(): String
}
