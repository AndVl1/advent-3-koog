package ru.andvl.chatter.koog.embeddings.reranking

import ru.andvl.chatter.koog.embeddings.model.DocumentChunk
import ru.andvl.chatter.koog.embeddings.storage.ChunkSearchResult

/**
 * Strategy for reranking and filtering search results after initial vector search
 */
internal interface RerankingStrategy {
    /**
     * Rerank and filter search results
     * @param query Original search query
     * @param results Initial search results with similarity scores
     * @return Filtered and reranked results
     */
    suspend fun rerank(query: String, results: List<ChunkSearchResult>): List<ChunkSearchResult>

    /**
     * Name of the strategy for logging/comparison
     */
    val name: String
}

/**
 * No filtering - baseline for comparison
 */
internal class NoFilterStrategy : RerankingStrategy {
    override suspend fun rerank(query: String, results: List<ChunkSearchResult>): List<ChunkSearchResult> {
        return results
    }

    override val name: String = "no-filter"
}

/**
 * Simple threshold-based filtering (current approach)
 */
internal class ThresholdFilterStrategy(
    private val threshold: Double
) : RerankingStrategy {
    override suspend fun rerank(query: String, results: List<ChunkSearchResult>): List<ChunkSearchResult> {
        return results.filter { it.similarity >= threshold }
    }

    override val name: String = "threshold-$threshold"
}

/**
 * Adaptive threshold based on score distribution
 * Filters out results that are significantly worse than the best result
 */
internal class AdaptiveThresholdStrategy(
    private val relativeThreshold: Double = 0.8  // Keep results within 80% of best score
) : RerankingStrategy {
    override suspend fun rerank(query: String, results: List<ChunkSearchResult>): List<ChunkSearchResult> {
        if (results.isEmpty()) return results

        val bestScore = results.maxOf { it.similarity }
        val adaptiveThreshold = bestScore * relativeThreshold

        return results.filter { it.similarity >= adaptiveThreshold }
    }

    override val name: String = "adaptive-${relativeThreshold}"
}

/**
 * Score gap-based filtering
 * Keeps results until there's a significant drop in similarity score
 */
internal class ScoreGapFilterStrategy(
    private val maxGap: Double = 0.15  // Maximum allowed gap between consecutive results
) : RerankingStrategy {
    override suspend fun rerank(query: String, results: List<ChunkSearchResult>): List<ChunkSearchResult> {
        if (results.isEmpty()) return results

        val filtered = mutableListOf<ChunkSearchResult>()
        var previousScore: Double? = null

        for (result in results.sortedByDescending { it.similarity }) {
            if (previousScore == null) {
                // Always keep the best result
                filtered.add(result)
            } else {
                val gap = previousScore - result.similarity
                if (gap <= maxGap) {
                    filtered.add(result)
                } else {
                    // Gap too large, stop here
                    break
                }
            }
            previousScore = result.similarity
        }

        return filtered
    }

    override val name: String = "score-gap-$maxGap"
}

/**
 * MMR (Maximal Marginal Relevance) - balances relevance and diversity
 * Reduces redundancy by penalizing similar documents
 */
internal class MMRFilterStrategy(
    private val lambda: Double = 0.7,  // Balance: 1.0 = pure relevance, 0.0 = pure diversity
    private val maxResults: Int = 10
) : RerankingStrategy {
    override suspend fun rerank(query: String, results: List<ChunkSearchResult>): List<ChunkSearchResult> {
        if (results.isEmpty()) return results

        val selected = mutableListOf<ChunkSearchResult>()
        val remaining = results.toMutableList()

        // Always pick the most relevant first
        val best = remaining.maxByOrNull { it.similarity } ?: return results
        selected.add(best)
        remaining.remove(best)

        // Iteratively select documents that maximize MMR score
        while (selected.size < maxResults && remaining.isNotEmpty()) {
            val nextDoc = remaining.maxByOrNull { candidate ->
                val relevance = candidate.similarity

                // Calculate maximum similarity to already selected documents
                val maxSimilarity = selected.maxOfOrNull { selected ->
                    // Simple diversity metric based on content overlap
                    calculateContentSimilarity(candidate.chunk, selected.chunk)
                } ?: 0.0

                // MMR formula: λ * Sim(q, d) - (1-λ) * max Sim(d, s)
                lambda * relevance - (1 - lambda) * maxSimilarity
            }

            if (nextDoc != null) {
                selected.add(nextDoc)
                remaining.remove(nextDoc)
            } else {
                break
            }
        }

        return selected
    }

    private fun calculateContentSimilarity(chunk1: DocumentChunk, chunk2: DocumentChunk): Double {
        // Simple Jaccard similarity on words
        val words1 = chunk1.content.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }.toSet()
        val words2 = chunk2.content.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }.toSet()

        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size

        return intersection.toDouble() / union
    }

    override val name: String = "mmr-lambda$lambda"
}

/**
 * Multi-criteria filtering combining several factors
 */
internal class MultiCriteriaFilterStrategy(
    private val minSimilarity: Double = 0.3,
    private val minContentLength: Int = 50,
    private val preferFunctions: Boolean = true
) : RerankingStrategy {
    override suspend fun rerank(query: String, results: List<ChunkSearchResult>): List<ChunkSearchResult> {
        return results
            .filter { it.similarity >= minSimilarity }
            .filter { it.chunk.content.length >= minContentLength }
            .sortedByDescending { result ->
                var score = result.similarity

                // Boost function chunks
                if (preferFunctions && result.chunk.metadata.chunkType.name.lowercase() == "function") {
                    score *= 1.2
                }

                // Boost chunks with metadata (function/class names)
                if (result.chunk.metadata.functionName != null || result.chunk.metadata.className != null) {
                    score *= 1.1
                }

                score
            }
    }

    override val name: String = "multi-criteria"
}
