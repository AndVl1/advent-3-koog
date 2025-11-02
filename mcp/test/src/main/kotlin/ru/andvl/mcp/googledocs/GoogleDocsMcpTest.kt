package ru.andvl.mcp.googledocs

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
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Тестирование Google Docs MCP сервера
 */
class GoogleDocsMcpTest {

    suspend fun testGoogleDocsMcpServer() {
        println("🧪 Testing Google Docs MCP Server...")
        val documentId = "1cqadlQLNHHTd2NgLxFZVzVdY61tQybRsSjsGpNNCF88" // android task 1

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

        try {
            println("🔍 Получение списка инструментов...")
            val toolsList = client.listTools()?.tools?.map { it.name }
            println("Available Tools = $toolsList")

            val docInfo = client.callTool(
                "get-document-info",
                mapOf("documentId" to documentId)
            )
                ?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Информация о документе:: ${docInfo?.joinToString()}")

            val docContent = client.callTool(
                "get-document-content",
                mapOf("documentId" to documentId)
            )
                ?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Содержимой документа:: ${docContent?.joinToString()}")
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
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                println("⚠️ Принудительное завершение процесса...")
                process.destroyForcibly()
            }
        }
    }
}

fun main() = runBlocking {
    val tester = GoogleDocsMcpTest()

    println("=== Google Docs MCP Testing ===") // 1cqadlQLNHHTd2NgLxFZVzVdY61tQybRsSjsGpNNCF88

    println("\n" + "=".repeat(50) + "\n")

    // Тест MCP сервера
    tester.testGoogleDocsMcpServer()
}
