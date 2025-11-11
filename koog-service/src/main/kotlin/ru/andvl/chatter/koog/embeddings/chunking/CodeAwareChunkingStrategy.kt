package ru.andvl.chatter.koog.embeddings.chunking

import ru.andvl.chatter.koog.embeddings.model.*
import java.util.*

/**
 * Code-aware chunking strategy that respects semantic boundaries
 */
internal class CodeAwareChunkingStrategy : ChunkingStrategy {
    private val codeExtensions = setOf(
        ".kt", ".java", ".py", ".js", ".ts", ".jsx", ".tsx",
        ".go", ".rs", ".cpp", ".c", ".h", ".hpp", ".cs",
        ".rb", ".php", ".swift", ".scala"
    )

    override fun canHandle(filePath: String): Boolean {
        return codeExtensions.any { filePath.endsWith(it, ignoreCase = true) }
    }

    override fun chunk(
        filePath: String,
        content: String,
        repository: String,
        config: EmbeddingConfig
    ): List<DocumentChunk> {
        val language = detectLanguage(filePath)
        val lines = content.lines()
        val chunks = mutableListOf<DocumentChunk>()

        // Try to extract functions/classes
        val semanticBlocks = extractSemanticBlocks(lines, language)

        if (semanticBlocks.isNotEmpty()) {
            // Use semantic blocks
            semanticBlocks.forEach { block ->
                val chunkContent = lines.subList(block.startLine, block.endLine + 1).joinToString("\n")
                if (chunkContent.isNotBlank()) {
                    chunks.add(
                        DocumentChunk(
                            id = UUID.randomUUID().toString(),
                            content = chunkContent,
                            metadata = ChunkMetadata(
                                filePath = filePath,
                                fileName = filePath.substringAfterLast('/'),
                                fileType = FileType.CODE,
                                repository = repository,
                                chunkType = block.type,
                                language = language,
                                functionName = block.functionName,
                                className = block.className
                            ),
                            startLine = block.startLine,
                            endLine = block.endLine
                        )
                    )
                }
            }
        } else {
            // Fallback to sliding window
            chunks.addAll(
                slidingWindowChunk(
                    lines = lines,
                    filePath = filePath,
                    repository = repository,
                    config = config,
                    language = language
                )
            )
        }

        return chunks.take(config.maxChunks)
    }

    private fun detectLanguage(filePath: String): String {
        return when {
            filePath.endsWith(".kt") -> "kotlin"
            filePath.endsWith(".java") -> "java"
            filePath.endsWith(".py") -> "python"
            filePath.endsWith(".js") || filePath.endsWith(".jsx") -> "javascript"
            filePath.endsWith(".ts") || filePath.endsWith(".tsx") -> "typescript"
            filePath.endsWith(".go") -> "go"
            filePath.endsWith(".rs") -> "rust"
            filePath.endsWith(".rb") -> "ruby"
            filePath.endsWith(".php") -> "php"
            else -> "unknown"
        }
    }

    private data class SemanticBlock(
        val startLine: Int,
        val endLine: Int,
        val type: ChunkType,
        val functionName: String? = null,
        val className: String? = null
    )

