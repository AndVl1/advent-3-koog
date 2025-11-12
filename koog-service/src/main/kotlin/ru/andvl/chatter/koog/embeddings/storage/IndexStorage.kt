package ru.andvl.chatter.koog.embeddings.storage

import ru.andvl.chatter.koog.embeddings.model.EmbeddingIndex
import ru.andvl.chatter.koog.embeddings.model.SearchResult

/**
 * Interface for storing and retrieving embedding indices
 */
internal interface IndexStorage {
    /**
     * Save embedding index to storage
     */
    suspend fun save(index: EmbeddingIndex)

    /**
     * Load embedding index from storage
     */
    suspend fun load(repository: String): EmbeddingIndex?

    /**
     * Check if index exists for repository
     */
    suspend fun exists(repository: String): Boolean

    /**
     * Delete index for repository
     */
    suspend fun delete(repository: String)

    /**
     * Search for similar chunks using cosine similarity
     */
    suspend fun search(
        repository: String,
        queryEmbedding: List<Double>,
        topK: Int = 5,
        minSimilarity: Double = 0.0
    ): List<SearchResult>
}
