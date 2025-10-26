package ru.andvl.chatter.cli.statistics

import ru.andvl.chatter.cli.models.TokenUsageDto

/**
 * Token usage statistics for tracking total tokens across conversation
 */
data class TokenStats(
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalTokens: Int = 0,
    val requestCount: Int = 0,
    val historyClearCount: Int = 0,
    val compressionCount: Int = 0
) {
    fun addUsage(usage: TokenUsageDto): TokenStats {
        return copy(
            totalPromptTokens = totalPromptTokens + usage.promptTokens,
            totalCompletionTokens = totalCompletionTokens + usage.completionTokens,
            totalTokens = totalTokens + usage.totalTokens,
            requestCount = requestCount + 1
        )
    }

    fun onHistoryCleared(): TokenStats {
        return copy(historyClearCount = historyClearCount + 1)
    }

    fun onCompressionApplied(): TokenStats {
        return copy(compressionCount = compressionCount + 1)
    }

    fun onBackendCompressionDetected(): TokenStats {
        return copy(compressionCount = compressionCount + 1)
    }

    fun getAverageTokensPerRequest(): Double {
        return if (requestCount > 0) totalTokens.toDouble() / requestCount else 0.0
    }

    fun getAveragePromptTokens(): Double {
        return if (requestCount > 0) totalPromptTokens.toDouble() / requestCount else 0.0
    }

    fun getAverageCompletionTokens(): Double {
        return if (requestCount > 0) totalCompletionTokens.toDouble() / requestCount else 0.0
    }
}

/**
 * Token statistics manager for tracking conversation metrics
 */
class TokenStatistics {
    private var stats = TokenStats()

    /**
     * Add token usage from a request
     */
    fun addUsage(usage: TokenUsageDto) {
        stats = stats.addUsage(usage)
    }

    /**
     * Record that history was cleared
     */
    fun recordHistoryCleared() {
        stats = stats.onHistoryCleared()
    }

    /**
     * Record that backend compression was detected
     */
    fun recordBackendCompressionDetected() {
        stats = stats.onBackendCompressionDetected()
    }

    /**
     * Get current statistics
     */
    fun getStats(): TokenStats = stats

    /**
     * Reset all statistics
     */
    fun reset() {
        stats = TokenStats()
    }

    /**
     * Get summary string for display
     */
    fun getSummary(): String {
        val stats = getStats()
        return buildString {
            appendLine("📊 Session Statistics:")
            appendLine("  Requests: ${stats.requestCount}")
            appendLine("  Prompt tokens: ${stats.totalPromptTokens}")
            appendLine("  Completion tokens: ${stats.totalCompletionTokens}")
            if (stats.requestCount > 0) {
                appendLine("  Avg tokens/request: %.1f".format(stats.getAverageTokensPerRequest()))
                appendLine("  Avg prompt tokens: %.1f".format(stats.getAveragePromptTokens()))
                appendLine("  Avg completion tokens: %.1f".format(stats.getAverageCompletionTokens()))
            }
            if (stats.historyClearCount > 0) {
                appendLine("  History clears: ${stats.historyClearCount}")
            }
            if (stats.compressionCount > 0) {
                appendLine("  Backend compressions detected: ${stats.compressionCount}")
            }
        }
    }

    /**
     * Get compact summary for status display
     */
    fun getCompactSummary(): String {
        val stats = getStats()
        return if (stats.requestCount > 0) {
            "📊 Session: ${stats.requestCount} requests, ${stats.totalPromptTokens} total tokens"
        } else {
            "📊 Session: No requests yet"
        }
    }
}
