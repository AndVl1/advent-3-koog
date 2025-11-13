package ru.andvl.chatter.koog.embeddings.rag

import ai.koog.prompt.executor.ollama.client.OllamaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.embeddings.model.EmbeddingConfig
import ru.andvl.chatter.koog.embeddings.model.RerankingStrategyType
import ru.andvl.chatter.koog.embeddings.reranking.*
import ru.andvl.chatter.koog.embeddings.storage.ChunkVectorStorage
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("rag-service")

/**
 * RAG (Retrieval-Augmented Generation) service
 * Provides semantic search over indexed repositories
 * Uses Koog's OllamaClient for LLM-based embeddings
 */
internal class RAGService(
    private val storageRoot: Path,
    private val config: EmbeddingConfig
) {
    private var vectorStorage: ChunkVectorStorage? = null
    private var isInitialized = false
    private val ollamaClient = OllamaClient(baseUrl = config.ollamaBaseUrl)

    /**
     * Initialize RAG service (check Ollama availability)
     */
    suspend fun initialize(): Boolean {
        if (isInitialized) return true

        if (!config.enabled) {
            logger.info("RAG disabled in configuration")
            return false
        }

        return try {
            // Test Ollama availability
            val available = checkOllamaAvailability(ollamaClient)

            if (available) {
                vectorStorage = ChunkVectorStorage(storageRoot, config)
                isInitialized = true
                logger.info("✅ RAG service initialized with OllamaClient")
                true
            } else {
                logger.warn("⚠️ RAG service unavailable: Ollama not accessible at ${config.ollamaBaseUrl}")
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize RAG service", e)
            false
        }
    }

    private suspend fun checkOllamaAvailability(client: OllamaClient): Boolean = withContext(Dispatchers.IO) {
        try {
            // Try to get models to check if Ollama is running
            client.getModels()
            true
        } catch (e: Exception) {
            logger.debug("Ollama availability check failed: ${e.message}")
            false
        }
    }

    /**
     * Index a repository for semantic search
     */
    suspend fun indexRepository(repositoryPath: Path, repositoryName: String): RAGIndexingResult {
        if (!isInitialized || vectorStorage == null) {
            return RAGIndexingResult(
                success = false,
                message = "RAG service not initialized",
                filesProcessed = 0,
                chunksIndexed = 0
            )
        }

        logger.info("📊 Indexing repository: $repositoryName")

        val result = vectorStorage!!.indexRepository(repositoryPath, repositoryName)

        return RAGIndexingResult(
            success = result.success,
            message = if (result.success) {
                "Successfully indexed ${result.filesProcessed} files with ${result.chunksIndexed} chunks"
            } else {
                result.error ?: "Unknown error"
            },
            filesProcessed = result.filesProcessed,
            chunksIndexed = result.chunksIndexed
        )
    }

    /**
     * Get relevant context for a query using semantic search
     *
     * @param query User's query for semantic search
     * @param repositoryName Repository to search in
     * @param maxChunks Maximum number of chunks to return (default from config or 20)
     * @param similarityThreshold Minimum similarity score (0.0-1.0). Used only for THRESHOLD strategy.
     */
    suspend fun getRelevantContext(
        query: String,
        repositoryName: String,
        maxChunks: Int = config.retrievalChunks,
        similarityThreshold: Double = config.similarityThreshold
    ): RAGContext {
        val vectorStorage = vectorStorage
        if (!isInitialized || vectorStorage == null) {
            return RAGContext(
                available = false,
                chunks = emptyList(),
                formattedContext = "",
                rerankingInfo = null
            )
        }

        logger.debug("🔍 Searching for context: $query (strategy: ${config.rerankingStrategy})")

        // Phase 1: Vector search (no filtering at this stage)
        val initialResults = vectorStorage.searchSimilarChunks(
            query = query,
            repositoryName = repositoryName,
            topK = maxChunks,
            similarityThreshold = 0.0  // Get all results for reranking
        )

        if (initialResults.isEmpty()) {
            logger.debug("No relevant chunks found")
            return RAGContext(
                available = true,
                chunks = emptyList(),
                formattedContext = "",
                rerankingInfo = RerankingInfo(
                    strategyUsed = config.rerankingStrategy.name,
                    initialCount = 0,
                    finalCount = 0,
                    filteredOut = 0
                )
            )
        }

        logger.info("📊 Vector search returned ${initialResults.size} chunks")
        logSimilarityDistribution(initialResults)

        // Phase 2: Reranking/Filtering
        val rerankingStrategy = createRerankingStrategy(similarityThreshold)
        val rerankedResults = rerankingStrategy.rerank(query, initialResults)

        val filteredCount = initialResults.size - rerankedResults.size
        logger.info("🎯 After reranking (${rerankingStrategy.name}): ${rerankedResults.size} chunks (filtered out: $filteredCount)")

        if (rerankedResults.isNotEmpty()) {
            logger.debug("Top result: similarity=${String.format("%.4f", rerankedResults.first().similarity)}, file=${rerankedResults.first().chunk.metadata.filePath}")
        }

        val formattedContext = buildString {
            appendLine("## 📚 Relevant Code Context")
            appendLine()
            rerankedResults.forEach { result ->
                appendLine("### ${result.chunk.metadata.filePath}")
                appendLine("**Similarity:** ${String.format("%.4f", result.similarity)}")
                appendLine("**Type:** ${result.chunk.metadata.chunkType}")
                if (result.chunk.metadata.functionName != null) {
                    appendLine("**Function:** ${result.chunk.metadata.functionName}")
                }
                if (result.chunk.metadata.className != null) {
                    appendLine("**Class:** ${result.chunk.metadata.className}")
                }
                appendLine("**Lines:** ${result.chunk.startLine}-${result.chunk.endLine}")
                appendLine()
                appendLine("```${result.chunk.metadata.language ?: ""}")
                appendLine(result.chunk.content)
                appendLine("```")
                appendLine()
            }
        }

        return RAGContext(
            available = true,
            chunks = rerankedResults.map { it.chunk },
            formattedContext = formattedContext,
            rerankingInfo = RerankingInfo(
                strategyUsed = rerankingStrategy.name,
                initialCount = initialResults.size,
                finalCount = rerankedResults.size,
                filteredOut = filteredCount,
                topSimilarities = rerankedResults.take(5).map { it.similarity }
            )
        )
    }

    /**
     * Create reranking strategy based on configuration
     */
    private fun createRerankingStrategy(threshold: Double): RerankingStrategy {
        return when (config.rerankingStrategy) {
            RerankingStrategyType.NONE -> NoFilterStrategy()
            RerankingStrategyType.THRESHOLD -> ThresholdFilterStrategy(threshold)
            RerankingStrategyType.ADAPTIVE -> AdaptiveThresholdStrategy(config.adaptiveThresholdRatio)
            RerankingStrategyType.SCORE_GAP -> ScoreGapFilterStrategy(config.scoreGapThreshold)
            RerankingStrategyType.MMR -> MMRFilterStrategy(config.mmrLambda, config.retrievalChunks)
            RerankingStrategyType.MULTI_CRITERIA -> MultiCriteriaFilterStrategy(
                minSimilarity = threshold,
                preferFunctions = true
            )
            RerankingStrategyType.OLLAMA_CONTEXTUAL_EMBEDDINGS -> OllamaEmbeddingRerankingStrategy(
                ollamaClient = ollamaClient,
                embeddingModelName = config.modelName,
                topK = config.retrievalChunks,
                truncateChunks = config.ollamaRerankTruncateChunks,
                maxChunkLength = config.ollamaRerankMaxChunkLength
            )
        }
    }

    /**
     * Log similarity score distribution for analysis
     */
    private fun logSimilarityDistribution(results: List<ru.andvl.chatter.koog.embeddings.storage.ChunkSearchResult>) {
        if (results.isEmpty()) return

        val similarities = results.map { it.similarity }
        val min = similarities.minOrNull() ?: 0.0
        val max = similarities.maxOrNull() ?: 0.0
        val avg = similarities.average()

        logger.debug("Similarity distribution: min=${String.format("%.4f", min)}, max=${String.format("%.4f", max)}, avg=${String.format("%.4f", avg)}")
    }

    /**
     * Check if repository is indexed
     */
    suspend fun isRepositoryIndexed(repositoryName: String): Boolean {
        if (!isInitialized || vectorStorage == null) return false
        return vectorStorage!!.isRepositoryIndexed(repositoryName)
    }

    fun close() {
        isInitialized = false
    }
}

/**
 * Result of RAG indexing
 */
internal data class RAGIndexingResult(
    val success: Boolean,
    val message: String,
    val filesProcessed: Int,
    val chunksIndexed: Int
)

/**
 * RAG context with relevant chunks
 */
internal data class RAGContext(
    val available: Boolean,
    val chunks: List<ru.andvl.chatter.koog.embeddings.model.DocumentChunk>,
    val formattedContext: String,
    val rerankingInfo: RerankingInfo? = null
)

/**
 * Information about reranking process for analysis
 */
internal data class RerankingInfo(
    val strategyUsed: String,
    val initialCount: Int,
    val finalCount: Int,
    val filteredOut: Int,
    val topSimilarities: List<Double> = emptyList()
)
