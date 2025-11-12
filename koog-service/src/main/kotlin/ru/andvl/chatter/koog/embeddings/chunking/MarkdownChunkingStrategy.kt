package ru.andvl.chatter.koog.embeddings.chunking

import ru.andvl.chatter.koog.embeddings.model.*
import java.util.*

/**
 * Markdown-aware chunking strategy that splits by sections
 */
internal class MarkdownChunkingStrategy : ChunkingStrategy {
    override fun canHandle(filePath: String): Boolean {
        return filePath.endsWith(".md", ignoreCase = true)
    }

    override fun chunk(
        filePath: String,
        content: String,
        repository: String,
        config: EmbeddingConfig
    ): List<DocumentChunk> {
        val lines = content.lines()
        val chunks = mutableListOf<DocumentChunk>()
        var currentSection = mutableListOf<String>()
        var sectionStartLine = 0

        lines.forEachIndexed { index, line ->
            // Detect section headers (# ## ### etc.)
            if (line.trim().startsWith("#") && currentSection.isNotEmpty()) {
                // Save previous section
                val sectionContent = currentSection.joinToString("\n")
                if (sectionContent.isNotBlank()) {
                    chunks.add(
                        createChunk(
                            content = sectionContent,
                            filePath = filePath,
                            repository = repository,
                            startLine = sectionStartLine,
                            endLine = index - 1
                        )
                    )
                }
                // Start new section
                currentSection = mutableListOf(line)
                sectionStartLine = index
            } else {
                currentSection.add(line)
            }
        }

        // Add last section
        if (currentSection.isNotEmpty()) {
            val sectionContent = currentSection.joinToString("\n")
            if (sectionContent.isNotBlank()) {
                chunks.add(
                    createChunk(
                        content = sectionContent,
                        filePath = filePath,
                        repository = repository,
                        startLine = sectionStartLine,
                        endLine = lines.size - 1
                    )
                )
            }
        }

        // If no sections found, use paragraph-based chunking
        if (chunks.isEmpty()) {
            return paragraphBasedChunk(lines, filePath, repository, config)
        }

        return chunks.take(config.maxChunks)
    }

    private fun createChunk(
        content: String,
        filePath: String,
        repository: String,
        startLine: Int,
        endLine: Int
    ): DocumentChunk {
        return DocumentChunk(
            id = UUID.randomUUID().toString(),
            content = content,
            metadata = ChunkMetadata(
                filePath = filePath,
                fileName = filePath.substringAfterLast('/'),
                fileType = FileType.MARKDOWN,
                repository = repository,
                chunkType = ChunkType.SECTION
            ),
            startLine = startLine,
            endLine = endLine
        )
    }

    private fun paragraphBasedChunk(
        lines: List<String>,
        filePath: String,
        repository: String,
        config: EmbeddingConfig
    ): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()
        var currentParagraph = mutableListOf<String>()
        var paragraphStartLine = 0

        lines.forEachIndexed { index, line ->
            if (line.isBlank() && currentParagraph.isNotEmpty()) {
                // End of paragraph
                val paragraphContent = currentParagraph.joinToString("\n")
                if (paragraphContent.isNotBlank()) {
                    chunks.add(
                        DocumentChunk(
                            id = UUID.randomUUID().toString(),
                            content = paragraphContent,
                            metadata = ChunkMetadata(
                                filePath = filePath,
                                fileName = filePath.substringAfterLast('/'),
                                fileType = FileType.MARKDOWN,
                                repository = repository,
                                chunkType = ChunkType.PARAGRAPH
                            ),
                            startLine = paragraphStartLine,
                            endLine = index - 1
                        )
                    )
                }
                currentParagraph = mutableListOf()
            } else if (line.isNotBlank()) {
                if (currentParagraph.isEmpty()) {
                    paragraphStartLine = index
                }
                currentParagraph.add(line)
            }
        }

        // Add last paragraph
        if (currentParagraph.isNotEmpty()) {
            val paragraphContent = currentParagraph.joinToString("\n")
            if (paragraphContent.isNotBlank()) {
                chunks.add(
                    DocumentChunk(
                        id = UUID.randomUUID().toString(),
                        content = paragraphContent,
                        metadata = ChunkMetadata(
                            filePath = filePath,
                            fileName = filePath.substringAfterLast('/'),
                            fileType = FileType.MARKDOWN,
                            repository = repository,
                            chunkType = ChunkType.PARAGRAPH
                        ),
                        startLine = paragraphStartLine,
                        endLine = lines.size - 1
                    )
                )
            }
        }

        return chunks
    }
}
