package ru.andvl.mcp.googledocs

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

        // Add tools
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

        val registeredTools = listOf(
            "get-document-info",
            "get-document-content",
            "extract-document-id",
            "check-document-access",
            "list-accessible-documents"
        )

        logger.info("📋 Registered Google Docs MCP tools: ${registeredTools.joinToString(", ")}")

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
