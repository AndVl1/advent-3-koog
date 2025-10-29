package ru.andvl.mcp.telegraph

import kotlinx.serialization.Serializable

/**
 * Telegraph account information
 */
@Serializable
data class TelegraphAccount(
    val shortName: String,
    val authorName: String? = null,
    val authorUrl: String? = null,
    val authUrl: String? = null,
    val pageCount: Int? = null,
    val accessToken: String? = null
)

/**
 * Telegraph page node element
 */
@Serializable
data class TelegraphNode(
    val tag: String,
    val children: List<String>? = null,
    val attrs: Map<String, String>? = null
)

/**
 * Telegraph page information
 */
@Serializable
data class TelegraphPage(
    val path: String,
    val url: String,
    val title: String,
    val description: String? = null,
    val authorName: String? = null,
    val authorUrl: String? = null,
    val imageUrl: String? = null,
    val content: List<TelegraphNode>? = null,
    val views: Int? = null,
    val canEdit: Boolean? = null
)

/**
 * List of Telegraph pages
 */
@Serializable
data class TelegraphPageList(
    val totalPages: Int,
    val pages: List<TelegraphPage>
)

/**
 * Telegraph page views statistics
 */
@Serializable
data class TelegraphPageViews(
    val views: Int
)

/**
 * Telegraph API response wrapper
 */
@Serializable
data class TelegraphResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val error: String? = null
)
