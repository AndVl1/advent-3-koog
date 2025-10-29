package ru.andvl.mcp.telegraph

/**
 * Telegraph MCP Server
 * 
 * This server provides MCP tools for interacting with the Telegraph API.
 * 
 * ## Content Node Structure
 * 
 * For `create-telegraph-page` and `edit-telegraph-page` tools, the content parameter
 * should be an array of nodes. Each node is a JSON object with the following structure:
 * 
 * ```json
 * {
 *   "tag": "string",           // Required: HTML tag name (e.g., "p", "h3", "ul", "li")
 *   "children": ["string"],   // Optional: Array of text content
 *   "attrs": {                 // Optional: HTML attributes
 *     "style": "string",
 *     "href": "string",
 *     "src": "string"
 *   }
 * }
 * ```
 * 
 * ### Supported Tags:
 * - **Text formatting**: `p`, `h1`, `h2`, `h3`, `h4`, `strong`, `em`, `u`, `s`, `code`, `pre`
 * - **Lists**: `ul`, `ol`, `li`
 * - **Links**: `a` (with `href` in attrs)
 * - **Images**: `img` (with `src` in attrs)
 * - **Other**: `blockquote`, `hr`, `br`
 * 
 * ### Examples:
 * 
 * **Simple paragraph:**
 * ```json
 * [
 *   {
 *     "tag": "p",
 *     "children": ["Hello, world!"]
 *   }
 * ]
 * ```
 * 
 * **Heading with style:**
 * ```json
 * [
 *   {
 *     "tag": "h3",
 *     "children": ["Welcome to Telegraph"],
 *     "attrs": {
 *       "style": "color: blue;"
 *     }
 *   }
 * ]
 * ```
 * 
 * **List:**
 * ```json
 * [
 *   {
 *     "tag": "ul",
 *     "children": [
 *       "First item",
 *       "Second item",
 *       "Third item"
 *     ]
 *   }
 * ]
 * ```
 * 
 * **Link:**
 * ```json
 * [
 *   {
 *     "tag": "p",
 *     "children": ["Visit "],
 *   },
 *   {
 *     "tag": "a",
 *     "children": ["Telegraph"],
 *     "attrs": {
 *       "href": "https://telegra.ph"
 *     }
 *   },
 *   {
 *     "tag": "p",
 *     "children": [" for more info."]
 *   }
 * ]
 * ```
 * 
 * **Mixed formatting:**
 * ```json
 * [
 *   {
 *     "tag": "h3",
 *     "children": ["Article Title"]
 *   },
 *   {
 *     "tag": "p",
 *     "children": ["This is "],
 *   },
 *   {
 *     "tag": "strong",
 *     "children": ["bold"]
 *   },
 *   {
 *     "tag": "p",
 *     "children": [" and "],
 *   },
 *   {
 *     "tag": "em",
 *     "children": ["italic"]
 *   },
 *   {
 *     "tag": "p",
 *     "children": [" text."]
 *   },
 *   {
 *     "tag": "blockquote",
 *     "children": ["This is a quote."]
 *   }
 * ]
 * ```
 */

import io.github.cdimascio.dotenv.dotenv
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

object TelegraphMcpServer {

    private val logger = LoggerFactory.getLogger(TelegraphMcpServer::class.java)

