package ru.andvl.mcp.github

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File
import kotlin.system.exitProcess

/**
 * Тест Kotlin MCP сервера для GitHub API
 *
 * Тестирует Kotlin реализацию MCP сервера с инструментами:
 * - get-repo-base-info
 * - hello-world
 *
 * Перед запуском: ./gradlew :mcp:github:build
 */
fun main() = runBlocking {
    println("🧪 Тестирование GitHub клиента (Kotlin MCP Server)...")

    // Правильный относительный путь к JAR файлу
    val jarPath = "mcp/github/build/libs/github-0.1.0.jar"

    // Проверяем существование JAR файла
    val jarFile = File(jarPath)
    if (!jarFile.exists()) {
        println("❌ JAR файл не найден: $jarPath")
        println("Абсолютный путь: ${jarFile.absolutePath}")
        println("Текущая директория: ${File(".").absolutePath}")
        println("💡 Запустите: ./gradlew :mcp:github:build")
        exitProcess(1)
    }

    println("✅ JAR файл найден: $jarPath")

    println("🚀 Запуск Kotlin MCP сервера...")
    val process = ProcessBuilder("java", "-jar", jarFile.absolutePath)
        .redirectErrorStream(false) // Разделяем stdout и stderr
        .start()

    // Даем серверу время на запуск
    println("⏳ Ожидание запуска сервера...")
    delay(2000)

    // Проверяем, что процесс еще работает
    if (!process.isAlive) {
        println("❌ Процесс сервера завершился с кодом: ${process.exitValue()}")
        val errorOutput = process.errorStream.bufferedReader().readText()
        val stdOutput = process.inputStream.bufferedReader().readText()
        println("Stderr: $errorOutput")
        println("Stdout: $stdOutput")
        exitProcess(1)
    } else {
        // Читаем stderr для диагностики (неблокирующе)
        if (process.errorStream.available() > 0) {
            val errorOutput = process.errorStream.bufferedReader().readLine()
            println("📋 Server log: $errorOutput")
        }
    }

    println("📦 Подключение к MCP серверу...")
    val transport = StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered()
    )

    val client = Client(
        clientInfo = Implementation(name = "kotlin-github-test-client", version = "1.0.0"),
    )

    try {
        println("🔌 Подключение к серверу...")
        client.connect(transport)
        println("✅ Подключено успешно!")

        // Give a moment for initialization
        delay(1000)
    } catch (e: Exception) {
        println("❌ Ошибка подключения: ${e.message}")
        e.printStackTrace()
        process.destroy()
        exitProcess(1)
    }

    try {
        println("🔍 Получение списка инструментов...")
        val toolsList = client.listTools()?.tools?.map { it.name }
        println("Available Tools = $toolsList")

        if (toolsList?.contains("hello-world") == true) {
            println("🔧 Вызов инструмента hello-world...")
            val result = client.callTool("hello-world", mapOf("name" to "Andrey"))
                ?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат: ${result?.joinToString()}")
        } else {
            println("⚠️ Инструмент hello-world не найден")
        }

        if (toolsList?.contains("get-repo-base-info") == true) {
            println("🔧 Вызов инструмента get-repo-base-info...")
            val result = client.callTool("get-repo-base-info", mapOf(
                "repository" to "AndVl1/SnakeGame"
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат: ${result?.joinToString()}")
        } else {
            println("⚠️ Инструмент get-repo-base-info не найден")
        }

    } catch (e: Exception) {
        println("❌ Ошибка вызова инструмента: ${e.message}")
        e.printStackTrace()
    } finally {
        println("🔚 Закрытие соединения...")
        try {
            client.close()
        } catch (e: Exception) {
            println("⚠️ Ошибка закрытия клиента: ${e.message}")
        }

        println("🛑 Остановка сервера...")
        process.destroy()
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            println("⚠️ Принудительное завершение процесса...")
            process.destroyForcibly()
        }
    }

    println("\n✅ Тестирование завершено")
}
