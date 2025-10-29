package ru.andvl.mcp.telegraph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Telegraph account information
 */
@Serializable
data class TelegraphAccount(
    @SerialName("short_name") val shortName: String,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_url") val authorUrl: String? = null,
    @SerialName("auth_url") val authUrl: String? = null,
    @SerialName("page_count") val pageCount: Int? = null,
    @SerialName("access_token") val accessToken: String? = null
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
    @SerialName("path") val path: String,
    @SerialName("url") val url: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_url") val authorUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("content") val content: List<TelegraphNode>? = null,
    @SerialName("views") val views: Int? = null,
    @SerialName("can_edit") val canEdit: Boolean? = null
)

/**
 * List of Telegraph pages
 */
@Serializable
data class TelegraphPageList(
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("pages") val pages: List<TelegraphPage> = emptyList()
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
