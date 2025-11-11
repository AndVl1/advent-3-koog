package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.embeddings.chunking.ChunkingStrategyFactory
import ru.andvl.chatter.koog.embeddings.model.EmbeddingConfig
import ru.andvl.chatter.koog.embeddings.model.EmbeddingEntry
import ru.andvl.chatter.koog.embeddings.model.EmbeddingIndex
import ru.andvl.chatter.koog.embeddings.service.EmbeddingService
import ru.andvl.chatter.koog.embeddings.storage.FileBasedIndexStorage
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.ToolChatResponse
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText

private val embeddingInfoKey = createStorageKey<EmbeddingProcessingInfo>("embedding-info")
private val logger = LoggerFactory.getLogger("embeddings-subgraph")

/**
 * Information about embedding processing
 */
internal data class EmbeddingProcessingInfo(
    val enabled: Boolean,
    val ollamaAvailable: Boolean,
    val chunksProcessed: Int = 0,
    val embeddingsGenerated: Int = 0,
    val filesProcessed: Int = 0,
    val indexSaved: Boolean = false,
    val error: String? = null
)

internal fun AIAgentGraphStrategyBuilder<GithubChatRequest, ToolChatResponse>.subgraphEmbeddings(
    config: EmbeddingConfig,
    storageDir: File
): AIAgentSubgraphDelegate<ToolChatResponse, ToolChatResponse> =
    subgraph("embeddings-pipeline") {
        val nodeCheckAvailability by nodeCheckAvailability(config)
        val nodeProcessDocuments by nodeProcessDocuments(config, storageDir)
        val nodeFinalizeResponse by nodeFinalizeResponse()

        // Always go through nodeCheckAvailability to save original response
        edge(nodeStart forwardTo nodeCheckAvailability)

        // Process documents if enabled AND Ollama is available
        edge(nodeCheckAvailability forwardTo nodeProcessDocuments onCondition { info ->
            config.enabled && info.ollamaAvailable
        })

        // Skip processing if disabled or Ollama not available
        edge(nodeCheckAvailability forwardTo nodeFinish onCondition { info ->
            !config.enabled || !info.ollamaAvailable
        } transformed { _ ->
            val existingResponse = storage.get(
                createStorageKey<ToolChatResponse>("original-response")
            ) ?: return@transformed ToolChatResponse(
                response = "Error: Missing original response",
                shortSummary = "Error",
                toolCalls = emptyList(),
                originalMessage = null,
                tokenUsage = null,
                repositoryReview = null,
                requirements = null,
                userRequestAnalysis = null,
                dockerInfo = null
            )

            if (!config.enabled) {
                logger.info("📊 Embeddings disabled in configuration, skipping")
                existingResponse
            } else {
                logger.warn("📊 Ollama not available, skipping embeddings generation")
                existingResponse.copy(
                    response = existingResponse.response + "\n\n⚠️ **Embeddings skipped**: Ollama not available. Install model with: `ollama pull ${config.modelName}`"
                )
            }
        })

        edge(nodeProcessDocuments forwardTo nodeFinalizeResponse)
        edge(nodeFinalizeResponse forwardTo nodeFinish)
    }

private fun AIAgentSubgraphBuilderBase<ToolChatResponse, ToolChatResponse>.nodeCheckAvailability(
    config: EmbeddingConfig
) = node<ToolChatResponse, EmbeddingProcessingInfo>("check-ollama") { response ->
    // Store original response for later use
    storage.set(createStorageKey<ToolChatResponse>("original-response"), response)

    val embeddingService = EmbeddingService(config)
    val ollamaAvailable = embeddingService.checkAvailability()

    val info = EmbeddingProcessingInfo(
        enabled = config.enabled,
        ollamaAvailable = ollamaAvailable
    )

    storage.set(embeddingInfoKey, info)
    embeddingService.close()

    if (ollamaAvailable) {
        logger.info("✅ Ollama is available with model ${config.modelName}")
    } else {
        logger.warn("⚠️ Ollama not available at ${config.ollamaBaseUrl}")
    }

    info
}

