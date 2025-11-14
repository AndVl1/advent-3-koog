package ru.andvl.chatter.koog.embeddings.reranking

import ai.koog.embeddings.base.Vector
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.embeddings.local.OllamaEmbeddingModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.embeddings.storage.ChunkSearchResult

private val logger = LoggerFactory.getLogger("ollama-embedding-reranking")

/**
 * Reranking через contextual embeddings в Ollama
 *
 * Вместо простого cosine similarity между независимыми embeddings,
 * используется контекстный подход:
 * - Query embedding учитывает цель поиска кода
 * - Document embedding учитывает контекст запроса
 *
 * Это улучшает качество по сравнению с bi-encoder approach.
 *
 * Преимущества:
 * - Более точно чем обычный cosine similarity
 * - Быстрее чем LLM-as-judge
 * - Использует имеющуюся embedding модель
 * - Стабильно (не зависит от LLM парсинга)
 *
 * @param ollamaClient Клиент для Ollama API
 * @param embeddingModel Модель для embeddings (по умолчанию из конфига)
 * @param topK Количество результатов для возврата
 * @param truncateChunks Обрезать длинные chunks для ускорения
 * @param maxChunkLength Максимальная длина chunk для embedding
 */
internal class OllamaEmbeddingRerankingStrategy(
    ollamaClient: OllamaClient,
    @Suppress("UNUSED_PARAMETER") embeddingModelName: String,  // Kept for API compatibility
    private val topK: Int = 10,
    private val truncateChunks: Boolean = true,
    private val maxChunkLength: Int = 1000
) : RerankingStrategy {

    // Create LLMEmbedder for embeddings
    // Always uses MULTILINGUAL_E5 as it's the same model used in ChunkVectorStorage
    private val embedder = LLMEmbedder(
        ollamaClient,
        OllamaEmbeddingModels.MULTILINGUAL_E5
    )

    override suspend fun rerank(
        query: String,
        results: List<ChunkSearchResult>
    ): List<ChunkSearchResult> {
        if (results.isEmpty()) return results

        val startTime = System.currentTimeMillis()
        logger.info("🔄 Starting contextual embedding reranking for ${results.size} chunks")

        try {
            // Phase 1: Get query embedding with context
            val queryEmbedding = getQueryEmbedding(query)
            logger.debug("✅ Got query embedding")

            // Phase 2: Get document embeddings with context (parallel)
            val rerankedResults = coroutineScope {
                results.map { result ->
                    async {
                        val docEmbedding = getDocumentEmbedding(query, result)
                        val newSimilarity = cosineSimilarity(queryEmbedding, docEmbedding)
                        result.copy(similarity = newSimilarity)
                    }
                }.awaitAll()
            }

            // Phase 3: Sort and take top-K
            val finalResults = rerankedResults
                .sortedByDescending { it.similarity }
                .take(topK)

            val elapsed = System.currentTimeMillis() - startTime
            logger.info("✅ Contextual reranking complete: ${finalResults.size} chunks in ${elapsed}ms")

            if (finalResults.isNotEmpty()) {
                logger.debug("Top result: similarity=${String.format("%.4f", finalResults.first().similarity)}")
            }

            return finalResults

        } catch (e: Exception) {
            logger.error("❌ Contextual reranking failed: ${e.message}", e)
            // Fallback: return original results sorted by original similarity
            return results.sortedByDescending { it.similarity }.take(topK)
        }
    }

    /**
     * Get query embedding with retrieval context
     */
    private suspend fun getQueryEmbedding(query: String): Vector {
        val contextualPrompt = buildQueryPrompt(query)
        return getEmbedding(contextualPrompt)
    }

    /**
     * Get document embedding with query context
     */
    private suspend fun getDocumentEmbedding(
        query: String,
        result: ChunkSearchResult
    ): Vector {
        val contextualPrompt = buildDocumentPrompt(query, result)
        return getEmbedding(contextualPrompt)
    }

    /**
     * Build contextual prompt for query embedding
     */
    private fun buildQueryPrompt(query: String): String {
        return "Represent this query for retrieving relevant code: $query"
    }

    /**
     * Build contextual prompt for document embedding
     */
    private fun buildDocumentPrompt(query: String, result: ChunkSearchResult): String {
        val chunk = result.chunk
        val content = if (truncateChunks && chunk.content.length > maxChunkLength) {
            chunk.content.take(maxChunkLength) + "..."
        } else {
            chunk.content
        }

        // Include metadata for better context
        val metadata = buildString {
            append("File: ${chunk.metadata.filePath}")
            chunk.metadata.functionName?.let { append(", Function: $it") }
            chunk.metadata.className?.let { append(", Class: $it") }
        }

        return """
            Represent this code for answering query "$query":
            [$metadata]
            $content
        """.trimIndent()
    }

    /**
     * Get embedding from Ollama using LLMEmbedder
     * Returns Vector directly for use with Koog's optimized similarity methods
     */
    private suspend fun getEmbedding(text: String): Vector {
        try {
            return embedder.embed(text)
        } catch (e: Exception) {
            logger.error("Failed to get embedding: ${e.message}")
            throw e
        }
    }

    /**
     * Calculate cosine similarity using Koog's optimized Vector implementation
     * Koog's implementation includes:
     * - Automatic dimension checking
     * - Null vector handling
     * - Kahan summation for numerical accuracy
     */
    private fun cosineSimilarity(vector1: Vector, vector2: Vector): Double {
        return vector1.cosineSimilarity(vector2)
    }

    override val name: String = "ollama-contextual-embeddings"
}
