package ru.andvl.chatter.koog.embeddings.storage

import ai.koog.rag.base.files.DocumentProvider
import kotlinx.serialization.json.Json
import ru.andvl.chatter.koog.embeddings.model.DocumentChunk
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Document provider for chunk files stored on disk
 * Uses JSON serialization to preserve all DocumentChunk metadata
 * Used by Koog's TextDocumentEmbedder to read/write DocumentChunk objects
 */
internal class ChunkDocumentProvider : DocumentProvider<Path, DocumentChunk> {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    override suspend fun document(path: Path): DocumentChunk? {
        return try {
            val jsonContent = path.readText()
            json.decodeFromString<DocumentChunk>(jsonContent)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun text(document: DocumentChunk): CharSequence {
        // Serialize entire DocumentChunk to JSON so metadata is preserved
        return json.encodeToString(DocumentChunk.serializer(), document)
    }
}
