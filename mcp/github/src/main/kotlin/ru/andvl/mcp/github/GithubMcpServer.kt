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
        val gitOperations = GitOperations()
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
            name = "list-commits",
            description = "Get list of commits from repository with optional filtering",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("GitHub repository in format 'owner/repo'"))
                    }
                    putJsonObject("since") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ISO 8601 timestamp to filter commits after this date (optional)"))
                    }
                    putJsonObject("until") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ISO 8601 timestamp to filter commits before this date (optional)"))
                    }
                    putJsonObject("sha") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Branch or commit SHA to list commits from (optional)"))
                    }
                    putJsonObject("path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to filter commits that changed this file/directory (optional)"))
                    }
                    putJsonObject("per_page") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Number of results per page (1-100, default: 30)"))
                    }
                    putJsonObject("page") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Page number (default: 1)"))
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

            val since = request.arguments["since"]?.jsonPrimitive?.content
            val until = request.arguments["until"]?.jsonPrimitive?.content
            val sha = request.arguments["sha"]?.jsonPrimitive?.content
            val path = request.arguments["path"]?.jsonPrimitive?.content
            val perPage = request.arguments["per_page"]?.jsonPrimitive?.intOrNull ?: 30
            val page = request.arguments["page"]?.jsonPrimitive?.intOrNull ?: 1

            val commits = githubClient.getCommits(owner, repo, since, until, sha, path, perPage, page)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Unable to get commits for $owner/$repo")),
                    isError = true
                )

            val response = json.encodeToString(commits)

            CallToolResult(
                content = listOf(TextContent(response)),
            )
        }

        server.addTool(
            name = "get-commit-details",
            description = "Get detailed information about a specific commit including file changes",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("GitHub repository in format 'owner/repo'"))
                    }
                    putJsonObject("sha") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Commit SHA to get details for"))
                    }
                },
                required = listOf("repository", "sha")
            )
        ) { request ->
            val (owner, repo) = request.arguments["repository"]?.jsonPrimitive?.content
                ?.split("/")
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Repository not given or in incorrect format (needed 'owner/repo')")),
                    isError = true,
                )

            val sha = request.arguments["sha"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Commit SHA is required")),
                    isError = true
                )

            val commitDetails = githubClient.getCommitDetails(owner, repo, sha)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Unable to get commit details for $owner/$repo at SHA '$sha'")),
                    isError = true
                )

            val response = json.encodeToString(GitHubCommitDetails.serializer(), commitDetails)

            CallToolResult(
                content = listOf(TextContent(response)),
            )
        }

        server.addTool(
            name = "get-repository-changes",
            description = "Get all changes in repository for a specific time period (useful for daily/weekly reports)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("GitHub repository in format 'owner/repo'"))
                    }
                    putJsonObject("since") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ISO 8601 timestamp to get changes after this date"))
                    }
                    putJsonObject("until") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ISO 8601 timestamp to get changes before this date (optional, defaults to now)"))
                    }
                },
                required = listOf("repository", "since")
            )
        ) { request ->
            val (owner, repo) = request.arguments["repository"]?.jsonPrimitive?.content
                ?.split("/")
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Repository not given or in incorrect format (needed 'owner/repo')")),
                    isError = true,
                )

            val since = request.arguments["since"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Since parameter is required (ISO 8601 timestamp)")),
                    isError = true
                )

            val until = request.arguments["until"]?.jsonPrimitive?.content

            val changes = githubClient.getRepositoryChanges(owner, repo, since, until)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Unable to get repository changes for $owner/$repo since $since")),
                    isError = true
                )

            val response = json.encodeToString(changes)

            CallToolResult(
                content = listOf(TextContent(response)),
            )
        }

        server.addTool(
            name = "get-repository-summary",
            description = "Get comprehensive summary of repository activity including author statistics and top changed files for a time period",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("GitHub repository in format 'owner/repo'"))
                    }
                    putJsonObject("since") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ISO 8601 timestamp to get activity summary after this date"))
                    }
                    putJsonObject("until") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("ISO 8601 timestamp to get activity summary before this date (optional, defaults to now)"))
                    }
                },
                required = listOf("repository", "since")
            )
        ) { request ->
            val (owner, repo) = request.arguments["repository"]?.jsonPrimitive?.content
                ?.split("/")
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Repository not given or in incorrect format (needed 'owner/repo')")),
                    isError = true,
                )

            val since = request.arguments["since"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Since parameter is required (ISO 8601 timestamp)")),
                    isError = true
                )

            val until = request.arguments["until"]?.jsonPrimitive?.content

            val summary = githubClient.getRepositorySummary(owner, repo, since, until)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Unable to get repository summary for $owner/$repo since $since")),
                    isError = true
                )

            val response = json.encodeToString(GitHubRepositorySummary.serializer(), summary)

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

        // Git operations tools
        server.addTool(
            name = "github-create-branch",
            description = "Create a new branch in a local repository",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository_path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to the local repository"))
                    }
                    putJsonObject("branch_name") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Name of the new branch"))
                    }
                    putJsonObject("base_branch") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Base branch to create from (optional)"))
                    }
                },
                required = listOf("repository_path", "branch_name")
            )
        ) { request ->
            val repoPath = request.arguments["repository_path"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("repository_path is required")),
                    isError = true
                )

            val branchName = request.arguments["branch_name"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("branch_name is required")),
                    isError = true
                )

            val baseBranch = request.arguments["base_branch"]?.jsonPrimitive?.content

            val result = gitOperations.createBranch(repoPath, branchName, baseBranch)

            if (result.isSuccess) {
                CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to true,
                        "branch_name" to branchName,
                        "message" to result.getOrNull()
                    )))),
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to false,
                        "message" to result.exceptionOrNull()?.message
                    )))),
                    isError = true
                )
            }
        }

        server.addTool(
            name = "github-commit",
            description = "Create a commit with changes in a local repository",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository_path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to the local repository"))
                    }
                    putJsonObject("message") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Commit message"))
                    }
                    putJsonObject("files") {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("List of files to commit (optional, defaults to all changes)"))
                    }
                },
                required = listOf("repository_path", "message")
            )
        ) { request ->
            val repoPath = request.arguments["repository_path"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("repository_path is required")),
                    isError = true
                )

            val message = request.arguments["message"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("message is required")),
                    isError = true
                )

            val files = request.arguments["files"]?.jsonArray?.map { it.jsonPrimitive.content }

            val result = gitOperations.commit(repoPath, message, files)

            if (result.isSuccess) {
                CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to true,
                        "commit_sha" to result.getOrNull(),
                        "message" to "Commit created successfully"
                    )))),
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to false,
                        "message" to result.exceptionOrNull()?.message
                    )))),
                    isError = true
                )
            }
        }

        server.addTool(
            name = "github-push",
            description = "Push changes to remote repository",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository_path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to the local repository"))
                    }
                    putJsonObject("branch_name") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Branch name to push"))
                    }
                    putJsonObject("force") {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("Force push (optional, default: false)"))
                    }
                },
                required = listOf("repository_path", "branch_name")
            )
        ) { request ->
            val repoPath = request.arguments["repository_path"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("repository_path is required")),
                    isError = true
                )

            val branchName = request.arguments["branch_name"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("branch_name is required")),
                    isError = true
                )

            val force = request.arguments["force"]?.jsonPrimitive?.booleanOrNull ?: false

            val result = gitOperations.push(repoPath, branchName, force)

            if (result.isSuccess) {
                CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to true,
                        "pushed" to true,
                        "rejected" to false,
                        "message" to result.getOrNull()
                    )))),
                )
            } else {
                val rejected = result.exceptionOrNull()?.message?.contains("rejected") == true
                CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to false,
                        "pushed" to false,
                        "rejected" to rejected,
                        "message" to result.exceptionOrNull()?.message
                    )))),
                    isError = !rejected  // Don't treat rejection as error, just as info
                )
            }
        }

        server.addTool(
            name = "github-get-diff",
            description = "Get diff between two branches or commits in a local repository",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository_path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to the local repository"))
                    }
                    putJsonObject("base") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Base branch or commit"))
                    }
                    putJsonObject("head") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Head branch or commit"))
                    }
                },
                required = listOf("repository_path", "base", "head")
            )
        ) { request ->
            val repoPath = request.arguments["repository_path"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("repository_path is required")),
                    isError = true
                )

            val base = request.arguments["base"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("base is required")),
                    isError = true
                )

            val head = request.arguments["head"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("head is required")),
                    isError = true
                )

            val result = gitOperations.getDiff(repoPath, base, head)

            if (result.isSuccess) {
                val diffData = result.getOrNull()!!
                CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to true,
                        "diff" to diffData["diff"],
                        "files_changed" to diffData["files_changed"],
                        "insertions" to diffData["insertions"],
                        "deletions" to diffData["deletions"],
                        "message" to "Diff retrieved successfully"
                    )))),
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to false,
                        "message" to result.exceptionOrNull()?.message
                    )))),
                    isError = true
                )
            }
        }

        server.addTool(
            name = "github-get-default-branch",
            description = "Get the default branch of a GitHub repository",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("owner") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Repository owner"))
                    }
                    putJsonObject("repo") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Repository name"))
                    }
                },
                required = listOf("owner", "repo")
            )
        ) { request ->
            val owner = request.arguments["owner"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("owner is required")),
                    isError = true
                )

            val repo = request.arguments["repo"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("repo is required")),
                    isError = true
                )

            val defaultBranch = githubClient.getDefaultBranch(owner, repo)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to false,
                        "message" to "Unable to get default branch for $owner/$repo"
                    )))),
                    isError = true
                )

            CallToolResult(
                content = listOf(TextContent(json.encodeToString(mapOf(
                    "success" to true,
                    "default_branch" to defaultBranch,
                    "message" to "Default branch retrieved successfully"
                )))),
            )
        }

        server.addTool(
            name = "github-create-pull-request",
            description = "Create a pull request on GitHub",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("owner") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Repository owner"))
                    }
                    putJsonObject("repo") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Repository name"))
                    }
                    putJsonObject("title") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Pull request title"))
                    }
                    putJsonObject("body") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Pull request body/description"))
                    }
                    putJsonObject("head") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Branch with changes"))
                    }
                    putJsonObject("base") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Target branch"))
                    }
                },
                required = listOf("owner", "repo", "title", "body", "head", "base")
            )
        ) { request ->
            val owner = request.arguments["owner"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("owner is required")),
                    isError = true
                )

            val repo = request.arguments["repo"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("repo is required")),
                    isError = true
                )

            val title = request.arguments["title"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("title is required")),
                    isError = true
                )

            val body = request.arguments["body"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("body is required")),
                    isError = true
                )

            val head = request.arguments["head"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("head is required")),
                    isError = true
                )

            val base = request.arguments["base"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("base is required")),
                    isError = true
                )

            val pr = githubClient.createPullRequest(owner, repo, title, body, head, base)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to false,
                        "message" to "Unable to create pull request for $owner/$repo"
                    )))),
                    isError = true
                )

            CallToolResult(
                content = listOf(TextContent(json.encodeToString(mapOf(
                    "success" to true,
                    "pr_number" to pr.number,
                    "pr_url" to pr.html_url,
                    "message" to "Pull request created successfully"
                )))),
            )
        }

        server.addTool(
            name = "github-checkout-branch",
            description = "Switch to an existing branch in a local repository",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("repository_path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to the local repository"))
                    }
                    putJsonObject("branch_name") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Name of the branch to switch to"))
                    }
                },
                required = listOf("repository_path", "branch_name")
            )
        ) { request ->
            val repoPath = request.arguments["repository_path"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("repository_path is required")),
                    isError = true
                )

            val branchName = request.arguments["branch_name"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("branch_name is required")),
                    isError = true
                )

            val result = gitOperations.checkoutBranch(repoPath, branchName)

            if (result.isSuccess) {
                CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to true,
                        "branch_name" to branchName,
                        "message" to result.getOrNull()
                    )))),
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent(json.encodeToString(mapOf(
                        "success" to false,
                        "message" to result.exceptionOrNull()?.message
                    )))),
                    isError = true
                )
            }
        }

        logger.info("📋 Registered GitHub MCP tools: get-repo-base-info, list-repository-contents, get-file-content, get-repository-tree, list-commits, get-commit-details, get-repository-changes, get-repository-summary, hello-world, github-create-branch, github-commit, github-push, github-get-diff, github-get-default-branch, github-create-pull-request, github-checkout-branch")
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
