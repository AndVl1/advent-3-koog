package ru.andvl.chatter.app.platform

import ru.andvl.chatter.app.BuildConfig

actual object EnvLoader {
    actual fun loadApiKey(key: String): String = when (key) {
        "OPENROUTER_API_KEY" -> BuildConfig.OPENROUTER_API_KEY
        else -> ""
    }
}
