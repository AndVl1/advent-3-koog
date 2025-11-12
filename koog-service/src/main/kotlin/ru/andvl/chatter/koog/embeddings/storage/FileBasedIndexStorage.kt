package ru.andvl.chatter.koog.embeddings.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.embeddings.model.EmbeddingIndex
import ru.andvl.chatter.koog.embeddings.model.SearchResult
import java.io.File
import kotlin.math.sqrt

private val logger = LoggerFactory.getLogger("file-based-index-storage")

/**
 * File-based storage for embedding indices using JSON
 */
internal class FileBasedIndexStorage(
    private val storageDir: File
) : IndexStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
            logger.info("Created storage directory: ${storageDir.absolutePath}")
        }
    }

    override suspend fun save(index: EmbeddingIndex) = withContext(Dispatchers.IO) {
        try {
            val file = getIndexFile(index.repository)
            val jsonContent = json.encodeToString(index)
            file.writeText(jsonContent)
            logger.info("Saved embedding index for repository '${index.repository}' with ${index.entries.size} entries")
        } catch (e: Exception) {
            logger.error("Failed to save embedding index for repository '${index.repository}'", e)
            throw e
        }
    }

    override suspend fun load(repository: String): EmbeddingIndex? = withContext(Dispatchers.IO) {
        try {
            val file = getIndexFile(repository)
            if (!file.exists()) {
                logger.debug("Index file not found for repository '$repository'")
                return@withContext null
            }

            val jsonContent = file.readText()
            val index = json.decodeFromString<EmbeddingIndex>(jsonContent)
            logger.info("Loaded embedding index for repository '$repository' with ${index.entries.size} entries")
            index
        } catch (e: Exception) {
            logger.error("Failed to load embedding index for repository '$repository'", e)
            null
        }
    }

    override suspend fun exists(repository: String): Boolean = withContext(Dispatchers.IO) {
        getIndexFile(repository).exists()
    }

    override suspend fun delete(repository: String) = withContext(Dispatchers.IO) {
        try {
            val file = getIndexFile(repository)
            if (file.exists()) {
                file.delete()
                logger.info("Deleted embedding index for repository '$repository'")
            }
        } catch (e: Exception) {
            logger.error("Failed to delete embedding index for repository '$repository'", e)
        }
    }

    override suspend fun search(
        repository: String,
        queryEmbedding: List<Double>,
        topK: Int,
        minSimilarity: Double
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val index = load(repository) ?: run {
                logger.warn("No index found for repository '$repository'")
                return@withContext emptyList()
            }

            val queryNorm = sqrt(queryEmbedding.sumOf { it * it })

            // Calculate similarities
            val results = index.entries
                .map { entry ->
                    val similarity = cosineSimilarity(
                        queryEmbedding = queryEmbedding,
                        queryNorm = queryNorm,
                        docEmbedding = entry.embedding,
                        docNorm = entry.norm
                    )
                    entry to similarity
                }
                .filter { (_, similarity) -> similarity >= minSimilarity }
                .sortedByDescending { (_, similarity) -> similarity }
                .take(topK)
                .mapIndexed { index, (entry, similarity) ->
                    SearchResult(
                        chunk = entry.chunk,
                        similarity = similarity,
                        rank = index + 1
                    )
                }

            logger.info("Found ${results.size} results for query in repository '$repository'")
            results
        } catch (e: Exception) {
            logger.error("Failed to search in repository '$repository'", e)
            emptyList()
        }
    }

    private fun getIndexFile(repository: String): File {
        val sanitizedName = repository.replace("[^a-zA-Z0-9-_]".toRegex(), "_")
        return File(storageDir, "$sanitizedName.json")
    }

    /**
     * Optimized cosine similarity using pre-computed norms
     */
    private fun cosineSimilarity(
        queryEmbedding: List<Double>,
        queryNorm: Double,
        docEmbedding: List<Double>,
        docNorm: Double
    ): Double {
        var dotProduct = 0.0
        for (i in queryEmbedding.indices) {
            dotProduct += queryEmbedding[i] * docEmbedding[i]
        }
        return dotProduct / (queryNorm * docNorm)
    }
}
