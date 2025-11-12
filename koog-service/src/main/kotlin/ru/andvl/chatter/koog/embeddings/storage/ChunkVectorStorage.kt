package ru.andvl.chatter.koog.embeddings.storage

import ai.koog.rag.base.mostRelevantDocuments
import ai.koog.rag.vector.FileDocumentEmbeddingStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.embeddings.chunking.ChunkingStrategyFactory
import ru.andvl.chatter.koog.embeddings.model.DocumentChunk
import ru.andvl.chatter.koog.embeddings.model.EmbeddingConfig
import ru.andvl.chatter.koog.embeddings.service.EmbeddingService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

private val logger = LoggerFactory.getLogger("chunk-vector-storage")

/**
 * Vector storage for document chunks using Koog's EmbeddingBasedDocumentStorage
 * Combines custom code-aware chunking with Koog's RAG infrastructure
 *
 * Uses:
 * - Custom ChunkDocumentEmbedder for embedding generation
 * - JVMFileVectorStorage for persistent storage
 * - EmbeddingBasedDocumentStorage for ranking and retrieval
 */
internal class ChunkVectorStorage(
    private val storageRoot: Path,
    private val config: EmbeddingConfig,
    private val embeddingService: EmbeddingService
) {
    private val chunkEmbedder = ChunkDocumentEmbedder(embeddingService)
    private val chunkProvider = ChunkDocumentProvider()

    // Use Koog's EmbeddingBasedDocumentStorage for automatic ranking
    private val documentStorage = FileDocumentEmbeddingStorage(
        embedder = chunkEmbedder,
        documentProvider = chunkProvider,
        fs = ai.koog.rag.base.files.JVMFileSystemProvider.ReadWrite,
        root = storageRoot
    )

    /**
     * Index a repository by chunking files and storing embeddings
     */
    suspend fun indexRepository(repositoryPath: Path, repositoryName: String): IndexingResult = withContext(Dispatchers.IO) {
        try {
            logger.info("📊 Starting indexing for repository: $repositoryName")

            val eligibleFiles = findEligibleFiles(repositoryPath)
            logger.info("📄 Found ${eligibleFiles.size} eligible files")

            if (eligibleFiles.isEmpty()) {
                return@withContext IndexingResult(
                    success = false,
                    filesProcessed = 0,
                    chunksIndexed = 0,
                    error = "No eligible files found"
                )
            }

            var filesProcessed = 0
            var chunksIndexed = 0

            // Process files until we hit maxFiles or maxChunks limit
            for (file in eligibleFiles.take(config.maxFiles)) {
                // Check if we've reached the chunk limit
                if (chunksIndexed >= config.maxChunks) {
                    logger.info("⚠️ Reached chunk limit ($chunksIndexed/${config.maxChunks}). Stopping indexing.")
                    break
                }

                try {
                    val content = file.readText()
                    val relativeFilePath = repositoryPath.relativize(file).pathString

                    // Chunk the file
                    val strategy = ChunkingStrategyFactory.getStrategy(relativeFilePath)
                    val chunks = strategy.chunk(
                        filePath = relativeFilePath,
                        content = content,
                        repository = repositoryName,
                        config = config
                    )

                    logger.debug("Created ${chunks.size} chunks for $relativeFilePath")

                    // Generate and store embeddings for each chunk (with limit check)
                    for (chunk in chunks) {
                        if (chunksIndexed >= config.maxChunks) {
                            logger.debug("Chunk limit reached during file processing")
                            break
                        }

                        // Store chunk (embeddings are generated automatically by documentStorage)
                        storeChunk(chunk, repositoryName)
                        chunksIndexed++
                    }

                    filesProcessed++
                } catch (e: Exception) {
                    logger.error("Failed to process file ${file.name}: ${e.message}", e)
                }
            }

            logger.info("✅ Indexing complete: $filesProcessed files, $chunksIndexed chunks")

            IndexingResult(
                success = true,
                filesProcessed = filesProcessed,
                chunksIndexed = chunksIndexed,
                error = null
            )
        } catch (e: Exception) {
            logger.error("Failed to index repository: ${e.message}", e)
            IndexingResult(
                success = false,
                filesProcessed = 0,
                chunksIndexed = 0,
                error = e.message
            )
        }
    }

    /**
     * Search for similar chunks using semantic search
     * Uses Koog's mostRelevantDocuments() with similarity threshold
     */
    suspend fun searchSimilarChunks(
        query: String,
        repositoryName: String,
        topK: Int = 5,
        similarityThreshold: Double = 0.3  // Filter out chunks with similarity < 0.3
    ): List<ChunkSearchResult> = withContext(Dispatchers.IO) {
        try {
            // Use Koog's mostRelevantDocuments extension with automatic ranking and filtering
            val relevantChunks = documentStorage
                .mostRelevantDocuments(query, count = topK, similarityThreshold = similarityThreshold)
                .toList()

            logger.debug("Found ${relevantChunks.size} relevant chunks (threshold: $similarityThreshold)")

            // Convert to ChunkSearchResult
            relevantChunks.mapIndexed { index, chunk ->
                ChunkSearchResult(
                    chunk = chunk,
                    similarity = 1.0, // Similarity already computed by Koog, but not exposed
                    rank = index + 1
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to search chunks: ${e.message}", e)
            emptyList()
        }
    }


    /**
     * Check if repository is indexed
     */
    suspend fun isRepositoryIndexed(repositoryName: String): Boolean = withContext(Dispatchers.IO) {
        val repoDir = storageRoot.resolve(sanitizeRepositoryName(repositoryName))
        repoDir.exists() && repoDir.isDirectory()
    }

    private suspend fun storeChunk(chunk: DocumentChunk, repositoryName: String) {
        // Create chunk file with metadata
        createChunkFile(chunk, repositoryName)

        // Store using EmbeddingBasedDocumentStorage
        // It will automatically generate embeddings and store them
        documentStorage.store(chunk)
    }

    private fun createChunkFile(chunk: DocumentChunk, repositoryName: String): Path {
        val repoDir = storageRoot.resolve(sanitizeRepositoryName(repositoryName))
        repoDir.createDirectories()

        // Create chunk file with metadata
        val chunkFileName = "${chunk.id}.chunk"
        val chunkFile = repoDir.resolve(chunkFileName)

        // Write chunk metadata and content
        val chunkData = """
            |FILE: ${chunk.metadata.filePath}
            |TYPE: ${chunk.metadata.chunkType}
            |LINES: ${chunk.startLine}-${chunk.endLine}
            |LANGUAGE: ${chunk.metadata.language ?: "unknown"}
            |FUNCTION: ${chunk.metadata.functionName ?: "N/A"}
            |CLASS: ${chunk.metadata.className ?: "N/A"}
            |---
            |${chunk.content}
        """.trimMargin()

        chunkFile.writeText(chunkData)
        return chunkFile
    }

    private fun findEligibleFiles(rootPath: Path): List<Path> {
        val files = mutableListOf<Path>()

        Files.walk(rootPath)
            .filter { Files.isRegularFile(it) }
            .filter { path ->
                // Check file extension
                config.fileExtensions.any { ext -> path.name.endsWith(ext) }
            }
            .filter { path ->
                // Check exclude patterns
                val relativePath = rootPath.relativize(path).pathString
                config.excludePatterns.none { pattern ->
                    relativePath.contains(pattern.removeSurrounding("*", "*"))
                }
            }
            .forEach { files.add(it) }

        return files
    }

    private fun sanitizeRepositoryName(name: String): String {
        return name.replace("[^a-zA-Z0-9-_]".toRegex(), "_")
    }
}

/**
 * Result of indexing operation
 */
internal data class IndexingResult(
    val success: Boolean,
    val filesProcessed: Int,
    val chunksIndexed: Int,
    val error: String?
)

/**
 * Result of chunk search
 */
internal data class ChunkSearchResult(
    val chunk: DocumentChunk,
    val similarity: Double,
    val rank: Int
)