    private fun extractSemanticBlocks(lines: List<String>, language: String): List<SemanticBlock> {
        val blocks = mutableListOf<SemanticBlock>()
        var currentBlock: SemanticBlock? = null
        var braceCount = 0
        var inBlock = false

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()

            // Detect function/class start
            when (language) {
                "kotlin", "java" -> {
                    if (isFunctionStart(trimmed, language)) {
                        if (currentBlock == null) {
                            val functionName = extractFunctionName(trimmed, language)
                            currentBlock = SemanticBlock(
                                startLine = index,
                                endLine = index,
                                type = ChunkType.FUNCTION,
                                functionName = functionName
                            )
                            inBlock = true
                        }
                    } else if (isClassStart(trimmed, language)) {
                        if (currentBlock == null) {
                            val className = extractClassName(trimmed, language)
                            currentBlock = SemanticBlock(
                                startLine = index,
                                endLine = index,
                                type = ChunkType.CLASS,
                                className = className
                            )
                            inBlock = true
                        }
                    }
                }
                "python" -> {
                    if (trimmed.startsWith("def ") || trimmed.startsWith("async def ")) {
                        if (currentBlock != null && braceCount == 0) {
                            blocks.add(currentBlock)
                        }
                        val functionName = trimmed.substringAfter("def ").substringBefore("(").trim()
                        currentBlock = SemanticBlock(
                            startLine = index,
                            endLine = index,
                            type = ChunkType.FUNCTION,
                            functionName = functionName
                        )
                        inBlock = true
                    } else if (trimmed.startsWith("class ")) {
                        if (currentBlock != null && braceCount == 0) {
                            blocks.add(currentBlock)
                        }
                        val className = trimmed.substringAfter("class ").substringBefore(":").substringBefore("(").trim()
                        currentBlock = SemanticBlock(
                            startLine = index,
                            endLine = index,
                            type = ChunkType.CLASS,
                            className = className
                        )
                        inBlock = true
                    }
                }
            }

            // Track braces for block boundaries
            if (inBlock) {
                braceCount += trimmed.count { it == '{' }
                braceCount -= trimmed.count { it == '}' }

                currentBlock?.let {
                    currentBlock = it.copy(endLine = index)
                }

                // Python uses indentation
                if (language == "python") {
                    if (trimmed.isNotEmpty() && !trimmed.startsWith(" ") && !trimmed.startsWith("\t") && index > currentBlock!!.startLine) {
                        blocks.add(currentBlock!!)
                        currentBlock = null
                        inBlock = false
                    }
                } else {
                    if (braceCount == 0 && trimmed.contains("}")) {
                        currentBlock?.let { blocks.add(it) }
                        currentBlock = null
                        inBlock = false
                    }
                }
            }
        }

        // Add last block if exists
        currentBlock?.let { blocks.add(it) }

        return blocks
    }

    private fun isFunctionStart(line: String, language: String): Boolean {
        return when (language) {
            "kotlin" -> line.contains("fun ") && line.contains("(")
            "java" -> {
                val hasParentheses = line.contains("(") && line.contains(")")
                val hasAccessModifier = line.contains("public") || line.contains("private") ||
                                       line.contains("protected") || line.contains("internal")
                val notClass = !line.contains("class") && !line.contains("interface")
                hasParentheses && (hasAccessModifier || line.contains("fun")) && notClass
            }
            else -> false
        }
    }

    private fun isClassStart(line: String, language: String): Boolean {
        return when (language) {
            "kotlin", "java" -> (line.contains("class ") || line.contains("interface ") ||
                                line.contains("object ")) && line.contains("{")
            else -> false
        }
    }

    private fun extractFunctionName(line: String, language: String): String? {
        return try {
            when (language) {
                "kotlin" -> line.substringAfter("fun ").substringBefore("(").trim()
                "java" -> {
                    val parts = line.split("(")[0].split(" ")
                    parts.lastOrNull()?.trim()
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractClassName(line: String, language: String): String? {
        return try {
            when (language) {
                "kotlin", "java" -> {
                    line.substringAfter("class ")
                        .substringAfter("interface ")
                        .substringAfter("object ")
                        .substringBefore("(")
                        .substringBefore("{")
                        .substringBefore(":")
                        .trim()
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun slidingWindowChunk(
        lines: List<String>,
        filePath: String,
        repository: String,
        config: EmbeddingConfig,
        language: String
    ): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()
        var currentLine = 0

        while (currentLine < lines.size) {
            val endLine = minOf(currentLine + config.chunkSize, lines.size)
            val chunkContent = lines.subList(currentLine, endLine).joinToString("\n")

            if (chunkContent.isNotBlank()) {
                chunks.add(
                    DocumentChunk(
                        id = UUID.randomUUID().toString(),
                        content = chunkContent,
                        metadata = ChunkMetadata(
                            filePath = filePath,
                            fileName = filePath.substringAfterLast('/'),
                            fileType = FileType.CODE,
                            repository = repository,
                            chunkType = ChunkType.CODE_BLOCK,
                            language = language
                        ),
                        startLine = currentLine,
                        endLine = endLine - 1
                    )
                )
            }

            currentLine += config.chunkSize - config.chunkOverlap
        }

        return chunks
    }
}
