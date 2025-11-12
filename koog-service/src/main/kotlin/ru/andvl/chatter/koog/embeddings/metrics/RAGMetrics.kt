package ru.andvl.chatter.koog.embeddings.metrics

import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.embeddings.model.DocumentChunk
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private val logger = LoggerFactory.getLogger("rag-metrics")

/**
 * Metrics for monitoring RAG effectiveness
 */
internal object RAGMetrics {
    // Retrieval metrics
    private val totalSearches = AtomicInteger(0)
    private val successfulSearches = AtomicInteger(0)
    private val totalChunksRequested = AtomicLong(0)
    private val totalChunksReturned = AtomicLong(0)
    private val totalChunksFiltered = AtomicLong(0) // Filtered by repository

    // Quality metrics
    private val uniqueFilesRetrieved = mutableSetOf<String>()
    private val uniqueRepositoriesSearched = mutableSetOf<String>()

    // Usage metrics
    private val ragAvailableCount = AtomicInteger(0)
    private val ragUnavailableCount = AtomicInteger(0)
    private val mcpToolCallsWithRAG = AtomicInteger(0)
    private val mcpToolCallsWithoutRAG = AtomicInteger(0)

    /**
     * Record a search operation
     */
    fun recordSearch(
        repositoryName: String,
        requested: Int,
        returned: List<DocumentChunk>,
        filteredFrom: Int
    ) {
        totalSearches.incrementAndGet()
        if (returned.isNotEmpty()) {
            successfulSearches.incrementAndGet()
        }

        totalChunksRequested.addAndGet(requested.toLong())
        totalChunksReturned.addAndGet(returned.size.toLong())
        totalChunksFiltered.addAndGet((filteredFrom - returned.size).toLong())

        uniqueRepositoriesSearched.add(repositoryName)
        returned.forEach { chunk ->
            uniqueFilesRetrieved.add(chunk.metadata.filePath)
        }

        // Log if retrieval was poor
        if (returned.size < requested * 0.5) {
            logger.warn("⚠️ Low retrieval rate: got ${returned.size} chunks out of $requested requested (filtered from $filteredFrom)")
        }
    }

    /**
     * Record RAG availability
     */
    fun recordRAGAvailability(available: Boolean) {
        if (available) {
            ragAvailableCount.incrementAndGet()
        } else {
            ragUnavailableCount.incrementAndGet()
        }
    }

    /**
     * Record MCP tool calls
     */
    fun recordMCPToolCalls(count: Int, ragWasAvailable: Boolean) {
        if (ragWasAvailable) {
            mcpToolCallsWithRAG.addAndGet(count)
        } else {
            mcpToolCallsWithoutRAG.addAndGet(count)
        }
    }

    /**
     * Get current metrics summary
     */
    fun getSummary(): RAGMetricsSummary {
        val searches = totalSearches.get()
        val successful = successfulSearches.get()
        val requested = totalChunksRequested.get()
        val returned = totalChunksReturned.get()
        val filtered = totalChunksFiltered.get()
        val mcpWithRAG = mcpToolCallsWithRAG.get()
        val mcpWithoutRAG = mcpToolCallsWithoutRAG.get()

        return RAGMetricsSummary(
            totalSearches = searches,
            successfulSearches = successful,
            successRate = if (searches > 0) successful.toDouble() / searches else 0.0,
            avgChunksPerSearch = if (searches > 0) returned.toDouble() / searches else 0.0,
            totalChunksRequested = requested,
            totalChunksReturned = returned,
            retrievalEfficiency = if (requested > 0) returned.toDouble() / requested else 0.0,
            totalChunksFilteredOut = filtered,
            filterRate = if (filtered + returned > 0) filtered.toDouble() / (filtered + returned) else 0.0,
            uniqueFilesRetrieved = uniqueFilesRetrieved.size,
            uniqueRepositoriesSearched = uniqueRepositoriesSearched.size,
            ragAvailableCount = ragAvailableCount.get(),
            ragUnavailableCount = ragUnavailableCount.get(),
            mcpToolCallsWithRAG = mcpWithRAG,
            mcpToolCallsWithoutRAG = mcpWithoutRAG,
            mcpToolCallReduction = if (mcpWithoutRAG > 0)
                1.0 - (mcpWithRAG.toDouble() / mcpWithoutRAG)
            else 0.0
        )
    }

    /**
     * Log metrics summary
     */
    fun logSummary() {
        val summary = getSummary()

        val mcpReductionText = if (summary.mcpToolCallsWithoutRAG > 0) {
            val reduction = summary.mcpToolCallReduction * 100
            if (reduction > 0) {
                "${String.format("%.1f%%", reduction)} reduction"
            } else if (reduction < 0) {
                "${String.format("%.1f%%", -reduction)} increase"
            } else {
                "no change"
            }
        } else {
            "N/A"
        }

        logger.info("""
            |
            |📊 RAG Metrics Summary:
            |  Searches: ${summary.totalSearches} (${summary.successfulSearches} successful, ${String.format("%.1f%%", summary.successRate * 100)})
            |  Chunks: ${summary.totalChunksReturned} returned / ${summary.totalChunksRequested} requested (${String.format("%.1f%%", summary.retrievalEfficiency * 100)} efficiency)
            |  Filtering: ${summary.totalChunksFilteredOut} chunks filtered out (${String.format("%.1f%%", summary.filterRate * 100)} filter rate)
            |  Coverage: ${summary.uniqueFilesRetrieved} unique files from ${summary.uniqueRepositoriesSearched} repositories
            |  Availability: ${summary.ragAvailableCount} available / ${summary.ragUnavailableCount} unavailable
            |  Avg chunks/search: ${String.format("%.1f", summary.avgChunksPerSearch)}
            |  MCP tool calls: ${summary.mcpToolCallsWithRAG} with RAG / ${summary.mcpToolCallsWithoutRAG} without RAG ($mcpReductionText)
            |
        """.trimMargin())
    }

    /**
     * Reset metrics (useful for testing)
     */
    fun reset() {
        totalSearches.set(0)
        successfulSearches.set(0)
        totalChunksRequested.set(0)
        totalChunksReturned.set(0)
        totalChunksFiltered.set(0)
        ragAvailableCount.set(0)
        ragUnavailableCount.set(0)
        mcpToolCallsWithRAG.set(0)
        mcpToolCallsWithoutRAG.set(0)
        uniqueFilesRetrieved.clear()
        uniqueRepositoriesSearched.clear()
    }
}

/**
 * Summary of RAG metrics
 */
internal data class RAGMetricsSummary(
    val totalSearches: Int,
    val successfulSearches: Int,
    val successRate: Double,
    val avgChunksPerSearch: Double,
    val totalChunksRequested: Long,
    val totalChunksReturned: Long,
    val retrievalEfficiency: Double,
    val totalChunksFilteredOut: Long,
    val filterRate: Double,
    val uniqueFilesRetrieved: Int,
    val uniqueRepositoriesSearched: Int,
    val ragAvailableCount: Int,
    val ragUnavailableCount: Int,
    val mcpToolCallsWithRAG: Int,
    val mcpToolCallsWithoutRAG: Int,
    val mcpToolCallReduction: Double
)
