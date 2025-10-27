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
    val author: String,
    val date: String
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