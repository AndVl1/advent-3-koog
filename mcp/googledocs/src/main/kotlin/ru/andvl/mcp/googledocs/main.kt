package ru.andvl.mcp.googledocs

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

fun main() = runBlocking {
    val logger = LoggerFactory.getLogger("GoogleDocsMcpServer")

    try {
        GoogleDocsMcpServer.runServer()
    } catch (e: Exception) {
        logger.error("❌ Ошибка запуска сервера: ${e.message}", e)
    }
}