package ru.andvl.chatter.app.platform

import io.github.cdimascio.dotenv.Dotenv

actual object EnvLoader {
    actual fun loadApiKey(key: String): String {
        return try {
            val dotenv = Dotenv.configure().ignoreIfMissing().load()
            dotenv[key] ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
