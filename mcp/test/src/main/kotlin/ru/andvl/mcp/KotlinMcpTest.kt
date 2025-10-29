package ru.andvl.mcp

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

/**
 * Тест Kotlin MCP сервера
 *
 * Тестирует Kotlin реализацию MCP сервера с инструментами:
 * - GitHub: get-repo-base-info, hello-world
 * - Telegraph: create-telegraph-account, get-telegraph-account-info
 *
 * Перед запуском: ./gradlew :mcp:github:build :mcp:telegraph:build
 */
fun main() = runBlocking {
    println("🧪 Тестирование MCP серверов (GitHub и Telegraph)...")

    // Тестируем GitHub MCP сервер
    testGitHubMcpServer()

    println("\n" + "=".repeat(50))

    // Тестируем Telegraph MCP сервер
    testTelegraphMcpServer()
}

/**
 * Тестирование GitHub MCP сервера
 */
suspend fun testGitHubMcpServer() {
    println("\n🧪 Тестирование GitHub MCP сервера...")

    // Правильный относительный путь к JAR файлу
    val jarPath = "mcp/github/build/libs/github-0.1.0.jar"

    // Проверяем существование JAR файла
    val jarFile = File(jarPath)
    if (!jarFile.exists()) {
        println("❌ GitHub JAR файл не найден: $jarPath")
        println("Абсолютный путь: ${jarFile.absolutePath}")
        println("Текущая директория: ${File(".").absolutePath}")
        println("💡 Запустите: ./gradlew :mcp:github:build")
        return
    }

    println("✅ GitHub JAR файл найден: $jarPath")

    println("🚀 Запуск GitHub MCP сервера...")
    val process = ProcessBuilder("java", "-jar", jarFile.absolutePath)
        .redirectErrorStream(false) // Разделяем stdout и stderr
        .start()

    // Даем серверу время на запуск
    println("⏳ Ожидание запуска сервера...")
    delay(2000)

    // Проверяем, что процесс еще работает
    if (!process.isAlive) {
        println("❌ GitHub процесс завершился с кодом: ${process.exitValue()}")
        val errorOutput = process.errorStream.bufferedReader().readText()
        val stdOutput = process.inputStream.bufferedReader().readText()
        println("Stderr: $errorOutput")
        println("Stdout: $stdOutput")
        return
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
        return
    }

    try {
        println("🔍 Получение списка инструментов...")
        val toolsList = client.listTools().tools.map { it.name }
        println("Available Tools = $toolsList")

        if (toolsList.contains("hello-world")) {
            println("🔧 Вызов инструмента hello-world...")
            val result = client.callTool("hello-world", mapOf("name" to "Andrey"))
                ?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат: ${result?.joinToString()}")
        } else {
            println("⚠️ Инструмент hello-world не найден")
        }

        if (toolsList.contains("get-repo-base-info")) {
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

    println("\n✅ Тестирование GitHub завершено")
}

/**
 * Тестирование Telegraph MCP сервера
 */
suspend fun testTelegraphMcpServer() {
    println("\n🧪 Тестирование Telegraph MCP сервера...")

    val jarPath = "mcp/telegraph/build/libs/telegraph-0.1.0.jar"
    val jarFile = File(jarPath)

    if (!jarFile.exists()) {
        println("❌ Telegraph JAR файл не найден: $jarPath")
        println("💡 Запустите: ./gradlew :mcp:telegraph:build")
        return
    }

    println("✅ Telegraph JAR файл найден: $jarPath")

    println("🚀 Запуск Telegraph MCP сервера...")
    val process = ProcessBuilder("java", "-jar", jarFile.absolutePath)
        .redirectErrorStream(false)
        .start()

    delay(2000)

    if (!process.isAlive) {
        println("❌ Telegraph процесс завершился с кодом: ${process.exitValue()}")
        return
    }

    val transport = StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered()
    )

    val client = Client(
        clientInfo = Implementation(name = "kotlin-telegraph-test-client", version = "1.0.0"),
    )

    try {
        println("🔌 Подключение к Telegraph серверу...")
        client.connect(transport)
        delay(1000)
        println("✅ Подключено успешно!")

        println("🔍 Получение списка инструментов...")
        val toolsList = client.listTools().tools.map { it.name }
        println("Available Tools = $toolsList")

        // Тест create-telegraph-account
        if (toolsList.contains("create-telegraph-account")) {
            println("🔧 Вызов инструмента create-telegraph-account...")
            val result = client.callTool("create-telegraph-account", mapOf(
                "short_name" to "TestAccount",
                "author_name" to "Test Author"
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат создания аккаунта: ${result?.joinToString()}")
        }

        // Тест get-telegraph-account-info
        if (toolsList.contains("get-telegraph-account-info")) {
            println("🔧 Вызов инструмента get-telegraph-account-info...")
            val result = client.callTool("get-telegraph-account-info", mapOf(
                "fields" to listOf("short_name", "author_name", "page_count")
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат информации об аккаунте: ${result?.joinToString()}")
        }

        // Тест create-telegraph-page
        var createdPagePath: String? = null
        if (toolsList.contains("create-telegraph-page")) {
            println("\n🔧 Вызов инструмента create-telegraph-page...")
            val content = listOf(
                mapOf(
                    "tag" to "h3",
                    "children" to listOf("Hello Telegraph!")
                ),
                mapOf(
                    "tag" to "p",
                    "children" to listOf("This is a test page created via MCP.")
                ),
                mapOf(
                    "tag" to "p",
                    "children" to listOf("Bold text", " and ", "italic text."),
                    "attrs" to mapOf("style" to "color: blue;")
                )
            )

            val result = client.callTool("create-telegraph-page", mapOf(
                "title" to "Test Page via MCP",
                "author_name" to "MCP Test",
                "content" to content,
                "return_content" to true
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат создания страницы: ${result?.joinToString()}")

            // Извлекаем path из результата для последующего использования
            result?.firstOrNull()?.let { jsonStr ->
                // Простое извлечение path из JSON строки
                val pathRegex = """"path":"([^"]+)"""".toRegex()
                val match = pathRegex.find(jsonStr)
                createdPagePath = match?.groupValues?.get(1)
                if (createdPagePath != null) {
                    println("✅ Сохранен путь созданной страницы: $createdPagePath")
                }
            }
        }

        // Тест get-telegraph-page
        if (createdPagePath != null && toolsList.contains("get-telegraph-page")) {
            println("\n🔧 Вызов инструмента get-telegraph-page...")
            val result = client.callTool("get-telegraph-page", mapOf(
                "path" to createdPagePath,
                "return_content" to true
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат получения страницы: ${result?.joinToString()}")
        } else if (createdPagePath == null) {
            println("\n⚠️ Не удалось получить путь созданной страницы, пропускаем тест get-telegraph-page")
        }

        // Тест edit-telegraph-page
        if (createdPagePath != null && toolsList.contains("edit-telegraph-page")) {
            println("\n🔧 Вызов инструмента edit-telegraph-page...")
            val editedContent = listOf(
                mapOf(
                    "tag" to "h3",
                    "children" to listOf("Hello Telegraph! (Edited)")
                ),
                mapOf(
                    "tag" to "p",
                    "children" to listOf("This page has been edited via MCP.")
                ),
                mapOf(
                    "tag" to "ul",
                    "children" to listOf(
                        "First item",
                        "Second item",
                        "Third item with bold and italic text"
                    )
                ),
                mapOf(
                    "tag" to "ul",
                    "children" to listOf(
                        "Second item",
                    )
                ),
                mapOf(
                    "tag" to "p",
                    "children" to listOf("Edited at 2025-10-29"),
                    "attrs" to mapOf("style" to "color: green;")
                )
            )

            val result = client.callTool("edit-telegraph-page", mapOf(
                "path" to createdPagePath,
                "title" to "Test Page via MCP (Edited)",
                "author_name" to "MCP Test Editor",
                "content" to editedContent,
                "return_content" to true
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат редактирования страницы: ${result?.joinToString()}")
        } else if (createdPagePath == null) {
            println("\n⚠️ Не удалось получить путь созданной страницы, пропускаем тест edit-telegraph-page")
        }

        // Тест get-telegraph-page после редактирования
        if (createdPagePath != null && toolsList.contains("get-telegraph-page")) {
            println("\n🔧 Вызов инструмента get-telegraph-page (после редактирования)...")
            val result = client.callTool("get-telegraph-page", mapOf(
                "path" to createdPagePath,
                "return_content" to true
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат получения отредактированной страницы: ${result?.joinToString()}")
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

        println("🛑 Остановка Telegraph сервера...")
        process.destroy()
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            println("⚠️ Принудительное завершение процесса...")
            process.destroyForcibly()
        }
    }

    println("\n✅ Тестирование Telegraph завершено")
}
