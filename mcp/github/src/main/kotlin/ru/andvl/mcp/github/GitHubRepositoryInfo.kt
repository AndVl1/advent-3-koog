package ru.andvl.mcp.github

import kotlinx.serialization.Serializable

/**
 * Информация о GitHub репозитории
 */
@Serializable
data class GitHubRepositoryInfo(
    val name: String,
    val description: String? = null,
    val branchCount: Int,
    val commitCount: Int,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val language: String? = null,
    val url: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * Информация о ветке репозитория
 */
@Serializable
data class GitHubBranch(
    val name: String,
    val sha: String,
    val isDefault: Boolean = false
)

/**
 * Информация о коммите
 */
@Serializable
data class GitHubCommit(
    val sha: String,
    val message: String,
    val author: GitHubCommitAuthor,
    val committer: GitHubCommitAuthor,
    val date: String,
    val url: String? = null,
    val stats: GitHubCommitStats? = null
)

/**
 * Автор коммита
 */
@Serializable
data class GitHubCommitAuthor(
    val name: String,
    val email: String,
    val date: String
)

/**
 * Статистика коммита
 */
@Serializable
data class GitHubCommitStats(
    val additions: Int,
    val deletions: Int,
    val total: Int
)

/**
 * Детали коммита с изменениями файлов
 */
@Serializable
data class GitHubCommitDetails(
    val sha: String,
    val message: String,
    val author: GitHubCommitAuthor,
    val committer: GitHubCommitAuthor,
    val date: String,
    val url: String,
    val stats: GitHubCommitStats,
    val files: List<GitHubCommitFile>
)

/**
 * Файл изменённый в коммите
 */
@Serializable
data class GitHubCommitFile(
    val filename: String,
    val status: String, // "added", "modified", "removed", "renamed"
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String? = null,
    val previousFilename: String? = null // for renamed files
)

/**
 * Элемент содержимого репозитория (файл или директория)
 */
@Serializable
data class GitHubContentItem(
    val name: String,
    val path: String,
    val type: String, // "file", "dir", "symlink", "submodule"
    val size: Long? = null,
    val sha: String,
    val url: String,
    val downloadUrl: String? = null
)

/**
 * Содержимое файла
 */
@Serializable
data class GitHubFileContent(
    val name: String,
    val path: String,
    val content: String,
    val encoding: String,
    val size: Long,
    val sha: String
)

/**
 * Элемент дерева файлов
 */
@Serializable
data class GitHubTreeItem(
    val path: String,
    val mode: String,
    val type: String, // "tree", "blob"
    val sha: String,
    val size: Long? = null
)

/**
 * Статистика активности автора
 */
@Serializable
data class GitHubAuthorStats(
    val author: String,
    val email: String,
    val commitCount: Int,
    val totalAdditions: Int,
    val totalDeletions: Int,
    val firstCommit: String,
    val lastCommit: String
)

/**
 * Сводка изменений в репозитории
 */
@Serializable
data class GitHubRepositorySummary(
    val repository: String,
    val period: String,
    val totalCommits: Int,
    val totalAuthors: Int,
    val totalAdditions: Int,
    val totalDeletions: Int,
    val authorStats: List<GitHubAuthorStats>,
    val topChangedFiles: List<String>
)