    fun runServer() {
        logger.info("🚀 Starting Kotlin Telegraph MCP Server...")

        val dotenv = dotenv { ignoreIfMissing = true }
        val telegraphToken: String? = dotenv["TELEGRAPH_ACCESS_TOKEN"]
        val telegraphClient = TelegraphClient(telegraphToken)
        val json = Json { prettyPrint = false }

        logger.info("📦 Telegraph token available: ${telegraphToken != null}")

        val server = Server(
            Implementation("telegraph-mcp", "0.0.1"),
            ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    logging = null,
                )
            )
        )

        // Create Account tool
        server.addTool(
            name = "create-telegraph-account",
            description = "Create a new Telegraph account",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("short_name") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Account name (1-32 characters)"))
                    }
                    putJsonObject("author_name") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Default author name (0-128 characters, optional)"))
                    }
                    putJsonObject("author_url") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Default profile link (0-512 characters, optional)"))
                    }
                },
                required = listOf("short_name")
            )
        ) { request ->
            val shortName = request.arguments["short_name"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Short name is required")),
                    isError = true
                )
            
            val authorName = request.arguments["author_name"]?.jsonPrimitive?.content
            val authorUrl = request.arguments["author_url"]?.jsonPrimitive?.content

            val account = telegraphClient.createAccount(shortName, authorName, authorUrl)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Failed to create Telegraph account")),
                    isError = true
                )

            val response = json.encodeToString(TelegraphAccount.serializer(), account)
            
            CallToolResult(
                content = listOf(TextContent(response))
            )
        }

        // Get Account Info tool
        server.addTool(
            name = "get-telegraph-account-info",
            description = "Get information about a Telegraph account",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("access_token") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Access token (optional if set in .env)"))
                    }
                    putJsonObject("fields") {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("Fields to return (optional)"))
                    }
                },
                required = listOf()
            )
        ) { request ->
            val accessToken = request.arguments["access_token"]?.jsonPrimitive?.content
            val fieldsJson = request.arguments["fields"]?.jsonArray
            val fields = fieldsJson?.map { it.jsonPrimitive.content } 
                ?: listOf("short_name", "author_name", "author_url", "page_count")

            val token = accessToken ?: telegraphToken
            if (token == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Access token is required")),
                    isError = true
                )
            }
            
            val account = telegraphClient.getAccountInfo(
                accessToken = token,
                fields = fields
            ) ?: return@addTool CallToolResult(
                content = listOf(TextContent("Failed to get account info. Check access token.")),
                isError = true
            )

            val response = json.encodeToString(TelegraphAccount.serializer(), account)
            
            CallToolResult(
                content = listOf(TextContent(response))
            )
        }

        // Create Page tool
        server.addTool(
            name = "create-telegraph-page",
            description = "Create a new Telegraph page",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("title") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Page title (1-256 characters)"))
                    }
                    putJsonObject("author_name") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Author name (0-128 characters, optional)"))
                    }
                    putJsonObject("author_url") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Author URL (0-512 characters, optional)"))
                    }
                    putJsonObject("content") {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("Page content as array of nodes. Each node has 'tag' (required), 'children' (optional array of strings), and 'attrs' (optional object). Example: [{\"tag\":\"p\",\"children\":[\"Hello world\"]}]"))
                    }
                    putJsonObject("return_content") {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("Return content in response (default: false)"))
                    }
                },
                required = listOf("title", "content")
            )
        ) { request ->
            val title = request.arguments["title"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Title is required")),
                    isError = true
                )
            
            val authorName = request.arguments["author_name"]?.jsonPrimitive?.content
            val authorUrl = request.arguments["author_url"]?.jsonPrimitive?.content
            val returnContent = request.arguments["return_content"]?.jsonPrimitive?.booleanOrNull ?: false

            val contentJson = request.arguments["content"]?.jsonArray
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Content is required")),
                    isError = true
                )

            val content = contentJson.mapNotNull { node ->
                val nodeObj = node.jsonObject
                val tag = nodeObj["tag"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val children = nodeObj["children"]?.jsonArray?.map { it.jsonPrimitive.content }
                val attrs = nodeObj["attrs"]?.jsonObject?.mapValues { 
                    it.value.jsonPrimitive.content 
                }
                TelegraphNode(tag, children, attrs)
            }

            val token = telegraphToken
            if (token == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Access token is required in .env file")),
                    isError = true
                )
            }
            
            val page = telegraphClient.createPage(
                accessToken = token,
                title = title,
                authorName = authorName,
                authorUrl = authorUrl,
                content = content,
                returnContent = returnContent
            ) ?: return@addTool CallToolResult(
                content = listOf(TextContent("Failed to create page. Check access token.")),
                isError = true
            )

            val response = json.encodeToString(TelegraphPage.serializer(), page)
            
            CallToolResult(
                content = listOf(TextContent(response))
            )
        }

        // Edit Page tool
        server.addTool(
            name = "edit-telegraph-page",
            description = "Edit an existing Telegraph page",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Page path (e.g., 'Sample-Page-12-15')"))
                    }
                    putJsonObject("title") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Page title (1-256 characters)"))
                    }
                    putJsonObject("author_name") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Author name (0-128 characters, optional)"))
                    }
                    putJsonObject("author_url") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Author URL (0-512 characters, optional)"))
                    }
                    putJsonObject("content") {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("Page content as array of nodes. Each node has 'tag' (required), 'children' (optional array of strings), and 'attrs' (optional object). Example: [{\"tag\":\"p\",\"children\":[\"Hello world\"]}]"))
                    }
                    putJsonObject("return_content") {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("Return content in response (default: false)"))
                    }
                },
                required = listOf("path", "title", "content")
            )
        ) { request ->
            val path = request.arguments["path"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Path is required")),
                    isError = true
                )
            
            val title = request.arguments["title"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Title is required")),
                    isError = true
                )
            
            val authorName = request.arguments["author_name"]?.jsonPrimitive?.content
            val authorUrl = request.arguments["author_url"]?.jsonPrimitive?.content
            val returnContent = request.arguments["return_content"]?.jsonPrimitive?.booleanOrNull ?: false

            val contentJson = request.arguments["content"]?.jsonArray
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Content is required")),
                    isError = true
                )

            val content = contentJson.mapNotNull { node ->
                val nodeObj = node.jsonObject
                val tag = nodeObj["tag"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val children = nodeObj["children"]?.jsonArray?.map { it.jsonPrimitive.content }
                val attrs = nodeObj["attrs"]?.jsonObject?.mapValues { 
                    it.value.jsonPrimitive.content 
                }
                TelegraphNode(tag, children, attrs)
            }

            val token = telegraphToken
            if (token == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Access token is required in .env file")),
                    isError = true
                )
            }
            
            val page = telegraphClient.editPage(
                accessToken = token,
                path = path,
                title = title,
                authorName = authorName,
                authorUrl = authorUrl,
                content = content,
                returnContent = returnContent
            ) ?: return@addTool CallToolResult(
                content = listOf(TextContent("Failed to edit page. Check access token and path.")),
                isError = true
            )

            val response = json.encodeToString(TelegraphPage.serializer(), page)
            
            CallToolResult(
                content = listOf(TextContent(response))
            )
        }

        // Get Page tool
        server.addTool(
            name = "get-telegraph-page",
            description = "Get a Telegraph page by path",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Page path (e.g., 'Sample-Page-12-15')"))
                    }
                    putJsonObject("return_content") {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("Return content in response (default: false)"))
                    }
                },
                required = listOf("path")
            )
        ) { request ->
            val path = request.arguments["path"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Path is required")),
                    isError = true
                )
            
            val returnContent = request.arguments["return_content"]?.jsonPrimitive?.booleanOrNull ?: false

            val page = telegraphClient.getPage(path, returnContent)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Failed to get page. Check path.")),
                    isError = true
                )

            val response = json.encodeToString(TelegraphPage.serializer(), page)
            
            CallToolResult(
                content = listOf(TextContent(response))
            )
        }

        // Get Page List tool
        server.addTool(
            name = "get-telegraph-page-list",
            description = "Get list of pages belonging to a Telegraph account",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("access_token") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Access token (optional if set in .env)"))
                    }
                    putJsonObject("offset") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Sequential number of first page (default: 0)"))
                    }
                    putJsonObject("limit") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Number of pages to retrieve (0-200, default: 50)"))
                    }
                },
                required = listOf()
            )
        ) { request ->
            val accessToken = request.arguments["access_token"]?.jsonPrimitive?.content
            val offset = request.arguments["offset"]?.jsonPrimitive?.intOrNull ?: 0
            val limit = request.arguments["limit"]?.jsonPrimitive?.intOrNull ?: 50

            val token = accessToken ?: telegraphToken
            if (token == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Access token is required")),
                    isError = true
                )
            }
            
            val pageList = telegraphClient.getPageList(
                accessToken = token,
                offset = offset,
                limit = limit
            ) ?: return@addTool CallToolResult(
                content = listOf(TextContent("Failed to get page list. Check access token.")),
                isError = true
            )

            val response = json.encodeToString(TelegraphPageList.serializer(), pageList)
            
            CallToolResult(
                content = listOf(TextContent(response))
            )
        }

        // Get Views tool
        server.addTool(
            name = "get-telegraph-views",
            description = "Get the number of views for a Telegraph page",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Page path (e.g., 'Sample-Page-12-15')"))
                    }
                    putJsonObject("year") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Year (2000-2100, optional)"))
                    }
                    putJsonObject("month") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Month (1-12, optional)"))
                    }
                    putJsonObject("day") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Day (1-31, optional)"))
                    }
                    putJsonObject("hour") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Hour (0-24, optional)"))
                    }
                },
                required = listOf("path")
            )
        ) { request ->
            val path = request.arguments["path"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Path is required")),
                    isError = true
                )
            
            val year = request.arguments["year"]?.jsonPrimitive?.intOrNull
            val month = request.arguments["month"]?.jsonPrimitive?.intOrNull
            val day = request.arguments["day"]?.jsonPrimitive?.intOrNull
            val hour = request.arguments["hour"]?.jsonPrimitive?.intOrNull

            val views = telegraphClient.getViews(path, year, month, day, hour)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Failed to get page views. Check path.")),
                    isError = true
                )

            val response = json.encodeToString(TelegraphPageViews.serializer(), views)
            
            CallToolResult(
                content = listOf(TextContent(response))
            )
        }

        logger.info("📋 Registered Telegraph MCP tools: create-telegraph-account, get-telegraph-account-info, create-telegraph-page, edit-telegraph-page, get-telegraph-page, get-telegraph-page-list, get-telegraph-views")
        logger.info("🔗 Starting Telegraph MCP server on stdin/stdout...")

        val transport = StdioServerTransport(
            System.`in`.asInput(),
            System.out.asSink().buffered()
        )

        runBlocking {
            try {
                server.connect(transport)
                val done = Job()
                server.onClose {
                    logger.info("🔚 Server closing...")
                    done.complete()
                    telegraphClient.close()
                }
                logger.info("✅ Server started successfully")
                done.join()
            } catch (e: Exception) {
                logger.error("❌ Server error: ${e.message}", e)
                telegraphClient.close()
            }
        }
    }
}