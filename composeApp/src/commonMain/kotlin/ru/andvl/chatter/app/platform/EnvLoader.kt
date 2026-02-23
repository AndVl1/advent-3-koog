package ru.andvl.chatter.app.platform

expect object EnvLoader {
    fun loadApiKey(key: String): String
}