private fun AIAgentSubgraphBuilderBase<ToolChatResponse, ToolChatResponse>.nodeProcessDocuments(
    config: EmbeddingConfig,
    storageDir: File
) = node<EmbeddingProcessingInfo, ToolChatResponse>("process-documents") { info ->
    val originalResponse = storage.get(
        createStorageKey<ToolChatResponse>("original-response")
    ) ?: return@node ToolChatResponse(
        response = "Error: Missing original response in embeddings processing",
        shortSummary = "Error",
        toolCalls = emptyList(),
        originalMessage = null,
        tokenUsage = null,
        repositoryReview = null,
        requirements = null,
        userRequestAnalysis = null,
        dockerInfo = null
    )

    try {
        logger.info("📊 Starting embeddings generation for repository")

        // Get repository URL from the original response
        val repositoryUrl = extractRepositoryUrl(originalResponse.response)
        val repositoryName = repositoryUrl.substringAfterLast("/").removeSuffix(".git")

        // Create temporary directory for repository clone
        val tempDir = Files.createTempDirectory("github-embeddings-").toFile()

        try {
            // Clone repository
            logger.info("📥 Cloning repository: $repositoryUrl")
            val cloneResult = ProcessBuilder("git", "clone", "--depth", "1", repositoryUrl, tempDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            cloneResult.waitFor()

            if (cloneResult.exitValue() != 0) {
                throw Exception("Failed to clone repository")
            }

            // Find eligible files
            val eligibleFiles = findEligibleFiles(tempDir.toPath(), config)
            logger.info("📄 Found ${eligibleFiles.size} eligible files for processing")

            if (eligibleFiles.isEmpty()) {
                logger.warn("No eligible files found for embedding")
                return@node originalResponse.copy(
                    response = originalResponse.response + "\n\n⚠️ **Embeddings**: No eligible files found for processing"
                )
            }

            // Initialize services
            val embeddingService = EmbeddingService(config)
            embeddingService.checkAvailability() // Ensure it's initialized

            val indexStorage = FileBasedIndexStorage(storageDir)

            // Process files
            val embeddingEntries = mutableListOf<EmbeddingEntry>()
            var filesProcessed = 0
            var chunksProcessed = 0

            for (file in eligibleFiles.take(config.maxChunks / 10)) { // Limit files to avoid overwhelming
                try {
                    val content = file.readText()
                    val relativeFilePath = tempDir.toPath().relativize(file).pathString

                    logger.debug("Processing file: $relativeFilePath")

                    // Chunk the file
                    val strategy = ChunkingStrategyFactory.getStrategy(relativeFilePath)
                    val chunks = strategy.chunk(
                        filePath = relativeFilePath,
                        content = content,
                        repository = repositoryName,
                        config = config
                    )

                    logger.debug("Created ${chunks.size} chunks for $relativeFilePath")

                    // Generate embeddings for each chunk
                    for (chunk in chunks) {
                        val embedding = embeddingService.embed(chunk.content)
                        if (embedding != null) {
                            val norm = embeddingService.calculateNorm(embedding)
                            embeddingEntries.add(
                                EmbeddingEntry(
                                    chunk = chunk,
                                    embedding = embedding,
                                    norm = norm
                                )
                            )
                            chunksProcessed++
                        }
                    }

                    filesProcessed++
                } catch (e: Exception) {
                    logger.error("Failed to process file ${file.name}: ${e.message}")
                }
            }

            // Save index
            if (embeddingEntries.isNotEmpty()) {
                val embeddingIndex = EmbeddingIndex(
                    repository = repositoryName,
                    createdAt = System.currentTimeMillis(),
                    modelName = config.modelName,
                    entries = embeddingEntries
                )

                indexStorage.save(embeddingIndex)
                logger.info("💾 Saved embedding index with ${embeddingEntries.size} entries")

                // Update info
                val updatedInfo = info.copy(
                    filesProcessed = filesProcessed,
                    chunksProcessed = chunksProcessed,
                    embeddingsGenerated = embeddingEntries.size,
                    indexSaved = true
                )
                storage.set(embeddingInfoKey, updatedInfo)
            }

            embeddingService.close()

        } finally {
            // Cleanup temp directory
            tempDir.deleteRecursively()
        }

        originalResponse

    } catch (e: Exception) {
        logger.error("Failed to process embeddings: ${e.message}", e)

        val updatedInfo = info.copy(
            error = e.message
        )
        storage.set(embeddingInfoKey, updatedInfo)

        originalResponse.copy(
            response = originalResponse.response + "\n\n⚠️ **Embeddings Error**: ${e.message}"
        )
    }
}

private fun AIAgentSubgraphBuilderBase<ToolChatResponse, ToolChatResponse>.nodeFinalizeResponse() =
    node<ToolChatResponse, ToolChatResponse>("finalize-embeddings") { response ->
        val info = storage.get(embeddingInfoKey)!!

        if (info.indexSaved) {
            val embeddingsSummary = buildString {
                appendLine()
                appendLine("## 📊 Embeddings Index")
                appendLine()
                appendLine("**Status:** ✅ Successfully generated")
                appendLine("**Files Processed:** ${info.filesProcessed}")
                appendLine("**Chunks Created:** ${info.chunksProcessed}")
                appendLine("**Embeddings Generated:** ${info.embeddingsGenerated}")
                appendLine("**Model:** `${info.ollamaAvailable}`")
                appendLine()
                appendLine("The repository has been indexed and is ready for semantic search.")
            }

            response.copy(
                response = response.response + embeddingsSummary
            )
        } else {
            response
        }
    }

private fun findEligibleFiles(rootPath: Path, config: EmbeddingConfig): List<Path> {
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

private fun extractRepositoryUrl(text: String): String {
    val pattern = "https://github\\.com/[\\w.-]+/[\\w.-]+".toRegex()
    return pattern.find(text)?.value ?: throw IllegalArgumentException("No GitHub repository URL found in response")
}
