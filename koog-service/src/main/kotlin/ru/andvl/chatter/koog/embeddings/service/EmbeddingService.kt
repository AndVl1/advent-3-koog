package ru.andvl.chatter.koog.embeddings.service

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.embeddings.local.OllamaEmbeddingModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.embeddings.model.EmbeddingConfig

private val logger = LoggerFactory.getLogger("embeddings-service")

/**
 * Service for generating embeddings using Ollama
 * Thread-safe implementation using double-checked locking
 */
internal class EmbeddingService(
    private val config: EmbeddingConfig
) {
    @Volatile
    private var embedder: Embedder? = null

    @Volatile
    private var isAvailable: Boolean = false

    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
        }
    }

    private val initMutex = Mutex()

    /**
     * Check if Ollama is available and the model is installed
     * Thread-safe initialization using double-checked locking with Mutex
     */
    suspend fun checkAvailability(): Boolean {
        if (!config.enabled) {
            logger.info("Embeddings disabled in configuration")
            return false
        }

        // First check (no locking) - fast path
        if (isAvailable && embedder != null) {
            return true
        }

        // Double-checked locking with Mutex for coroutine safety
        initMutex.withLock {
            // Second check (with lock) - ensure initialization happens only once
            if (isAvailable && embedder != null) {
                return true
            }

            return try {
                logger.info("Checking Ollama availability at ${config.ollamaBaseUrl}")

                // Check if Ollama is running
                val response = httpClient.get("${config.ollamaBaseUrl}/api/tags")
                if (!response.status.isSuccess()) {
                    logger.warn("Ollama not responding at ${config.ollamaBaseUrl}")
                    isAvailable = false
                    return false
                }

                // Check if model is available
                val responseText = response.bodyAsText()
                val json = Json { ignoreUnknownKeys = true }
                val ollamaModels = json.decodeFromString<OllamaModelsResponse>(responseText)

                val modelAvailable = ollamaModels.models.any {
                    it.name.contains(config.modelName, ignoreCase = true)
                }

                if (!modelAvailable) {
                    logger.warn("Model ${config.modelName} not found in Ollama. Available models: ${ollamaModels.models.map { it.name }}")
                    logger.info("To install the model, run: ollama pull ${config.modelName}")
                    isAvailable = false
                    return false
                }

                logger.info("Ollama is available with model ${config.modelName}")

                // Initialize embedder
                val ollamaClient = OllamaClient(baseUrl = config.ollamaBaseUrl)
                OllamaEmbeddingModels.MULTILINGUAL_E5
                val llmModel = LLModel(
                    provider = LLMProvider.Ollama,
                    id = config.modelName,
                    capabilities = listOf(LLMCapability.Embed),
                    contextLength = 512 // Default context length for embedding models
                )

                // Atomic assignment to volatile field
                val newEmbedder = LLMEmbedder(
                    client = ollamaClient,
                    model = llmModel
                )

                embedder = newEmbedder
                isAvailable = true
                true
            } catch (e: Exception) {
                logger.warn("Failed to connect to Ollama at ${config.ollamaBaseUrl}: ${e.message}")
                logger.debug("Full error details", e)
                isAvailable = false
                false
            }
        }
    }

    /**
     * Generate embedding for text
     * Thread-safe read using local copy
     */
    suspend fun embed(text: String): List<Double>? {
        // Read volatile variable once to avoid race conditions
        val currentEmbedder = embedder
        if (!isAvailable || currentEmbedder == null) {
            logger.debug("Embedder not available, skipping embedding generation")
            return null
        }

        return try {
            val vector = currentEmbedder.embed(text)
            vector.values.toList()
        } catch (e: Exception) {
            logger.error("Failed to generate embedding for text: ${text.take(100)}...", e)
            null
        }
    }

    /**
     * Generate embeddings for multiple texts with batching
     * Thread-safe read using local copy
     */
    suspend fun embedBatch(texts: List<String>, batchSize: Int = 10): List<List<Double>?> {
        // Read volatile variable once to avoid race conditions
        val currentEmbedder = embedder
        if (!isAvailable || currentEmbedder == null) {
            logger.debug("Embedder not available, skipping batch embedding generation")
            return texts.map { null }
        }

        val embeddings = mutableListOf<List<Double>?>()

        texts.chunked(batchSize).forEach { batch ->
            batch.forEach { text ->
                try {
                    val embedding = embed(text)
                    embeddings.add(embedding)
                    delay(100) // Small delay to avoid overwhelming Ollama
                } catch (e: Exception) {
                    logger.error("Failed to generate embedding in batch", e)
                    embeddings.add(null)
                }
            }
        }

        return embeddings
    }

    /**
     * Calculate cosine similarity between two embeddings
     */
    fun cosineSimilarity(embedding1: List<Double>, embedding2: List<Double>): Double {
        require(embedding1.size == embedding2.size) { "Embeddings must have the same size" }

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        return dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
    }

    /**
     * Calculate L2 norm of embedding vector
     */
    fun calculateNorm(embedding: List<Double>): Double {
        return kotlin.math.sqrt(embedding.sumOf { it * it })
    }

    fun close() {
        httpClient.close()
    }
}

/**
 * Ollama API response models
 * Note: ignoreUnknownKeys is used to handle additional fields in the API response
 */
@Serializable
private data class OllamaModelsResponse(
    val models: List<OllamaModel>
)

@Serializable
private data class OllamaModel(
    val name: String
    // Ollama API returns additional fields like 'model', 'modified_at', 'size', etc.
    // We only need 'name', so ignoreUnknownKeys is configured in Json parser
)
