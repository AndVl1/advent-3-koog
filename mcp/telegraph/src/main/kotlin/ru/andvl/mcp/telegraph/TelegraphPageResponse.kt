package ru.andvl.mcp.telegraph

import kotlinx.serialization.Serializable

/**
 * Wrapper for Telegraph page response with Markdown content
 */
@Serializable
data class TelegraphPageResponse(
    val path: String,
    val url: String,
    val title: String,
    val description: String? = null,
    val authorName: String? = null,
    val authorUrl: String? = null,
    val imageUrl: String? = null,
    val contentMarkdown: String? = null,
    val views: Int? = null,
    val canEdit: Boolean? = null
)