package ru.andvl.mcp.github

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

/**
 * HTTP клиент для работы с GitHub API
 */
class GitHubClient(private val token: String? = null) {

    private val logger = LoggerFactory.getLogger(GitHubClient::class.java)

    init {
        // Token is optional for public API access
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.NONE
        }
    }

    /**
     * Получить информацию о репозитории
     */
    suspend fun getRepositoryInfo(owner: String, repo: String): GitHubRepositoryInfo? {
        return try {
            // Получить основную информацию о репозитории
            val repoResponse = httpClient.get("https://api.github.com/repos/$owner/$repo") {
                token?.let {
                    header("Authorization", "Bearer $it")
                }
            }

            if (repoResponse.status != HttpStatusCode.OK) {
                logger.warn("Error getting repository info: ${repoResponse.status}")
                return null
            }

            val repoData = repoResponse.body<GitHubRepoResponse>()

            // Получить количество веток
            val branchCount = getBranchCount(owner, repo)

            // Получить количество коммитов (приблизительно через последние коммиты)
            val commitCount = getCommitCount(owner, repo)

            GitHubRepositoryInfo(
                name = repoData.name,
                description = repoData.description,
                branchCount = branchCount,
                commitCount = commitCount,
                defaultBranch = repoData.default_branch ?: "main",
                isPrivate = repoData.private ?: false,
                language = repoData.language,
                url = repoData.html_url ?: repoData.url ?: "",
                createdAt = repoData.created_at,
                updatedAt = repoData.updated_at
            )
        } catch (e: Exception) {
            logger.error("Error fetching repository info: ${e.message}", e)
            null
        }
    }

    /**
     * Получить количество веток
     */
    private suspend fun getBranchCount(owner: String, repo: String): Int {
        return try {
            val response = httpClient.get("https://api.github.com/repos/$owner/$repo/branches") {
                token?.let {
                    header("Authorization", "Bearer $it")
                }
                // Получить только первую страницу для подсчета
                parameter("per_page", "100")
                parameter("page", "1")
            }

            if (response.status == HttpStatusCode.OK) {
                val branches = response.body<List<GitHubBranchResponse>>()
                branches.size
            } else {
                0
            }
        } catch (e: Exception) {
            logger.warn("Error getting branch count: ${e.message}")
            0
        }
    }

    /**
     * Получить приблизительное количество коммитов
     */
    private suspend fun getCommitCount(owner: String, repo: String): Int {
        return try {
            val response = httpClient.get("https://api.github.com/repos/$owner/$repo/commits") {
                token?.let {
                    header("Authorization", "Bearer $it")
                }
                parameter("per_page", "1") // Получаем только один коммит для оптимизации
            }

            if (response.status == HttpStatusCode.OK) {
                // Попытаться получить информацию о количестве из заголовков Link
                val linkHeader = response.headers["Link"]
                if (linkHeader != null) {
                    // Парсинг заголовка Link для получения общего количества страниц
                    val lastPageRegex = """page=(\d+)>; rel="last"""".toRegex()
                    val match = lastPageRegex.find(linkHeader)
                    if (match != null) {
                        val lastPage = match.groupValues[1].toInt()
                        // Приблизительное количество (последняя страница * 30 коммитов на страницу)
                        return lastPage * 30
                    }
                }

                // Если нет пагинации, возвращаем 1 (есть хотя бы один коммит)
                1
            } else {
                0
            }
        } catch (e: Exception) {
            logger.warn("Error getting commit count: ${e.message}")
            0
        }
    }

    /**
     * Получить содержимое корневой директории репозитория
     */
    suspend fun getRepositoryContents(owner: String, repo: String, path: String = "", ref: String? = null): List<GitHubContentItem>? {
        return try {
            val response = httpClient.get("https://api.github.com/repos/$owner/$repo/contents/$path") {
                token?.let {
                    header("Authorization", "Bearer $it")
                }
                ref?.let {
                    parameter("ref", it)
                }
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("Error getting repository contents: ${response.status}")
                return null
            }

            val contentData = response.body<List<GitHubContentResponse>>()
            contentData.map { item ->
                GitHubContentItem(
                    name = item.name,
                    path = item.path,
                    type = item.type,
                    size = item.size,
                    sha = item.sha,
                    url = item.url,
                    downloadUrl = item.download_url
                )
            }
        } catch (e: Exception) {
            logger.error("Error fetching repository contents: ${e.message}", e)
            null
        }
    }

    /**
     * Получить содержимое файла
     */
    suspend fun getFileContent(owner: String, repo: String, path: String, ref: String? = null): GitHubFileContent? {
        return try {
            val response = httpClient.get("https://api.github.com/repos/$owner/$repo/contents/$path") {
                token?.let {
                    header("Authorization", "Bearer $it")
                }
                ref?.let {
                    parameter("ref", it)
                }
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("Error getting file content: ${response.status}")
                return null
            }

            val fileData = response.body<GitHubFileResponse>()

            // Декодировать base64 контент
            val decodedContent = if (fileData.encoding == "base64") {
                try {
                    String(Base64.getDecoder().decode(fileData.content.replace("\n", "")))
                } catch (e: Exception) {
                    logger.warn("Failed to decode base64 content: ${e.message}")
                    fileData.content
                }
            } else {
                fileData.content
            }

            GitHubFileContent(
                name = fileData.name,
                path = fileData.path,
                content = decodedContent,
                encoding = fileData.encoding,
                size = fileData.size,
                sha = fileData.sha
            )
        } catch (e: Exception) {
            logger.error("Error fetching file content: ${e.message}", e)
            null
        }
    }

    /**
     * Получить дерево файлов репозитория
     */
    suspend fun getRepositoryTree(owner: String, repo: String, treeSha: String? = null, recursive: Boolean = false): List<GitHubTreeItem>? {
        return try {
            val sha = treeSha ?: run {
                // Получить SHA главной ветки
                val repoInfo = getRepositoryInfo(owner, repo)
                repoInfo?.defaultBranch?.let { branch ->
                    getBranchSha(owner, repo, branch)
                }
            } ?: return null

            val response = httpClient.get("https://api.github.com/repos/$owner/$repo/git/trees/$sha") {
                token?.let {
                    header("Authorization", "Bearer $it")
                }
                if (recursive) {
                    parameter("recursive", "1")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("Error getting repository tree: ${response.status}")
                return null
            }

            val treeData = response.body<GitHubTreeResponse>()
            treeData.tree.map { item ->
                GitHubTreeItem(
                    path = item.path,
                    mode = item.mode,
                    type = item.type,
                    sha = item.sha,
                    size = item.size
                )
            }
        } catch (e: Exception) {
            logger.error("Error fetching repository tree: ${e.message}", e)
            null
        }
    }

    /**
     * Получить список коммитов
     */
    suspend fun getCommits(
        owner: String, 
        repo: String, 
        since: String? = null, 
        until: String? = null, 
        sha: String? = null,
        path: String? = null,
        perPage: Int = 30,
        page: Int = 1
    ): List<GitHubCommit>? {
        return try {
            val response = httpClient.get("https://api.github.com/repos/$owner/$repo/commits") {
                token?.let {
                    header("Authorization", "Bearer $it")
                }
                parameter("per_page", perPage)
                parameter("page", page)
                since?.let { parameter("since", it) }
                until?.let { parameter("until", it) }
                sha?.let { parameter("sha", it) }
                path?.let { parameter("path", it) }
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("Error getting commits: ${response.status}")
                return null
            }

            val commitsData = response.body<List<GitHubCommitResponse>>()
            commitsData.map { commit ->
                GitHubCommit(
                    sha = commit.sha,
                    message = commit.commit.message,
                    author = GitHubCommitAuthor(
                        name = commit.commit.author.name,
                        email = commit.commit.author.email,
                        date = commit.commit.author.date
                    ),
                    committer = GitHubCommitAuthor(
                        name = commit.commit.committer.name,
                        email = commit.commit.committer.email,
                        date = commit.commit.committer.date
                    ),
                    date = commit.commit.author.date,
                    url = commit.html_url
                )
            }
        } catch (e: Exception) {
            logger.error("Error fetching commits: ${e.message}", e)
            null
        }
    }

    /**
     * Получить детали конкретного коммита
     */
    suspend fun getCommitDetails(owner: String, repo: String, sha: String): GitHubCommitDetails? {
        return try {
            val response = httpClient.get("https://api.github.com/repos/$owner/$repo/commits/$sha") {
                token?.let {
                    header("Authorization", "Bearer $it")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("Error getting commit details: ${response.status}")
                return null
            }

            val commitData = response.body<GitHubCommitDetailsResponse>()
            GitHubCommitDetails(
                sha = commitData.sha,
                message = commitData.commit.message,
                author = GitHubCommitAuthor(
                    name = commitData.commit.author.name,
                    email = commitData.commit.author.email,
                    date = commitData.commit.author.date
                ),
                committer = GitHubCommitAuthor(
                    name = commitData.commit.committer.name,
                    email = commitData.commit.committer.email,
                    date = commitData.commit.committer.date
                ),
                date = commitData.commit.author.date,
                url = commitData.html_url,
                stats = GitHubCommitStats(
                    additions = commitData.stats.additions,
                    deletions = commitData.stats.deletions,
                    total = commitData.stats.total
                ),
                files = commitData.files.map { file ->
                    GitHubCommitFile(
                        filename = file.filename,
                        status = file.status,
                        additions = file.additions,
                        deletions = file.deletions,
                        changes = file.changes,
                        patch = file.patch,
                        previousFilename = file.previous_filename
                    )
                }
            )
        } catch (e: Exception) {
            logger.error("Error fetching commit details: ${e.message}", e)
            null
        }
    }

    /**
     * Получить изменения в репозитории за определенный период
     */
    suspend fun getRepositoryChanges(
        owner: String, 
        repo: String, 
        since: String, 
        until: String? = null
    ): List<GitHubCommit>? {
        return getCommits(owner, repo, since, until, perPage = 100)
    }

    /**
     * Получить сводку активности в репозитории за период
     */
    suspend fun getRepositorySummary(
        owner: String,
        repo: String,
        since: String,
        until: String? = null
    ): GitHubRepositorySummary? {
        return try {
            // Получить все коммиты за период
            val commits = mutableListOf<GitHubCommit>()
            var page = 1
            val perPage = 100
            
            while (true) {
                val pageCommits = getCommits(owner, repo, since, until, perPage = perPage, page = page)
                    ?: break
                
                if (pageCommits.isEmpty()) break
                commits.addAll(pageCommits)
                
                if (pageCommits.size < perPage) break
                page++
            }

            if (commits.isEmpty()) return null

            // Получить детали коммитов с файлами (ограничиваем первыми 50 для производительности)
            val commitDetails = mutableListOf<GitHubCommitDetails>()
            val maxDetailsToFetch = minOf(50, commits.size)
            
            for (i in 0 until maxDetailsToFetch) {
                val details = getCommitDetails(owner, repo, commits[i].sha)
                if (details != null) {
                    commitDetails.add(details)
                }
            }

            // Анализ по авторам
            val authorStatsMap = mutableMapOf<String, MutableList<GitHubCommitDetails>>()
            commitDetails.forEach { commit ->
                val authorKey = "${commit.author.name} (${commit.author.email})"
                authorStatsMap.getOrPut(authorKey) { mutableListOf() }.add(commit)
            }

            val authorStats = authorStatsMap.map { (authorKey, commits) ->
                val author = commits.first().author
                GitHubAuthorStats(
                    author = author.name,
                    email = author.email,
                    commitCount = commits.size,
                    totalAdditions = commits.sumOf { it.stats.additions },
                    totalDeletions = commits.sumOf { it.stats.deletions },
                    firstCommit = commits.minByOrNull { it.date }?.date ?: "",
                    lastCommit = commits.maxByOrNull { it.date }?.date ?: ""
                )
            }.sortedByDescending { it.commitCount }

            // Топ изменённых файлов
            val fileChanges = mutableMapOf<String, Int>()
            commitDetails.forEach { commit ->
                commit.files.forEach { file ->
                    fileChanges[file.filename] = fileChanges.getOrDefault(file.filename, 0) + file.changes
                }
            }
            val topChangedFiles = fileChanges.toList()
                .sortedByDescending { it.second }
                .take(10)
                .map { it.first }

            val period = if (until != null) "$since to $until" else "since $since"

            GitHubRepositorySummary(
                repository = "$owner/$repo",
                period = period,
                totalCommits = commits.size,
                totalAuthors = authorStats.size,
                totalAdditions = commitDetails.sumOf { it.stats.additions },
                totalDeletions = commitDetails.sumOf { it.stats.deletions },
                authorStats = authorStats,
                topChangedFiles = topChangedFiles
            )
        } catch (e: Exception) {
            logger.error("Error creating repository summary: ${e.message}", e)
            null
        }
    }

    /**
     * Получить SHA коммита ветки
     */
    private suspend fun getBranchSha(owner: String, repo: String, branch: String): String? {
        return try {
            val response = httpClient.get("https://api.github.com/repos/$owner/$repo/branches/$branch") {
                token?.let {
                    header("Authorization", "Bearer $it")
                }
            }

            if (response.status == HttpStatusCode.OK) {
                val branchData = response.body<GitHubBranchResponse>()
                branchData.commit.sha
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("Error getting branch SHA: ${e.message}")
            null
        }
    }

    fun close() {
        httpClient.close()
    }
}

/**
 * GitHub API response models
 */
@Serializable
private data class GitHubRepoResponse(
    val name: String,
    val description: String? = null,
    val default_branch: String? = null,
    val private: Boolean? = null,
    val language: String? = null,
    val html_url: String? = null,
    val url: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
private data class GitHubBranchResponse(
    val name: String,
    val commit: GitHubCommitRefResponse
)

@Serializable
private data class GitHubCommitRefResponse(
    val sha: String
)

@Serializable
private data class GitHubContentResponse(
    val name: String,
    val path: String,
    val type: String,
    val size: Long? = null,
    val sha: String,
    val url: String,
    val download_url: String? = null
)

@Serializable
private data class GitHubFileResponse(
    val name: String,
    val path: String,
    val content: String,
    val encoding: String,
    val size: Long,
    val sha: String
)

@Serializable
private data class GitHubTreeResponse(
    val sha: String,
    val tree: List<GitHubTreeItemResponse>
)

@Serializable
private data class GitHubTreeItemResponse(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String,
    val size: Long? = null
)

@Serializable
private data class GitHubCommitResponse(
    val sha: String,
    val commit: GitHubCommitDataResponse,
    val html_url: String? = null
)

@Serializable
private data class GitHubCommitDataResponse(
    val message: String,
    val author: GitHubCommitAuthorResponse,
    val committer: GitHubCommitAuthorResponse
)

@Serializable
private data class GitHubCommitAuthorResponse(
    val name: String,
    val email: String,
    val date: String
)

@Serializable
private data class GitHubCommitDetailsResponse(
    val sha: String,
    val commit: GitHubCommitDataResponse,
    val html_url: String,
    val stats: GitHubCommitStatsResponse,
    val files: List<GitHubCommitFileResponse>
)

@Serializable
private data class GitHubCommitStatsResponse(
    val additions: Int,
    val deletions: Int,
    val total: Int
)

@Serializable
private data class GitHubCommitFileResponse(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String? = null,
    val previous_filename: String? = null
)
