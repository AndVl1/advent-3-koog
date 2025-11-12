package ru.andvl.chatter.koog.embeddings.model

/**
 * Configuration for embeddings generation and RAG retrieval
 */
internal data class EmbeddingConfig(
    val enabled: Boolean = false,
    val ollamaBaseUrl: String = "http://localhost:11434",
    val modelName: String = "zylonai/multilingual-e5-large",

    // Chunking configuration
    val chunkSize: Int = 512,
    val chunkOverlap: Int = 50,

    // Indexing limits
    val maxChunks: Int = 5000,  // Maximum total chunks across all files
    val maxFiles: Int = Int.MAX_VALUE,  // Maximum files to index (no limit by default)

    // Retrieval configuration
    val retrievalChunks: Int = 20,  // Number of chunks to retrieve for context (increased from 5)
    val similarityThreshold: Double = 0.3,  // Minimum similarity score for retrieval

    val fileExtensions: Set<String> = setOf(
        ".kt", ".java", ".py", ".js", ".ts", ".go", ".rs",
        ".md", ".txt", ".json", ".yaml", ".yml", ".xml"
    ),
    val excludePatterns: Set<String> = setOf(
        "*/build/*", "*/target/*", "*/node_modules/*",
        "*/.git/*", "*/.*"
    )
)
