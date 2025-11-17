package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.mcp.GithubAnalysisNodes
import ru.andvl.chatter.koog.embeddings.model.EmbeddingConfig
import ru.andvl.chatter.koog.embeddings.rag.RAGService
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.InitialPromptAnalysisModel
import ru.andvl.chatter.koog.model.tool.ToolChatResponse
import java.nio.file.Files
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("rag-indexing-subgraph")
internal val ragServiceKey = createStorageKey<RAGService>("rag-service")
private val ragInitializedKey = createStorageKey<Boolean>("rag-initialized")
internal val embeddingConfigKey = createStorageKey<EmbeddingConfig>("embedding-config")

/**
 * Subgraph for RAG indexing
 * Executes after initial request analysis, before github analysis
 * Clones repository and indexes it for semantic search
 */
internal fun AIAgentGraphStrategyBuilder<GithubChatRequest, ToolChatResponse>.subgraphRAGIndexing(
    config: EmbeddingConfig,
    storageRoot: Path
): AIAgentSubgraphDelegate<InitialPromptAnalysisModel.SuccessAnalysisModel, InitialPromptAnalysisModel.SuccessAnalysisModel> =
    subgraph(GithubAnalysisNodes.Subgraphs.RAG_INDEXING) {
        val nodeInitializeRAG by nodeInitializeRAG(config, storageRoot)
        val nodeCloneAndIndex by nodeCloneAndIndex()

        // Always try to initialize RAG
        edge(nodeStart forwardTo nodeInitializeRAG)

        // If RAG available and enabled, clone and index
        edge(nodeInitializeRAG forwardTo nodeCloneAndIndex onCondition { initialAnalysis ->
            storage.get(ragInitializedKey) == true
        })

        // If RAG not available, skip indexing and go directly to finish
        edge(nodeInitializeRAG forwardTo nodeFinish onCondition { initialAnalysis ->
            storage.get(ragInitializedKey) != true
        })

        edge(nodeCloneAndIndex forwardTo nodeFinish)
    }

private fun AIAgentSubgraphBuilderBase<InitialPromptAnalysisModel.SuccessAnalysisModel, InitialPromptAnalysisModel.SuccessAnalysisModel>.nodeInitializeRAG(
    config: EmbeddingConfig,
    storageRoot: Path
) = node<InitialPromptAnalysisModel.SuccessAnalysisModel, InitialPromptAnalysisModel.SuccessAnalysisModel>(GithubAnalysisNodes.RAGIndexing.INITIALIZE_RAG) { initialAnalysis ->
    // Store config for later use in GitHub analysis
    storage.set(embeddingConfigKey, config)

    if (!config.enabled) {
        logger.info("📊 RAG disabled in configuration")
        storage.set(ragInitializedKey, false)
        return@node initialAnalysis
    }

    val ragService = RAGService(storageRoot, config)
    val initialized = ragService.initialize()

    if (initialized) {
        logger.info("✅ RAG service initialized successfully (retrieval: top ${config.retrievalChunks} chunks, threshold: ${config.similarityThreshold})")
        storage.set(ragServiceKey, ragService)
        storage.set(ragInitializedKey, true)
    } else {
        logger.warn("⚠️ RAG service initialization failed")
        storage.set(ragInitializedKey, false)
    }

    initialAnalysis
}

private fun AIAgentSubgraphBuilderBase<InitialPromptAnalysisModel.SuccessAnalysisModel, InitialPromptAnalysisModel.SuccessAnalysisModel>.nodeCloneAndIndex() =
    node<InitialPromptAnalysisModel.SuccessAnalysisModel, InitialPromptAnalysisModel.SuccessAnalysisModel>(GithubAnalysisNodes.RAGIndexing.CLONE_AND_INDEX) { initialAnalysis ->
        val ragService = storage.get(ragServiceKey)

        if (ragService == null) {
            logger.warn("RAG service not available")
            return@node initialAnalysis
        }

        try {
            // Extract repository URL and name
            val repositoryUrl = initialAnalysis.githubRepo
            val repositoryName = repositoryUrl.substringAfterLast("/").removeSuffix(".git")

            logger.info("📊 RAG Indexing - URL: '$repositoryUrl', extracted name: '$repositoryName'")

            // Check if already indexed
            val alreadyIndexed = ragService.isRepositoryIndexed(repositoryName)
            if (alreadyIndexed) {
                logger.info("📚 Repository already indexed, using existing index")
                return@node initialAnalysis
            }

            // Clone repository to temp directory
            logger.info("📥 Cloning repository: $repositoryUrl")
            val tempDir = Files.createTempDirectory("rag-indexing-")

            try {
                val cloneResult = ProcessBuilder("git", "clone", "--depth", "1", repositoryUrl, tempDir.toString())
                    .redirectErrorStream(true)
                    .start()

                cloneResult.waitFor()

                if (cloneResult.exitValue() != 0) {
                    logger.error("Failed to clone repository")
                    return@node initialAnalysis
                }

                // Index repository
                logger.info("📊 Indexing repository for RAG...")
                val indexingResult = ragService.indexRepository(tempDir, repositoryName)

                if (indexingResult.success) {
                    logger.info("✅ ${indexingResult.message}")
                } else {
                    logger.warn("⚠️ Indexing failed: ${indexingResult.message}")
                }

            } finally {
                // Cleanup temp directory
                tempDir.toFile().deleteRecursively()
            }

            initialAnalysis

        } catch (e: Exception) {
            logger.error("Failed to clone and index repository", e)
            initialAnalysis
        }
    }
