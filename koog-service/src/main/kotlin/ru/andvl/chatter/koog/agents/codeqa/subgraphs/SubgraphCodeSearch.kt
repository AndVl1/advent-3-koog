package ru.andvl.chatter.koog.agents.codeqa.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codeqa.*
import ru.andvl.chatter.koog.model.codeqa.CodeReferenceInternal
import ru.andvl.chatter.koog.model.codeqa.CodeSearchResult
import ru.andvl.chatter.koog.model.codeqa.QuestionAnalysisResult
import ru.andvl.chatter.koog.tools.CodeChunk
import ru.andvl.chatter.koog.tools.FileOperationsToolSet
import ru.andvl.chatter.koog.tools.RagToolSet
import ru.andvl.chatter.koog.tools.SearchCodeResult

private val logger = LoggerFactory.getLogger("codeqa-code-search")

/**
 * Subgraph: Code Search
 *
 * Purpose: Search for relevant code using RAG and/or GitHub MCP tools
 *
 * Flow:
 * 1. Search with RAG (if available)
 * 2. Search with file operations (fallback or complement)
 * 3. Deduplicate results
 * 4. Fetch full file context for top results
 *
 * Input: QuestionAnalysisResult
 * Output: CodeSearchResult (contains code references with metadata)
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphCodeSearch():
        AIAgentSubgraphDelegate<QuestionAnalysisResult, CodeSearchResult> =
    subgraph(
        name = "code-search",
        tools = ToolRegistry {
            tools(RagToolSet())
            tools(FileOperationsToolSet())
        }.tools
    ) {
        val nodeSearchWithRag by nodeSearchWithRag()
        val nodeSearchWithFileOps by nodeSearchWithFileOps()
        val nodeDeduplicateResults by nodeDeduplicateResults()
        val nodeFetchFullContext by nodeFetchFullContext()

        edge(nodeStart forwardTo nodeSearchWithRag)
        edge(nodeSearchWithRag forwardTo nodeSearchWithFileOps)
        edge(nodeSearchWithFileOps forwardTo nodeDeduplicateResults)
        edge(nodeDeduplicateResults forwardTo nodeFetchFullContext)
        edge(nodeFetchFullContext forwardTo nodeFinish)
    }

/**
 * Node: Search with RAG
 *
 * Uses RAG semantic search if available to find relevant code chunks.
 */
private fun AIAgentSubgraphBuilderBase<QuestionAnalysisResult, CodeSearchResult>.nodeSearchWithRag() =
    node<QuestionAnalysisResult, List<CodeReferenceInternal>>("search-with-rag") { analysisResult ->
        val ragAvailable = storage.get(ragAvailableKey) ?: false
        val searchQuery = analysisResult.searchQuery

        if (!ragAvailable) {
            logger.info("RAG not available, skipping semantic search")
            return@node emptyList()
        }

        logger.info("Performing RAG semantic search with query: $searchQuery")

        // RAG search would happen through tools, but since we're in a non-LLM node,
        // we need to call the tool directly. However, this is a simplified implementation.
        // In production, we'd use the RAG service directly or let an LLM node use the tool.

        // For now, return empty and let file operations handle the search
        // This will be improved when we have proper RAG integration
        logger.info("RAG search placeholder - returning empty results")
        emptyList<CodeReferenceInternal>()
    }

/**
 * Node: Search with File Operations
 *
 * Uses file operations to search for code patterns.
 * This is the fallback when RAG is not available or complement to RAG results.
 */
