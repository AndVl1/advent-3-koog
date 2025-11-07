package ru.andvl.mcp

import io.github.cdimascio.dotenv.dotenv
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import ru.andvl.mcp.googledocs.GoogleDocsMcpTest
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Тест Kotlin MCP сервера
 *
 * Тестирует Kotlin реализацию MCP сервера с инструментами:
 * - GitHub: get-repo-base-info, hello-world
 * - Telegraph: create-telegraph-account, get-telegraph-account-info
 * - Google Docs: get-document-info, get-document-content
 * - Google Sheets: get-spreadsheet-info, get-sheet-content, update-sheet-content, append-to-sheet, create-sheet, delete-sheet
 *
 * Перед запуском: ./gradlew :mcp:github:build :mcp:telegraph:build :mcp:googledocs:shadowJar
 */
fun main() = runBlocking {
    println("🧪 Тестирование MCP серверов (GitHub, Telegraph, Google Docs, and Google Sheets)...")

    // Загружаем переменные окружения из .env
    val dotenv = dotenv { ignoreIfMissing = true }

    // Добавляем TELEGRAPH_ACCESS_TOKEN в переменные окружения
    val env = System.getenv().toMutableMap()
    dotenv["TELEGRAPH_ACCESS_TOKEN"]?.let { token ->
        env["TELEGRAPH_ACCESS_TOKEN"] = token
    }

    // Тестируем GitHub MCP сервер
    testGitHubMcpServer()

    println("\n" + "=".repeat(50))

    // Тестируем Telegraph MCP сервер
    testTelegraphMcpServer(env)

    println("\n" + "=".repeat(50))

    GoogleDocsMcpTest().testGoogleDocsMcpServer()

    println("\n" + "=".repeat(50))

    // Тестируем Google Sheets MCP сервер
    testGoogleSheetsMcpServer()
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
suspend fun testTelegraphMcpServer(env: Map<String, String>) {
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

//    env.forEach { (k, v) -> pb.environment()[k] = v }

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
            val contentMarkdown = """# Hello Telegraph!
                
This is a test page created via MCP.

This paragraph has **bold** and *italic* text.

Here's a link to [Telegraph](https://telegra.ph).

## Features List

* First feature
* Second feature with **bold text**
* Third feature

> This is a quote from someone important.

You can also write `code` inline.

```
println("Hello, World!");
```

---

### Conclusion

This demonstrates Markdown support in the Telegraph MCP server."""

            val result = client.callTool("create-telegraph-page", mapOf(
                "title" to "Test Page via MCP",
                "author_name" to "MCP Test",
                "content" to contentMarkdown,
                "return_content" to true
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат создания страницы: ${result?.joinToString()}")

            // Извлекаем path и contentMarkdown из результата
            result?.firstOrNull()?.let { jsonStr ->
                // Простое извлечение path из JSON строки
                val pathRegex = """"path":"([^"]+)"""".toRegex()
                val pathMatch = pathRegex.find(jsonStr)
                createdPagePath = pathMatch?.groupValues?.get(1)
                if (createdPagePath != null) {
                    println("✅ Сохранен путь созданной страницы: $createdPagePath")
                }

                // Показываем Markdown если вернулся
                val contentMdRegex = """"contentMarkdown":"([^"]+)"""".toRegex()
                val contentMatch = contentMdRegex.find(jsonStr)
                if (contentMatch != null) {
                    println("\n📝 Контент в формате Markdown:")
                    println(contentMatch.groupValues[1].replace("\\n", "\n"))
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
            val editedContentMarkdown = """# Hello Telegraph! (Edited)
                
This page has been **edited** via MCP.

## Updated Features

1. First updated feature
2. Second updated feature
3. Third updated feature

### New Section

This is a completely new section that was added during editing.

> "This quote was added during editing"

The original content has been **modified** and *enhanced*.

```
// Updated code block
console.log("Edited content!");
```

**Edit timestamp:** 2025-10-29"""

            val result = client.callTool("edit-telegraph-page", mapOf(
                "path" to createdPagePath,
                "title" to "Test Page via MCP (Edited)",
                "author_name" to "MCP Test Editor",
                "content" to editedContentMarkdown,
                "return_content" to true
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат редактирования страницы: ${result?.joinToString()}")

            // Показываем отредактированный Markdown если вернулся
            result?.firstOrNull()?.let { jsonStr ->
                val contentMdRegex = """"contentMarkdown":"([^"]+)"""".toRegex()
                val contentMatch = contentMdRegex.find(jsonStr)
                if (contentMatch != null) {
                    println("\n📝 Отредактированный контент в формате Markdown:")
                    println(contentMatch.groupValues[1].replace("\\n", "\n"))
                }
            }
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

/**
 * Тестирование Google Sheets MCP сервера
 */
suspend fun testGoogleSheetsMcpServer() {
    println("\n🧪 Тестирование Google Sheets MCP сервера...")

    val jarPath = "mcp/googledocs/build/libs/googledocs-0.1.0.jar"
    val jarFile = File(jarPath)

    if (!jarFile.exists()) {
        println("❌ Google Sheets JAR файл не найден: $jarPath")
        println("💡 Запустите: ./gradlew :mcp:googledocs:shadowJar")
        return
    }

    println("✅ Google Sheets JAR файл найден: $jarPath")

    // Extract spreadsheet ID from URL
    val spreadsheetUrl = "https://docs.google.com/spreadsheets/d/1WX5XfE-GoaspvwICjBujW7_XNAh1aIDTUQ2ESmTAVNY/edit?gid=1747343534#gid=1747343534"
    val spreadsheetId = "1WX5XfE-GoaspvwICjBujW7_XNAh1aIDTUQ2ESmTAVNY"
    val testSheetName = "RK1lev1" // Default sheet name

    println("🚀 Запуск Google Sheets MCP сервера...")
    val process = ProcessBuilder("java", "-jar", jarFile.absolutePath)
        .redirectErrorStream(false)
        .start()

    delay(2000)

    if (!process.isAlive) {
        println("❌ Google Sheets процесс завершился с кодом: ${process.exitValue()}")
        return
    }

    val transport = StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered()
    )

    val client = Client(
        clientInfo = Implementation(name = "kotlin-googlesheets-test-client", version = "1.0.0"),
    )

    try {
        println("🔌 Подключение к Google Sheets серверу...")
        client.connect(transport)
        delay(1000)
        println("✅ Подключено успешно!")

        println("🔍 Получение списка инструментов...")
        val toolsList = client.listTools()?.tools?.map { it.name }
        println("Available Tools = $toolsList")

        // Test 1: Get spreadsheet info
        if (toolsList?.contains("get-spreadsheet-info") == true) {
            println("\n🔧 Тест 1: Получение информации о таблице...")
            val result = client.callTool("get-spreadsheet-info", mapOf(
                "spreadsheetId" to spreadsheetId
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Информация о таблице: ${result?.joinToString()}")
        }

        // Test 2: Get sheet content
        if (toolsList?.contains("get-sheet-content") == true) {
            println("\n🔧 Тест 2: Получение содержимого листа...")
            val result = client.callTool("get-sheet-content", mapOf(
                "spreadsheetId" to spreadsheetId,
                "sheetName" to testSheetName,
                "range" to "A8:Z8"
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Содержимое листа: ${result?.joinToString()}")
        }

        // Test 3: Update sheet content
        if (toolsList?.contains("update-sheet-content") == true) {
            println("\n🔧 Тест 3: Обновление содержимого листа...")
            val result = client.callTool("update-sheet-content", mapOf(
                "spreadsheetId" to spreadsheetId,
                "range" to "${testSheetName}!A10:C10",
                "values" to listOf(
                    listOf("Test Data", "Updated via MCP", java.time.LocalDateTime.now().toString())
                )
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат обновления: ${result?.joinToString()}")
        }

        // Test 4: Append to sheet
        if (toolsList?.contains("append-to-sheet") == true) {
            println("\n🔧 Тест 4: Добавление данных в лист...")
            val result = client.callTool("append-to-sheet", mapOf(
                "spreadsheetId" to spreadsheetId,
                "range" to "${testSheetName}!A:C",
                "values" to listOf(
                    listOf("Appended Data", "Via MCP", java.time.LocalDateTime.now().toString())
                )
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат добавления: ${result?.joinToString()}")
        }

        // Test 5: Create new sheet
        if (toolsList?.contains("create-sheet") == true) {
            println("\n🔧 Тест 5: Создание нового листа...")
            val testSheetTitle = "MCP Test Sheet ${System.currentTimeMillis()}"
            val result = client.callTool("create-sheet", mapOf(
                "spreadsheetId" to spreadsheetId,
                "title" to testSheetTitle,
                "rowCount" to 100,
                "columnCount" to 10
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Результат создания листа: ${result?.joinToString()}")

            // Test 6: Delete the created sheet (extract sheet ID from result)
            result?.firstOrNull()?.let { jsonStr ->
                // Simple extraction of sheetId from JSON response
                val sheetIdRegex = "\"sheetId\":\"([^\"]+)\"".toRegex()
                val sheetIdMatch = sheetIdRegex.find(jsonStr)
                val createdSheetId = sheetIdMatch?.groupValues?.get(1)

                if (createdSheetId != null && toolsList.contains("delete-sheet")) {
                    println("\n🔧 Тест 6: Удаление созданного листа...")
                    val deleteResult = client.callTool("delete-sheet", mapOf(
                        "spreadsheetId" to spreadsheetId,
                        "sheetId" to createdSheetId
                    ))?.content?.map { if (it is TextContent) it.text else it.toString() }

                    println("📋 Результат удаления: ${deleteResult?.joinToString()}")
                }
            }
        }

        // Verify the updates by reading the sheet again
        if (toolsList?.contains("get-sheet-content") == true) {
            println("\n🔧 Финальная проверка: Чтение обновленного содержимого...")
            val result = client.callTool("get-sheet-content", mapOf(
                "spreadsheetId" to spreadsheetId,
                "range" to "${testSheetName}!A8:C15"
            ))?.content?.map { if (it is TextContent) it.text else it.toString() }

            println("📋 Обновленное содержимое: ${result?.joinToString()}")
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

        println("🛑 Остановка Google Sheets сервера...")
        process.destroy()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            println("⚠️ Принудительное завершение процесса...")
            process.destroyForcibly()
        }
    }

    println("\n✅ Тестирование Google Sheets завершено")
}
