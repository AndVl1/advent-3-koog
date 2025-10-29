package ru.andvl.mcp.telegraph

/**
 * Telegraph MCP Server
 *
 * This server provides MCP tools for interacting with the Telegraph API.
 *
 * ## Markdown Support
 *
 * All content parameters accept **Markdown strings** which are automatically converted
 * to Telegraph's internal format. When retrieving pages, the content is returned
 * as Markdown strings for easy consumption.
 *
 * ### Supported Markdown Features:
 * - **Headers**: `# H1`, `## H2`, `### H3`, `#### H4`
 * - **Text formatting**: `**bold**`, `*italic*`, `~~strikethrough~~`, `` `code` ``
 * - **Links**: `[Link text](https://example.com)`
 * - **Images**: `![Alt text](https://example.com/image.jpg)`
 * - **Lists**:
 *   - Unordered: `* Item 1`, `- Item 2`, `+ Item 3`
 *   - Ordered: `1. Item 1`, `2. Item 2`
 * - **Blockquotes**: `> This is a quote`
 * - **Code blocks**: ```\ncode here\n```
 * - **Horizontal rules**: `---` or `***`
 * - **Paragraphs**: Separate lines with empty lines
 *
 * ### Examples:
 *
 * **Simple content:**
 * ```markdown
 * # Welcome to Telegraph
 *
 * This is a **simple** paragraph with *italic* text.
 * ```
 *
 * **Article with formatting:**
 * ```markdown
 * # My Article
 *
 * ## Introduction
 *
 * This is an introduction paragraph with a [link to Telegraph](https://telegra.ph).
 *
 * ## Features
 *
 * - First feature
 * - Second feature with **bold text**
 * - Third feature
 *
 * > This is a quote from someone important.
 *
 * ```code
 * println("Hello, World!")
 * ```
 *
 * Check out the image below:
 *
 * ![Example Image](https://example.com/image.jpg)
 * ```
 *
 * **Mixed formatting:**
 * ```markdown
 * # Title
 *
 * This paragraph has **bold**, *italic*, and `code` formatting.
 *
 * You can also create [links](https://example.com) and images:
 * ![Alt text](https://example.com/image.png)
 *
 * > "Quote of the day"
 *
 * ---
 *
 * ## Lists
 *
 * 1. First item
 * 2. Second item
 *    - Nested item
 *    - Another nested item
 * 3. Third item
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

        val dotenv = dotenv {
          ignoreIfMissing = true
        }
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
                    putJsonObject("access_token") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Access token (optional if set in .env)"))
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
            val accessToken = request.arguments["access_token"]?.jsonPrimitive?.content

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
//                    putJsonObject("access_token") {
//                        put("type", JsonPrimitive("string"))
//                        put("description", JsonPrimitive("Access token (optional if set in .env)"))
//                    }
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
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Page content in Markdown format. Supports headers, bold, italic, links, lists, blockquotes, code blocks, etc. Example: '# Title\\n\\nThis is **bold** text with a [link](https://example.com).'"))
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

            val contentMarkdown = request.arguments["content"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Content is required")),
                    isError = true
                )

            val content = MarkdownConverter.markdownToNodes(contentMarkdown)

            val token = telegraphToken ?: return@addTool CallToolResult(
                content = listOf(TextContent("Access token is required in .env file")),
                isError = true
            )

            val page = telegraphClient.createPage(
                accessToken = token,
                title = title,
                authorName = authorName,
                authorUrl = authorUrl,
                content = content,
                returnContent = returnContent
            ) ?: return@addTool CallToolResult(
                content = listOf(
                    TextContent("Failed to create page. Check access token."),
                    TextContent("Token: $token")
                ),
                isError = true
            )

            val contentMarkdownOut = if (returnContent && page.content != null) {
                MarkdownConverter.nodesToMarkdown(page.content!!)
            } else null

            val responsePage = TelegraphPageResponse(
                path = page.path,
                url = page.url,
                title = page.title,
                description = page.description,
                authorName = page.authorName,
                authorUrl = page.authorUrl,
                imageUrl = page.imageUrl,
                contentMarkdown = contentMarkdownOut,
                views = page.views,
                canEdit = page.canEdit
            )

            val response = json.encodeToString(TelegraphPageResponse.serializer(), responsePage)

            CallToolResult(
                content = listOf(
                    TextContent(response),
                    TextContent("TOKEN: $token")
                )
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
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Page content in Markdown format. Supports headers, bold, italic, links, lists, blockquotes, code blocks, etc. Example: '# Title\\n\\nThis is **bold** text with a [link](https://example.com).'"))
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

            val contentMarkdown = request.arguments["content"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Content is required")),
                    isError = true
                )

            val content = MarkdownConverter.markdownToNodes(contentMarkdown)

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

            val contentMarkdownOut = if (returnContent && page.content != null) {
                MarkdownConverter.nodesToMarkdown(page.content!!)
            } else null

            val responsePage = TelegraphPageResponse(
                path = page.path,
                url = page.url,
                title = page.title,
                description = page.description,
                authorName = page.authorName,
                authorUrl = page.authorUrl,
                imageUrl = page.imageUrl,
                contentMarkdown = contentMarkdownOut,
                views = page.views,
                canEdit = page.canEdit
            )

            val response = json.encodeToString(TelegraphPageResponse.serializer(), responsePage)

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

            val contentMarkdownOut = if (returnContent && page.content != null) {
                MarkdownConverter.nodesToMarkdown(page.content)
            } else null

            val responsePage = TelegraphPageResponse(
                path = page.path,
                url = page.url,
                title = page.title,
                description = page.description,
                authorName = page.authorName,
                authorUrl = page.authorUrl,
                imageUrl = page.imageUrl,
                contentMarkdown = contentMarkdownOut,
                views = page.views,
                canEdit = page.canEdit
            )

            val response = json.encodeToString(TelegraphPageResponse.serializer(), responsePage)

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