private fun AIAgentSubgraphBuilderBase<QuestionAnalysisResult, CodeSearchResult>.nodeSearchWithFileOps() =
    node<List<CodeReferenceInternal>, List<CodeReferenceInternal>>("search-with-file-ops") { ragResults ->
        val analysisResult = storage.get(questionAnalysisResultKey)!!
        val repositoryPath = storage.get(repositoryPathKey)!!
        val keywords = analysisResult.keywords

        logger.info("Performing file-based search in: $repositoryPath")
        logger.info("Keywords: $keywords")

        val fileSearchResults = mutableListOf<CodeReferenceInternal>()

        // Use FileOperationsToolSet to search for keywords with repository path access
        val fileOps = FileOperationsToolSet(allowedBasePath = repositoryPath)

        // Search for each keyword
        keywords.take(5).forEach { keyword ->
            try {
                logger.info("Searching for keyword: $keyword")

                // Build regex pattern - escape special characters
                val pattern = Regex.escape(keyword)

                val searchResult = fileOps.searchInFiles(
                    directoryPath = repositoryPath,
                    pattern = pattern,
                    filePatterns = listOf("*.kt", "*.kts", "*.java", "*.py", "*.js", "*.ts", "*.go", "*.rs"),
                    excludePatterns = listOf(".git", "build", "node_modules", ".gradle"),
                    contextLines = 3,
                    caseSensitive = false
                )

                if (searchResult.success) {
                    logger.info("Found ${searchResult.totalMatches} matches for '$keyword'")

                    // Convert SearchMatch to CodeReferenceInternal
                    searchResult.matches.take(20).forEach { match ->
                        val snippet = buildString {
                            match.contextBefore.forEach { appendLine(it) }
                            appendLine(match.lineContent)
                            match.contextAfter.forEach { appendLine(it) }
                        }

                        fileSearchResults.add(
                            CodeReferenceInternal(
                                filePath = match.filePath.removePrefix(repositoryPath).removePrefix("/"),
                                lineStart = match.lineNumber - match.contextBefore.size,
                                lineEnd = match.lineNumber + match.contextAfter.size,
                                codeSnippet = snippet.trim(),
                                relevanceScore = 0.7f, // Base score for keyword match
                                source = "FileOperations"
                            )
                        )
                    }
                } else {
                    logger.warn("Search failed for keyword '$keyword': ${searchResult.message}")
                }
            } catch (e: Exception) {
                logger.error("Error searching for keyword '$keyword'", e)
            }
        }

        logger.info("File operations found ${fileSearchResults.size} results")

        // Combine RAG results with file search results
        val allResults = ragResults.toMutableList()
        allResults.addAll(fileSearchResults)

        logger.info("Total search results: ${allResults.size}")

        allResults
    }

/**
 * Node: Deduplicate Results
 *
 * Remove duplicate code references based on file path and line ranges.
 */
private fun AIAgentSubgraphBuilderBase<QuestionAnalysisResult, CodeSearchResult>.nodeDeduplicateResults() =
    node<List<CodeReferenceInternal>, List<CodeReferenceInternal>>("deduplicate-results") { results ->
        logger.info("Deduplicating ${results.size} search results")

        // Deduplicate based on file path and overlapping line ranges
        val deduplicated = results.distinctBy { ref ->
            Triple(ref.filePath, ref.lineStart, ref.lineEnd)
        }

        logger.info("After deduplication: ${deduplicated.size} unique results")

        deduplicated
    }

/**
 * Node: Fetch Full File Context
 *
 * For each code reference, fetch additional context (surrounding code).
 * Limit to top N results to avoid overwhelming the LLM.
 */
private fun AIAgentSubgraphBuilderBase<QuestionAnalysisResult, CodeSearchResult>.nodeFetchFullContext() =
    node<List<CodeReferenceInternal>, CodeSearchResult>("fetch-full-file-context") { deduplicatedResults ->
        val maxResults = 10
        val topResults = deduplicatedResults
            .sortedByDescending { it.relevanceScore }
            .take(maxResults)

        logger.info("Fetching full context for top $maxResults results")

        // In production, we'd use read-file-content tool to get surrounding context
        // For now, keep the existing references
        val resultsWithContext = topResults

        logger.info("Code search completed successfully")

        val searchMethod = when {
            storage.get(ragAvailableKey) == true -> "RAG"
            else -> "File Operations"
        }

        val searchResult = CodeSearchResult(
            references = resultsWithContext,
            totalFound = resultsWithContext.size,
            ragResultsCount = 0, // Would be updated with actual RAG results
            mcpResultsCount = 0, // Would be updated with actual MCP results
            searchMethod = searchMethod,
            searchSuccessful = resultsWithContext.isNotEmpty()
        )

        storage.set(codeSearchResultKey, searchResult)
        storage.set(codeReferencesInternalKey, resultsWithContext)

        searchResult
    }

/**
 * Helper: Convert RAG CodeChunk to CodeReferenceInternal
 */
private fun CodeChunk.toCodeReference(): CodeReferenceInternal =
    CodeReferenceInternal(
        filePath = filePath,
        lineStart = startLine,
        lineEnd = endLine,
        codeSnippet = content,
        relevanceScore = 1.0f, // Would be calculated from similarity score
        source = "RAG"
    )

/**
 * Helper: Convert SearchCodeResult to list of CodeReferenceInternal
 */
private fun SearchCodeResult.toCodeReferences(): List<CodeReferenceInternal> =
    chunks.map { it.toCodeReference() }
