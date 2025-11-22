package ru.andvl.chatter.desktop.services

import ai.koog.prompt.executor.ollama.client.OllamaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ollama-service")

/**
 * Service for checking Ollama availability and fetching available models
 */
object OllamaService {
    private const val DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434"

    /**
     * Check if Ollama is available and get list of available models
     *
     * @param baseUrl Ollama base URL (default: http://localhost:11434)
     * @return List of available model names, or empty list if Ollama is not available
     */
    suspend fun getAvailableModels(baseUrl: String = DEFAULT_OLLAMA_BASE_URL): List<String> =
        withContext(Dispatchers.IO) {
            try {
                logger.info("Checking Ollama availability at $baseUrl")
                val client = OllamaClient(baseUrl = baseUrl)

                // Get list of models from Ollama
                val models = client.getModels()
                val modelNames = models.map { it.name }

                if (modelNames.isNotEmpty()) {
                    logger.info("✅ Ollama available with ${modelNames.size} models: ${modelNames.joinToString(", ")}")
                } else {
                    logger.info("⚠️ Ollama is running but no models are available")
                }

                modelNames
            } catch (e: Exception) {
                logger.debug("❌ Ollama not available at $baseUrl: ${e.message}")
                emptyList()
            }
        }

    /**
     * Check if Ollama is available (without fetching models)
     */
    suspend fun isAvailable(baseUrl: String = DEFAULT_OLLAMA_BASE_URL): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val client = OllamaClient(baseUrl = baseUrl)
                client.getModels()
                true
            } catch (e: Exception) {
                false
            }
        }
}
