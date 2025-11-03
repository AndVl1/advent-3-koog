package ru.andvl.chatter.koog.mcp

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File
import kotlin.system.exitProcess

object McpProvider {

    suspend fun getGoogleDocsClient(): Client {
        val jarPath = "mcp/googledocs/build/libs/googledocs-0.1.0.jar"
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
            clientInfo = Implementation(name = "kotlin-googledocs-test-client", version = "1.0.0"),
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

        return client
    }

    private var githubClient: Client? = null
    private val githubMutex = Mutex()

    suspend fun getGithubClient(): Client {
        return githubClient ?: githubMutex.withLock {
            if (githubClient != null) {
                githubClient!!
            } else {
                createGithubClient()
                    .also { githubClient = it }
            }
        }
    }

    private suspend fun createGithubClient(): Client {
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

        client.connect(transport)

        return client
    }
}
