package ru.andvl.chatter.koog.embeddings.chunking

import ru.andvl.chatter.koog.embeddings.model.*
import java.util.*

/**
 * Plain text chunking strategy using sliding window
 */
internal class PlainTextChunkingStrategy : ChunkingStrategy {
    override fun canHandle(filePath: String): Boolean {
        return true // Fallback strategy for all files
    }

    override fun chunk(
        filePath: String,
        content: String,
        repository: String,
        config: EmbeddingConfig
    ): List<DocumentChunk> {
        val lines = content.lines()
        val chunks = mutableListOf<DocumentChunk>()
        var currentLine = 0

        while (currentLine < lines.size) {
            val endLine = minOf(currentLine + config.chunkSize, lines.size)
            val chunkContent = lines.subList(currentLine, endLine).joinToString("\n")

            if (chunkContent.isNotBlank()) {
                chunks.add(
                    DocumentChunk(
                        id = UUID.randomUUID().toString(),
                        content = chunkContent,
                        metadata = ChunkMetadata(
                            filePath = filePath,
                            fileName = filePath.substringAfterLast('/'),
                            fileType = detectFileType(filePath),
                            repository = repository,
                            chunkType = ChunkType.PARAGRAPH
                        ),
                        startLine = currentLine,
                        endLine = endLine - 1
                    )
                )
            }

            currentLine += config.chunkSize - config.chunkOverlap
        }

        return chunks.take(config.maxChunks)
    }

    private fun detectFileType(filePath: String): FileType {
        return when {
            filePath.endsWith(".md") -> FileType.MARKDOWN
            filePath.matches(Regex(".*\\.(kt|java|py|js|ts|go|rs|cpp|c|h)$")) -> FileType.CODE
            filePath.endsWith(".txt") -> FileType.PLAIN_TEXT
            else -> FileType.UNKNOWN
        }
    }
}
