package ru.andvl.chatter.koog.agents.codemodifier.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.prompt.llm.LLModel
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codemodifier.*
import ru.andvl.chatter.koog.model.codemodifier.*
import ru.andvl.chatter.koog.tools.FileOperationsToolSet
import ru.andvl.chatter.koog.tools.RagToolSet
import java.io.File

private val logger = LoggerFactory.getLogger("codemodifier-code-analysis")

/**
 * Subgraph: Code Analysis
 *
 * Flow:
 * 1. Search relevant files (LLM + Tools) - Use RAG and file search to find relevant files
 * 2. Analyze files (LLM + Tools) - Read and parse files, extract context
 * 3. Detect patterns (Text-only LLM) - Detect coding style and patterns
 *
 * Input: ValidationResult
 * Output: CodeAnalysisResult
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphCodeAnalysis(
    model: LLModel
): AIAgentSubgraphDelegate<ValidationResult, CodeAnalysisResult> =
    subgraph(
        name = "code-analysis",
        tools = ToolRegistry {
            tools(FileOperationsToolSet())
            tools(RagToolSet())
        }.tools
    ) {
        val nodeSearchRelevantFiles by nodeSearchRelevantFiles(model)
        val nodeAnalyzeFiles by nodeAnalyzeFiles(model)
        val nodeDetectPatterns by nodeDetectPatterns(model)

        edge(nodeStart forwardTo nodeSearchRelevantFiles)
        edge(nodeSearchRelevantFiles forwardTo nodeAnalyzeFiles)
        edge(nodeAnalyzeFiles forwardTo nodeDetectPatterns)
        edge(nodeDetectPatterns forwardTo nodeFinish)
    }

/**
 * Node: Search relevant files (uses file scope directly, no LLM needed)
 *
 * Uses file scope from validation result to identify relevant files.
 */
private fun AIAgentSubgraphBuilderBase<ValidationResult, CodeAnalysisResult>.nodeSearchRelevantFiles(
    model: LLModel
) =
    node<ValidationResult, List<String>>("search-relevant-files") { validationResult ->
        logger.info("Searching for relevant files")

        val sessionPath = validationResult.sessionPath!!
        val fileScope = validationResult.normalizedFileScope
        val instructions = storage.get(instructionsKey)!!

        // For now, use file scope directly
        // In the future, could use RAG or LLM to filter files
        val relevantFiles = fileScope.take(20)

        logger.info("Found ${relevantFiles.size} relevant files from file scope")

        storage.set(relevantFilesKey, relevantFiles)

        relevantFiles
    }

/**
 * Node: Analyze files (reads files directly)
 *
 * Reads files and extracts context (imports, classes, functions).
 */
private fun AIAgentSubgraphBuilderBase<ValidationResult, CodeAnalysisResult>.nodeAnalyzeFiles(
    model: LLModel
) =
    node<List<String>, List<FileContext>>("analyze-files") { relevantFiles ->
        logger.info("Analyzing ${relevantFiles.size} files")

        val sessionPath = storage.get(sessionPathKey)!!

        val fileContexts = mutableListOf<FileContext>()

        // Read files manually
        relevantFiles.forEach { filePath ->
            try {
                val file = File(sessionPath, filePath)
                if (file.exists() && file.isFile) {
                    val content = file.readText()
                    val language = detectLanguage(filePath)
                    fileContexts.add(
                        FileContext(
                            filePath = filePath,
                            content = content.take(5000), // Limit content
                            language = language,
                            totalLines = content.lines().size
                        )
                    )
                }
            } catch (e: Exception) {
                logger.warn("Failed to read file $filePath", e)
            }
        }

        logger.info("Analyzed ${fileContexts.size} files")
        storage.set(fileContextsKey, fileContexts)

        fileContexts
    }

/**
 * Node: Detect patterns (uses default patterns)
 *
 * Analyzes file contexts to detect coding style and patterns.
 */
private fun AIAgentSubgraphBuilderBase<ValidationResult, CodeAnalysisResult>.nodeDetectPatterns(
    model: LLModel
) =
    node<List<FileContext>, CodeAnalysisResult>("detect-patterns") { fileContexts ->
        logger.info("Detecting code patterns")

        // For now, use default patterns
        val detectedPatterns = CodePatterns(
            indentation = "4 spaces",
            namingConvention = "camelCase",
            codeStyle = "standard"
        )

        storage.set(detectedPatternsKey, detectedPatterns)

        val relevantFiles = storage.get(relevantFilesKey)!!
        val analysisResult = CodeAnalysisResult(
            relevantFiles = relevantFiles,
            fileContexts = fileContexts,
            detectedPatterns = detectedPatterns
        )

        storage.set(codeAnalysisResultKey, analysisResult)
        logger.info("Code analysis completed")

        analysisResult
    }

// Helper functions

private fun detectLanguage(filePath: String): String {
    return when (val ext = File(filePath).extension.lowercase()) {
        "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "js" -> "JavaScript"
        "ts" -> "TypeScript"
        "py" -> "Python"
        "rs" -> "Rust"
        "go" -> "Go"
        else -> ext.uppercase()
    }
}
