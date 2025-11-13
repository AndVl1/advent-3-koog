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

    // Reranking configuration
    val rerankingStrategy: RerankingStrategyType = RerankingStrategyType.NONE,
    val adaptiveThresholdRatio: Double = 0.8,  // For adaptive strategy
    val scoreGapThreshold: Double = 0.15,  // For score-gap strategy
    val mmrLambda: Double = 0.7,  // For MMR strategy (1.0 = pure relevance, 0.0 = pure diversity)

    // Ollama reranking configuration (for OLLAMA_CONTEXTUAL_EMBEDDINGS)
    val ollamaRerankTruncateChunks: Boolean = true,  // Truncate long chunks for speed
    val ollamaRerankMaxChunkLength: Int = 1000,  // Max chunk length for embedding

    val fileExtensions: Set<String> = setOf(
        ".kt", ".java", ".py", ".js", ".ts", ".go", ".rs",
        ".md", ".txt", ".json", ".yaml", ".yml", ".xml"
    ),
    val excludePatterns: Set<String> = setOf(
        "*/build/*", "*/target/*", "*/node_modules/*",
        "*/.git/*", "*/.*"
    )
)

/**
 * Type of reranking strategy to use
 */
enum class RerankingStrategyType {
    /** No filtering - returns all results (baseline for comparison) */
    NONE,

    /** Simple threshold-based filtering (default) */
    THRESHOLD,

    /** Adaptive threshold based on best result (keeps results within X% of best) */
    ADAPTIVE,

    /** Score gap filtering (stops when gap between consecutive results is too large) */
    SCORE_GAP,

    /** MMR - Maximal Marginal Relevance (balances relevance and diversity) */
    MMR,

    /** Multi-criteria filtering (combines similarity, length, chunk type) */
    MULTI_CRITERIA,

    /** Ollama contextual embeddings - uses query-aware embeddings for more accurate similarity */
    OLLAMA_CONTEXTUAL_EMBEDDINGS
}
