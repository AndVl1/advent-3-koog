package ru.andvl.chatter.koog.embeddings.rag

import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.embeddings.model.EmbeddingConfig
import ru.andvl.chatter.koog.embeddings.service.EmbeddingService
import ru.andvl.chatter.koog.embeddings.storage.ChunkVectorStorage
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("rag-service")

/**
 * RAG (Retrieval-Augmented Generation) service
 * Provides semantic search over indexed repositories
 */
internal class RAGService(
    private val storageRoot: Path,
    private val config: EmbeddingConfig
) {
    private var embeddingService: EmbeddingService? = null
    private var vectorStorage: ChunkVectorStorage? = null
    private var isInitialized = false

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
            val service = EmbeddingService(config)
            val available = service.checkAvailability()

            if (available) {
                embeddingService = service
                vectorStorage = ChunkVectorStorage(storageRoot, config, service)
                isInitialized = true
                logger.info("✅ RAG service initialized")
                true
            } else {
                logger.warn("⚠️ RAG service unavailable: Ollama not accessible")
                service.close()
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize RAG service", e)
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
     * @param similarityThreshold Minimum similarity score (0.0-1.0). Chunks below this threshold are filtered out.
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
                formattedContext = ""
            )
        }

        logger.debug("🔍 Searching for context: $query (threshold: $similarityThreshold)")

        val results = vectorStorage.searchSimilarChunks(
            query = query,
            repositoryName = repositoryName,
            topK = maxChunks,
            similarityThreshold = similarityThreshold
        )

        if (results.isEmpty()) {
            logger.debug("No relevant chunks found")
            return RAGContext(
                available = true,
                chunks = emptyList(),
                formattedContext = ""
            )
        }

        logger.info("✅ Found ${results.size} relevant chunks")

        val formattedContext = buildString {
            appendLine("## 📚 Relevant Code Context")
            appendLine()
            results.forEach { result ->
                appendLine("### ${result.chunk.metadata.filePath} (Similarity: ${String.format("%.2f", result.similarity)})")
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
            chunks = results.map { it.chunk },
            formattedContext = formattedContext
        )
    }

    /**
     * Check if repository is indexed
     */
    suspend fun isRepositoryIndexed(repositoryName: String): Boolean {
        if (!isInitialized || vectorStorage == null) return false
        return vectorStorage!!.isRepositoryIndexed(repositoryName)
    }

    fun close() {
        embeddingService?.close()
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
    val formattedContext: String
)
