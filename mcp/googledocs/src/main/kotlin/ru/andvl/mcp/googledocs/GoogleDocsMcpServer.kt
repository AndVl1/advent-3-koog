package ru.andvl.mcp.googledocs

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.auth.http.HttpCredentialsAdapter
import io.github.cdimascio.dotenv.dotenv
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

object GoogleDocsMcpServer {

    private val logger = LoggerFactory.getLogger(GoogleDocsMcpServer::class.java)

    private fun createSheetsService(docsClient: GoogleDocsClient): Sheets {
        return Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(docsClient.credentials)
        ).setApplicationName("Google Sheets MCP Server").build()
    }

    suspend fun runServer() {
        logger.info("🚀 Starting Kotlin Google Docs MCP Server...")

        val dotenv = dotenv { ignoreIfMissing = true }
        val serviceAccountPath = dotenv["GOOGLE_SERVICE_ACCOUNT_JSON_PATH"]
        val serviceAccountJson = dotenv["GOOGLE_SERVICE_ACCOUNT_JSON"]

        val googleDocsClient = GoogleDocsClient(
            serviceAccountPath = serviceAccountPath,
            serviceAccountJson = serviceAccountJson
        )
        val json = Json { prettyPrint = false }

        logger.info("📦 Google Docs service account configured: ${serviceAccountPath != null || serviceAccountJson != null}")

        val server = Server(
            Implementation("googledocs-mcp", "1.0.0"),
            ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    logging = null,
                )
            )
        )

        // Add Google Docs Tools
        server.addTool(
            name = "get-document-info",
            description = "Получить информацию о Google Docs документе (заголовок, ID ревизии, стиль)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("documentId") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ID Google Docs документа"))
                    }
                },
                required = listOf("documentId")
            )
        ) { request ->
            val documentId = request.arguments["documentId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр documentId")),
                    isError = true,
                )

            val docInfo = googleDocsClient.getDocumentInfo(documentId)
            docInfo.getOrNull()
                ?.let {
                    val response = json.encodeToString(GoogleDocInfo.serializer(), it)
                    CallToolResult(content = listOf(TextContent(response)))
                }
                ?: docInfo.exceptionOrNull()?.let {
                    CallToolResult(
                        content = listOf(TextContent("❌ Не удалось получить информацию о документе $documentId. Произошла ошибка ${it.stackTraceToString()}")),
                        isError = true
                    )
                } ?: CallToolResult(
                    content = listOf(TextContent("❌ Не удалось получить информацию о документе $documentId")),
                    isError = true
                )
        }

        server.addTool(
            name = "get-document-content",
            description = "Получить текстовое содержимое Google Docs документа",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("documentId") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ID Google Docs документа"))
                    }
                },
                required = listOf("documentId")
            )
        ) { request ->
            val documentId = request.arguments["documentId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр documentId")),
                    isError = true,
                )

            val docContent = googleDocsClient.getDocumentContent(documentId)
            if (docContent != null) {
                val response = json.encodeToString(GoogleDocContent.serializer(), docContent)
                CallToolResult(
                    content = listOf(TextContent(response))
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("❌ Не удалось получить содержимое документа $documentId")),
                    isError = true
                )
            }
        }

        server.addTool(
            name = "extract-document-id",
            description = "Извлечь ID документа из URL Google Docs",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("url") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("URL Google Docs документа"))
                    }
                },
                required = listOf("url")
            )
        ) { request ->
            val url = request.arguments["url"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр url")),
                    isError = true,
                )

            val documentId = googleDocsClient.extractDocumentId(url)
            if (documentId != null) {
                CallToolResult(
                    content = listOf(TextContent("""{"documentId": "$documentId"}"""))
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("❌ Не удалось извлечь ID документа из URL: $url")),
                    isError = true
                )
            }
        }

        server.addTool(
            name = "check-document-access",
            description = "Проверить доступность Google Docs документа",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("documentId") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ID Google Docs документа"))
                    }
                },
                required = listOf("documentId")
            )
        ) { request ->
            val documentId = request.arguments["documentId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр documentId")),
                    isError = true,
                )

            val isAccessible = googleDocsClient.isDocumentAccessible(documentId)
            CallToolResult(
                content = listOf(TextContent("""{"documentId": "$documentId", "accessible": $isAccessible}"""))
            )
        }

        server.addTool(
            name = "list-accessible-documents",
            description = "Получить список доступных Google Docs документов",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("maxResults") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Максимальное количество документов для возврата"))
                        put("default", JsonPrimitive(10))
                    }
                },
                required = emptyList()
            )
        ) { request ->
            val maxResults = request.arguments["maxResults"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10

            val documents = googleDocsClient.listAccessibleDocuments(maxResults)
            val response = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(GoogleDocInfo.serializer()),
                documents
            )
            CallToolResult(
                content = listOf(TextContent(response))
            )
        }

        // Google Sheets Tools
        server.addTool(
            name = "get-spreadsheet-info",
            description = "Получить информацию о Google таблице (листы, размеры, свойства)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("spreadsheetId") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ID Google таблицы"))
                    }
                },
                required = listOf("spreadsheetId")
            )
        ) { request ->
            val spreadsheetId = request.arguments["spreadsheetId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр spreadsheetId")),
                    isError = true,
                )

            val sheetsClient = GoogleSheetsClient(createSheetsService(googleDocsClient))
            val result = sheetsClient.getSpreadsheetInfo(spreadsheetId)

            result.getOrNull()?.let {
                val response = json.encodeToString(GoogleSpreadsheetInfo.serializer(), it)
                CallToolResult(content = listOf(TextContent(response)))
            } ?: result.exceptionOrNull()?.let {
                CallToolResult(
                    content = listOf(TextContent("❌ Не удалось получить информацию о таблице $spreadsheetId: ${it.message}")),
                    isError = true
                )
            } ?: CallToolResult(
                content = listOf(TextContent("❌ Не удалось получить информацию о таблице $spreadsheetId")),
                isError = true
            )
        }

        server.addTool(
            name = "get-sheet-content",
            description = "Получить содержимое листа или диапазона ячеек из Google таблицы",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("spreadsheetId") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ID Google таблицы"))
                    }
                    putJsonObject("sheetName") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Название листа (опционально)"))
                    }
                    putJsonObject("range") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Диапазон ячеек (например, A1:C10) (опционально)"))
                    }
                },
                required = listOf("spreadsheetId")
            )
        ) { request ->
            val spreadsheetId = request.arguments["spreadsheetId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр spreadsheetId")),
                    isError = true,
                )

            val sheetName = request.arguments["sheetName"]?.jsonPrimitive?.content
            val range = request.arguments["range"]?.jsonPrimitive?.content

            val sheetsClient = GoogleSheetsClient(createSheetsService(googleDocsClient))
            val result = sheetsClient.getSheetContent(spreadsheetId, sheetName, range)

            result.getOrNull()?.let {
                val response = json.encodeToString(SheetContent.serializer(), it)
                CallToolResult(content = listOf(TextContent(response)))
            } ?: result.exceptionOrNull()?.let {
                CallToolResult(
                    content = listOf(TextContent("❌ Не удалось получить содержимое таблицы: ${it.message}")),
                    isError = true
                )
            } ?: CallToolResult(
                content = listOf(TextContent("❌ Не удалось получить содержимое таблицы")),
                isError = true
            )
        }

        server.addTool(
            name = "update-sheet-cells",
            description = "Обновить ячейки в Google таблице",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("spreadsheetId") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ID Google таблицы"))
                    }
                    putJsonObject("range") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Диапазон ячеек для обновления (например, A1:C10)"))
                    }
                    put("values", buildJsonArray {
                        addJsonObject {
                            put("type", JsonPrimitive("array"))
                            put("description", JsonPrimitive("Двумерный массив значений для записи"))
                        }
                    })
                },
                required = listOf("spreadsheetId", "range", "values")
            )
        ) { request ->
            val spreadsheetId = request.arguments["spreadsheetId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр spreadsheetId")),
                    isError = true,
                )

            val range = request.arguments["range"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр range")),
                    isError = true,
                )

            val valuesArray = request.arguments["values"]?.jsonArray
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр values")),
                    isError = true,
                )

            val values = valuesArray.map { row ->
                row.jsonArray.map { it.jsonPrimitive.content }
            }

            val sheetsClient = GoogleSheetsClient(createSheetsService(googleDocsClient))
            val result = sheetsClient.updateSheetContent(spreadsheetId, range, values)

            result.getOrNull()?.let {
                CallToolResult(content = listOf(TextContent(it)))
            } ?: result.exceptionOrNull()?.let {
                CallToolResult(
                    content = listOf(TextContent("❌ Не удалось обновить таблицу: ${it.message}")),
                    isError = true
                )
            } ?: CallToolResult(
                content = listOf(TextContent("❌ Не удалось обновить таблицу")),
                isError = true
            )
        }

        server.addTool(
            name = "append-to-sheet",
            description = "Добавить строки в конец таблицы",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("spreadsheetId") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ID Google таблицы"))
                    }
                    putJsonObject("range") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Диапазон для добавления (например, A1:C1)"))
                    }
                    put("values", buildJsonArray {
                        addJsonObject {
                            put("type", JsonPrimitive("array"))
                            put("description", JsonPrimitive("Двумерный массив значений для добавления"))
                        }
                    })
                },
                required = listOf("spreadsheetId", "range", "values")
            )
        ) { request ->
            val spreadsheetId = request.arguments["spreadsheetId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр spreadsheetId")),
                    isError = true,
                )

            val range = request.arguments["range"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр range")),
                    isError = true,
                )

            val valuesArray = request.arguments["values"]?.jsonArray
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр values")),
                    isError = true,
                )

            val values = valuesArray.map { row ->
                row.jsonArray.map { it.jsonPrimitive.content }
            }

            val sheetsClient = GoogleSheetsClient(createSheetsService(googleDocsClient))
            val result = sheetsClient.appendToSheet(spreadsheetId, range, values)

            result.getOrNull()?.let {
                CallToolResult(content = listOf(TextContent(it)))
            } ?: result.exceptionOrNull()?.let {
                CallToolResult(
                    content = listOf(TextContent("❌ Не удалось добавить данные: ${it.message}")),
                    isError = true
                )
            } ?: CallToolResult(
                content = listOf(TextContent("❌ Не удалось добавить данные")),
                isError = true
            )
        }

        server.addTool(
            name = "create-sheet",
            description = "Создать новый лист в таблице",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("spreadsheetId") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ID Google таблицы"))
                    }
                    putJsonObject("title") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Название нового листа"))
                    }
                    putJsonObject("rowCount") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Количество строк (по умолчанию 1000)"))
                        put("default", JsonPrimitive(1000))
                    }
                    putJsonObject("columnCount") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Количество колонок (по умолчанию 26)"))
                        put("default", JsonPrimitive(26))
                    }
                },
                required = listOf("spreadsheetId", "title")
            )
        ) { request ->
            val spreadsheetId = request.arguments["spreadsheetId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр spreadsheetId")),
                    isError = true,
                )

            val title = request.arguments["title"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр title")),
                    isError = true,
                )

            val rowCount = request.arguments["rowCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1000
            val columnCount = request.arguments["columnCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 26

            val sheetsClient = GoogleSheetsClient(createSheetsService(googleDocsClient))
            val result = sheetsClient.createSheet(spreadsheetId, title, rowCount, columnCount)

            result.getOrNull()?.let {
                val response = json.encodeToString(SheetInfo.serializer(), it)
                CallToolResult(content = listOf(TextContent(response)))
            } ?: result.exceptionOrNull()?.let {
                CallToolResult(
                    content = listOf(TextContent("❌ Не удалось создать лист: ${it.message}")),
                    isError = true
                )
            } ?: CallToolResult(
                content = listOf(TextContent("❌ Не удалось создать лист")),
                isError = true
            )
        }

        server.addTool(
            name = "clear-sheet-range",
            description = "Очистить диапазон ячеек в таблице",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("spreadsheetId") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ID Google таблицы"))
                    }
                    putJsonObject("range") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Диапазон для очистки (например, A1:C10)"))
                    }
                },
                required = listOf("spreadsheetId", "range")
            )
        ) { request ->
            val spreadsheetId = request.arguments["spreadsheetId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр spreadsheetId")),
                    isError = true,
                )

            val range = request.arguments["range"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Отсутствует параметр range")),
                    isError = true,
                )

            val sheetsClient = GoogleSheetsClient(createSheetsService(googleDocsClient))
            val result = sheetsClient.clearRange(spreadsheetId, range)

            result.getOrNull()?.let {
                CallToolResult(content = listOf(TextContent(it)))
            } ?: result.exceptionOrNull()?.let {
                CallToolResult(
                    content = listOf(TextContent("❌ Не удалось очистить диапазон: ${it.message}")),
                    isError = true
                )
            } ?: CallToolResult(
                content = listOf(TextContent("❌ Не удалось очистить диапазон")),
                isError = true
            )
        }

        val registeredTools = listOf(
            "get-document-info",
            "get-document-content",
            "extract-document-id",
            "check-document-access",
            "list-accessible-documents",
            "get-spreadsheet-info",
            "get-sheet-content",
            "update-sheet-cells",
            "append-to-sheet",
            "create-sheet",
            "clear-sheet-range"
        )

        logger.info("📋 Registered Google Docs & Sheets MCP tools: ${registeredTools.joinToString(", ")}")

        logger.info("🔗 Starting Google Docs MCP server on stdin/stdout...")

        val transport = StdioServerTransport(
            System.`in`.asInput(),
            System.out.asSink().buffered()
        )

        try {
            server.connect(transport)
            val done = Job()

            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info("🔚 Server closing...")
                done.complete()
            })

            logger.info("✅ Server started successfully")

            done.join()
        } catch (e: Exception) {
            logger.error("❌ Error running server: ${e.message}", e)
        }
    }
}