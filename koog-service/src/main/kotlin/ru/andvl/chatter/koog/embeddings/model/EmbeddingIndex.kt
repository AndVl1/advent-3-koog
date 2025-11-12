package ru.andvl.chatter.koog.embeddings.model

import kotlinx.serialization.Serializable

/**
 * Index containing document chunks and their embeddings
 */
@Serializable
internal data class EmbeddingIndex(
    val repository: String,
    val createdAt: Long,
    val modelName: String,
    val entries: List<EmbeddingEntry>
)

/**
 * Single entry in the embedding index
 */
@Serializable
internal data class EmbeddingEntry(
    val chunk: DocumentChunk,
    val embedding: List<Double>,
    val norm: Double // Pre-computed L2 norm for faster cosine similarity
)

/**
 * Result of similarity search
 */
internal data class SearchResult(
    val chunk: DocumentChunk,
    val similarity: Double,
    val rank: Int
)
