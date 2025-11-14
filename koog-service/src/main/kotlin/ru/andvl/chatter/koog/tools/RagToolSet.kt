package ru.andvl.chatter.koog.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.embeddings.model.EmbeddingConfig
import ru.andvl.chatter.koog.embeddings.rag.RAGService

/**
 * Context holder for RAG tool - allows passing dynamic data to statically registered tool
 */
internal object RagToolContext {
    var ragService: RAGService? = null
    var repositoryName: String = ""
    var config: EmbeddingConfig = EmbeddingConfig()
}

@LLMDescription("Tools for semantic code search in the repository using RAG (Retrieval-Augmented Generation)")
internal class RagToolSet : ToolSet {
    private val logger = LoggerFactory.getLogger(RagToolSet::class.java)

    @Tool("search-code-semantically")
    @LLMDescription(
        """Search for code in the indexed repository using semantic search.
        This finds the most relevant code chunks based on the meaning of your query, not just keywords.
        Use this when you need to find specific implementations, patterns, or code examples."""
    )
    fun searchCodeSemantically(
        @LLMDescription("Natural language description of what code you're looking for (e.g., 'authentication logic', 'database connection setup', 'error handling')")
        query: String,
        @LLMDescription("Number of code chunks to return (default: 10, max: 20)")
        topK: Int = 15
    ): SearchCodeResult = runBlocking {
        logger.info("🔍 Semantic search query: '$query' (topK: $topK)")

        val ragService = RagToolContext.ragService
        val repositoryName = RagToolContext.repositoryName
        val config = RagToolContext.config

        if (ragService == null || repositoryName.isEmpty()) {
            return@runBlocking SearchCodeResult(
                success = false,
                query = query,
                chunks = emptyList(),
                message = "RAG service not available. Repository may not be indexed yet."
            )
        }

        if (!ragService.isRepositoryIndexed(repositoryName)) {
            return@runBlocking SearchCodeResult(
                success = false,
                query = query,
                chunks = emptyList(),
                message = "Repository not indexed. RAG is not available for this repository."
            )
        }

        val limitedTopK = topK.coerceIn(1, 20)
        val context = ragService.getRelevantContext(
            query = query,
            repositoryName = repositoryName,
            maxChunks = limitedTopK,
            similarityThreshold = config.similarityThreshold
        )

        if (!context.available) {
            return@runBlocking SearchCodeResult(
                success = false,
                query = query,
                chunks = emptyList(),
                message = "RAG service not available"
            )
        }

        val codeChunks = context.chunks.map { chunk ->
            CodeChunk(
                filePath = chunk.metadata.filePath,
                content = chunk.content,
                language = chunk.metadata.language,
                functionName = chunk.metadata.functionName,
                className = chunk.metadata.className,
                startLine = chunk.startLine,
                endLine = chunk.endLine,
                chunkType = chunk.metadata.chunkType.name
            )
        }

        logger.info("✅ Found ${codeChunks.size} relevant code chunks")

        SearchCodeResult(
            success = true,
            query = query,
            chunks = codeChunks,
            message = "Found ${codeChunks.size} relevant code chunks"
        )
    }
}

@Serializable
data class SearchCodeResult(
    @SerialName("success")
    val success: Boolean,
    @SerialName("query")
    val query: String,
    @SerialName("chunks")
    val chunks: List<CodeChunk>,
    @SerialName("message")
    val message: String
)

@Serializable
data class CodeChunk(
    @SerialName("file_path")
    val filePath: String,
    @SerialName("content")
    val content: String,
    @SerialName("language")
    val language: String? = null,
    @SerialName("function_name")
    val functionName: String? = null,
    @SerialName("class_name")
    val className: String? = null,
    @SerialName("start_line")
    val startLine: Int,
    @SerialName("end_line")
    val endLine: Int,
    @SerialName("chunk_type")
    val chunkType: String
)
