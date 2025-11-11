package ru.andvl.chatter.koog.embeddings.model

import kotlinx.serialization.Serializable

/**
 * Represents a chunk of a document with metadata
 */
@Serializable
internal data class DocumentChunk(
    val id: String,
    val content: String,
    val metadata: ChunkMetadata,
    val startLine: Int,
    val endLine: Int
)

/**
 * Metadata associated with a document chunk
 */
@Serializable
internal data class ChunkMetadata(
    val filePath: String,
    val fileName: String,
    val fileType: FileType,
    val repository: String,
    val chunkType: ChunkType,
    val language: String? = null,
    val functionName: String? = null,
    val className: String? = null
)

/**
 * Type of file being chunked
 */
@Serializable
internal enum class FileType {
    CODE,
    MARKDOWN,
    PLAIN_TEXT,
    UNKNOWN
}

/**
 * Type of chunk
 */
@Serializable
internal enum class ChunkType {
    FUNCTION,
    CLASS,
    SECTION,
    PARAGRAPH,
    CODE_BLOCK,
    FULL_DOCUMENT
}
