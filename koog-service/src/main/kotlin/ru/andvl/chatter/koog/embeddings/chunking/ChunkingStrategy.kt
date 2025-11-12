package ru.andvl.chatter.koog.embeddings.chunking

import ru.andvl.chatter.koog.embeddings.model.DocumentChunk
import ru.andvl.chatter.koog.embeddings.model.EmbeddingConfig

/**
 * Strategy for splitting documents into chunks
 */
internal interface ChunkingStrategy {
    /**
     * Split document content into chunks
     *
     * @param filePath Path to the file
     * @param content File content
     * @param repository Repository name
     * @param config Embedding configuration
     * @return List of document chunks
     */
    fun chunk(
        filePath: String,
        content: String,
        repository: String,
        config: EmbeddingConfig
    ): List<DocumentChunk>

    /**
     * Check if this strategy can handle the given file
     */
    fun canHandle(filePath: String): Boolean
}

/**
 * Factory for creating appropriate chunking strategy based on file type
 */
internal object ChunkingStrategyFactory {
    private val strategies = listOf(
        CodeAwareChunkingStrategy(),
        MarkdownChunkingStrategy(),
        PlainTextChunkingStrategy()
    )

    fun getStrategy(filePath: String): ChunkingStrategy {
        return strategies.firstOrNull { it.canHandle(filePath) }
            ?: PlainTextChunkingStrategy()
    }
}
