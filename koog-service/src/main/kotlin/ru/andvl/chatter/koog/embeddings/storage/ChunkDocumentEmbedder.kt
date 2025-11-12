package ru.andvl.chatter.koog.embeddings.storage

import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.vector.DocumentEmbedder
import ru.andvl.chatter.koog.embeddings.model.DocumentChunk
import ru.andvl.chatter.koog.embeddings.service.EmbeddingService
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * Document embedder for DocumentChunk
 * Integrates our custom chunking with Koog's embedding infrastructure
 */
internal class ChunkDocumentEmbedder(
    private val embeddingService: EmbeddingService
) : DocumentEmbedder<DocumentChunk> {

    override suspend fun embed(document: DocumentChunk): Vector {
        val embedding = embeddingService.embed(document.content)
        return Vector(embedding ?: emptyList())
    }

    override suspend fun embed(text: String): Vector {
        val embedding = embeddingService.embed(text)
        return Vector(embedding ?: emptyList())
    }

    override fun diff(embedding1: Vector, embedding2: Vector): Double {
        // Koog uses distance (lower is better), cosine similarity is 0-1 (higher is better)
        // So we convert: distance = 1.0 - similarity
        return 1.0 - embedding1.cosineSimilarity(embedding2)
    }
}

/**
 * Document provider for chunk files stored on disk
 * Reads chunk files and deserializes them back to DocumentChunk objects
 */
internal class ChunkDocumentProvider : DocumentProvider<Path, DocumentChunk> {

    override suspend fun document(path: Path): DocumentChunk? {
        return try {
            deserializeChunk(path)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun text(document: DocumentChunk): CharSequence {
        return document.content
    }

    private fun deserializeChunk(documentPath: Path): DocumentChunk {
        val content = documentPath.readText()
        val lines = content.lines()

        // Parse metadata
        val metadata = lines.takeWhile { it != "---" }
            .associate { line ->
                val parts = line.split(": ", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null to null
            }
            .filterKeys { it != null }
            .mapKeys { it.key!! }

        val chunkContent = lines.dropWhile { it != "---" }.drop(1).joinToString("\n")
        val lineRange = metadata["LINES"]?.split("-")

        return DocumentChunk(
            id = documentPath.nameWithoutExtension,
            content = chunkContent,
            metadata = ru.andvl.chatter.koog.embeddings.model.ChunkMetadata(
                filePath = metadata["FILE"] ?: "",
                fileName = metadata["FILE"]?.substringAfterLast('/') ?: "",
                fileType = ru.andvl.chatter.koog.embeddings.model.FileType.CODE,
                repository = documentPath.parent?.fileName?.toString() ?: "",
                chunkType = ru.andvl.chatter.koog.embeddings.model.ChunkType.valueOf(
                    metadata["TYPE"] ?: "CODE_BLOCK"
                ),
                language = metadata["LANGUAGE"]?.takeIf { it != "unknown" },
                functionName = metadata["FUNCTION"]?.takeIf { it != "N/A" },
                className = metadata["CLASS"]?.takeIf { it != "N/A" }
            ),
            startLine = lineRange?.getOrNull(0)?.toIntOrNull() ?: 0,
            endLine = lineRange?.getOrNull(1)?.toIntOrNull() ?: 0
        )
    }
}
