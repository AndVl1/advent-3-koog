package ru.andvl.mcp.github

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

object GithubMcpServer {

    private val logger = LoggerFactory.getLogger(GithubMcpServer::class.java)

    fun runServer() {
        logger.info("🚀 Starting Kotlin GitHub MCP Server...")

        val dotenv = dotenv { ignoreIfMissing = true }
        val githubToken: String? = dotenv["GITHUB_TOKEN"]
        val githubClient = GitHubClient(githubToken)
        val json = Json { prettyPrint = false }

        logger.info("📦 GitHub token available: ${githubToken != null}")

        val server = Server(
            Implementation("github-mcp", "0.0.1"),
            ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    logging = null,
                )
            )
        )

        server.addTool(
            name = "get-repo-base-info",
            description = "Get base repository info: title, commits and branches count",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository") {
                        put("type", JsonPrimitive("string"))
                        put(
                            "description",
                            JsonPrimitive("GitHub repository in format 'owner/repo' (например: 'facebook/react')")
                        )
                    }
                },
                required = listOf("repository")
            )
        ) { request ->
            val (owner, repo) = request.arguments["repository"]?.jsonPrimitive?.content
                ?.split("/")
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Repository not given or in incorrect format (needed 'owner/repo')")),
                    isError = true,
                )

            val repoInfo = githubClient.getRepositoryInfo(owner, repo)
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("Unable to get repository info for $owner/$repo"),
                    ),
                    isError = true
                )

            val response = json.encodeToString(GitHubRepositoryInfo.serializer(), repoInfo)

            CallToolResult(
                content = listOf(
                    TextContent(response)
                ),
            )
        }

        server.addTool(
            name = "list-repository-contents",
            description = "List files and directories in a repository path",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("GitHub repository in format 'owner/repo'"))
                    }
                    putJsonObject("path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path within repository (empty for root)"))
                    }
                    putJsonObject("ref") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Branch or commit reference (optional)"))
                    }
                },
                required = listOf("repository")
            )
        ) { request ->
            val (owner, repo) = request.arguments["repository"]?.jsonPrimitive?.content
                ?.split("/")
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Repository not given or in incorrect format (needed 'owner/repo')")),
                    isError = true,
                )

            val path = request.arguments["path"]?.jsonPrimitive?.content ?: ""
            val ref = request.arguments["ref"]?.jsonPrimitive?.content

            val contents = githubClient.getRepositoryContents(owner, repo, path, ref)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Unable to get repository contents for $owner/$repo at path '$path'")),
                    isError = true
                )

            val response = json.encodeToString(contents)

            CallToolResult(
                content = listOf(TextContent(response)),
            )
        }

        server.addTool(
            name = "get-file-content",
            description = "Get the content of a specific file in the repository",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("GitHub repository in format 'owner/repo'"))
                    }
                    putJsonObject("path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to the file within repository"))
                    }
                    putJsonObject("ref") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Branch or commit reference (optional)"))
                    }
                },
                required = listOf("repository", "path")
            )
        ) { request ->
            val (owner, repo) = request.arguments["repository"]?.jsonPrimitive?.content
                ?.split("/")
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Repository not given or in incorrect format (needed 'owner/repo')")),
                    isError = true,
                )

            val path = request.arguments["path"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("File path is required")),
                    isError = true
                )

            val ref = request.arguments["ref"]?.jsonPrimitive?.content

            val fileContent = githubClient.getFileContent(owner, repo, path, ref)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Unable to get file content for $owner/$repo at path '$path'")),
                    isError = true
                )

            val response = json.encodeToString(GitHubFileContent.serializer(), fileContent)

            CallToolResult(
                content = listOf(TextContent(response)),
            )
        }

        server.addTool(
            name = "get-repository-tree",
            description = "Get the full file tree of the repository",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("GitHub repository in format 'owner/repo'"))
                    }
                    putJsonObject("recursive") {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("Get full recursive tree (default: false)"))
                    }
                    putJsonObject("treeSha") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Specific tree SHA (optional, uses default branch if not provided)"))
                    }
                },
                required = listOf("repository")
            )
        ) { request ->
            val (owner, repo) = request.arguments["repository"]?.jsonPrimitive?.content
                ?.split("/")
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Repository not given or in incorrect format (needed 'owner/repo')")),
                    isError = true,
                )

            val recursive = request.arguments["recursive"]?.jsonPrimitive?.booleanOrNull ?: false
            val treeSha = request.arguments["treeSha"]?.jsonPrimitive?.content

            val tree = githubClient.getRepositoryTree(owner, repo, treeSha, recursive)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Unable to get repository tree for $owner/$repo")),
                    isError = true
                )

            val response = json.encodeToString(tree)

            CallToolResult(
                content = listOf(TextContent(response)),
            )
        }

        server.addTool(
            name = "hello-world",
            description = "Say hello world",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Random or provided name"))
                    }
                },
                required = listOf("name")
            )
        ) { request ->
            CallToolResult(
                content = listOf(
                    TextContent("Hello World, ${request.arguments["name"]?.jsonPrimitive?.content}"),
                ),
            )
        }

        logger.info("📋 Registered GitHub MCP tools: get-repo-base-info, list-repository-contents, get-file-content, get-repository-tree, hello-world")
        logger.info("🔗 Starting GitHub MCP server on stdin/stdout...")

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
                    githubClient.close()
                }
                logger.info("✅ Server started successfully")
                done.join()
            } catch (e: Exception) {
                logger.error("❌ Server error: ${e.message}", e)
                githubClient.close()
            }
        }
    }
}
