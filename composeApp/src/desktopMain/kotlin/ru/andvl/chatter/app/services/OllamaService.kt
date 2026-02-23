package ru.andvl.chatter.app.services

import ai.koog.prompt.executor.ollama.client.OllamaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ollama-service")

object OllamaService {
    private const val DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434"

    suspend fun getAvailableModels(baseUrl: String = DEFAULT_OLLAMA_BASE_URL): List<String> =
        withContext(Dispatchers.IO) {
            try {
                logger.info("Checking Ollama availability at $baseUrl")
                val client = OllamaClient(baseUrl = baseUrl)
                val models = client.getModels()
                val modelNames = models.map { it.name }
                if (modelNames.isNotEmpty()) {
                    logger.info("Ollama available with ${modelNames.size} models")
                }
                modelNames
            } catch (e: Exception) {
                logger.debug("Ollama not available at $baseUrl: ${e.message}")
                emptyList()
            }
        }

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
