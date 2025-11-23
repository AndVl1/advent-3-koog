package ru.andvl.chatter.koog.agents.repoanalyzer.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.repoanalyzer.*
import ru.andvl.chatter.koog.model.repoanalyzer.SetupResult
import ru.andvl.chatter.koog.model.repoanalyzer.StructureResult
import java.io.File

private val logger = LoggerFactory.getLogger("repoanalyzer-structure")

/**
 * Language extensions map
 *
 * Maps file extensions to programming languages
 */
private val LANGUAGE_EXTENSIONS = mapOf(
    "kt" to "Kotlin",
    "kts" to "Kotlin",
    "java" to "Java",
    "js" to "JavaScript",
    "ts" to "TypeScript",
    "tsx" to "TypeScript",
    "jsx" to "JavaScript",
    "py" to "Python",
    "rb" to "Ruby",
    "go" to "Go",
    "rs" to "Rust",
    "cpp" to "C++",
    "cc" to "C++",
    "cxx" to "C++",
    "c" to "C",
    "h" to "C/C++",
    "hpp" to "C++",
    "cs" to "C#",
    "php" to "PHP",
    "swift" to "Swift",
    "m" to "Objective-C",
    "mm" to "Objective-C++",
    "scala" to "Scala",
    "groovy" to "Groovy",
    "sh" to "Shell",
    "bash" to "Shell",
    "zsh" to "Shell",
    "xml" to "XML",
    "html" to "HTML",
    "css" to "CSS",
    "scss" to "SCSS",
    "sass" to "Sass",
    "sql" to "SQL",
    "md" to "Markdown",
    "json" to "JSON",
    "yaml" to "YAML",
    "yml" to "YAML",
    "toml" to "TOML"
)

/**
 * Directories to skip during file tree traversal
 */
private val SKIP_DIRECTORIES = setOf(
    ".git",
    ".idea",
    ".vscode",
    "node_modules",
    "build",
    "target",
    "out",
    "bin",
    "obj",
    ".gradle",
    "__pycache__",
    ".pytest_cache",
    "venv",
    "env",
    ".venv",
    "dist",
    "coverage",
    ".next",
    ".nuxt"
)

/**
 * File extensions to skip when counting lines
 */
private val SKIP_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "gif", "bmp", "svg", "ico",
    "pdf", "zip", "tar", "gz", "jar", "war", "ear",
    "so", "dll", "dylib", "exe", "bin",
    "class", "pyc", "o", "a"
)

/**
 * Data class to hold file tree traversal results
 */
private data class FileTreeData(
    val tree: String,
    val languages: Map<String, Int>,
    val totalFiles: Int,
    val totalLines: Int
)

/**
 * Build file tree as string with indentation
 */
private fun buildFileTree(directory: File, prefix: String = "", maxDepth: Int = 3, currentDepth: Int = 0): String {
    if (currentDepth >= maxDepth) return ""

    val builder = StringBuilder()
    val files = directory.listFiles()?.filter { file ->
        !SKIP_DIRECTORIES.contains(file.name)
    }?.sortedBy { it.name } ?: emptyList()

    files.forEachIndexed { index, file ->
        val isLast = index == files.size - 1
        val marker = if (isLast) "└── " else "├── "
        val nextPrefix = if (isLast) "$prefix    " else "$prefix│   "

        builder.append("$prefix$marker${file.name}")
        if (file.isDirectory) {
            builder.append("/\n")
            builder.append(buildFileTree(file, nextPrefix, maxDepth, currentDepth + 1))
        } else {
            builder.append("\n")
        }
    }

    return builder.toString()
}

/**
 * Count lines in a file
 */
private fun countLines(file: File): Int {
    return try {
        file.readLines().size
    } catch (e: Exception) {
        logger.debug("Failed to read file ${file.name}: ${e.message}")
        0
    }
}

/**
 * Traverse directory and collect statistics
 */
private fun traverseDirectory(
    directory: File,
    languageStats: MutableMap<String, Int>,
    totalFiles: MutableList<Int>,
    totalLines: MutableList<Int>
) {
    val files = directory.listFiles() ?: return

    files.forEach { file ->
        if (file.isDirectory) {
            if (!SKIP_DIRECTORIES.contains(file.name)) {
                traverseDirectory(file, languageStats, totalFiles, totalLines)
            }
        } else {
            val extension = file.extension.lowercase()

            // Skip binary and non-source files
            if (SKIP_EXTENSIONS.contains(extension)) {
                return@forEach
            }

            // Count file
            totalFiles[0]++

            // Detect language
            val language = LANGUAGE_EXTENSIONS[extension]
            if (language != null) {
                languageStats[language] = languageStats.getOrDefault(language, 0) + 1

                // Count lines for source files
                val lines = countLines(file)
                totalLines[0] += lines
            }
        }
    }
}

/**
 * Subgraph: Structure Analysis
 *
 * Flow:
 * 1. Get file tree with limited depth
 * 2. Detect languages by file extensions
 * 3. Count total files and lines of code
 *
 * Input: SetupResult
 * Output: StructureResult (contains fileTree, languages, totalFiles, totalLines)
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphStructureAnalysis():
        AIAgentSubgraphDelegate<SetupResult, StructureResult> =
    subgraph(name = "structure-analysis") {
        val nodeGetFileTree by nodeGetFileTree()
        val nodeDetectLanguages by nodeDetectLanguages()
        val nodeCountFiles by nodeCountFiles()

        edge(nodeStart forwardTo nodeGetFileTree)
        edge(nodeGetFileTree forwardTo nodeDetectLanguages)
        edge(nodeDetectLanguages forwardTo nodeCountFiles)
        edge(nodeCountFiles forwardTo nodeFinish)
    }

/**
 * Node: Get file tree
 *
 * Builds a tree representation of the repository structure.
 */
private fun AIAgentSubgraphBuilderBase<SetupResult, StructureResult>.nodeGetFileTree() =
    node<SetupResult, SetupResult>("get-file-tree") { setupResult ->
        logger.info("Building file tree for: ${setupResult.repositoryPath}")

        val repoDir = File(setupResult.repositoryPath)
        if (!repoDir.exists() || !repoDir.isDirectory) {
            throw IllegalStateException("Repository path is not a valid directory: ${setupResult.repositoryPath}")
        }

        val fileTree = buildFileTree(repoDir, maxDepth = 3)

        storage.set(fileTreeKey, fileTree)
        logger.info("File tree built successfully (${fileTree.lines().size} lines)")

        setupResult
    }

/**
 * Node: Detect languages
 *
 * Analyzes file extensions to detect programming languages used in the repository.
 */
private fun AIAgentSubgraphBuilderBase<SetupResult, StructureResult>.nodeDetectLanguages() =
    node<SetupResult, SetupResult>("detect-languages") { setupResult ->
        logger.info("Detecting languages in repository")

        val repoDir = File(setupResult.repositoryPath)
        val languageStats = mutableMapOf<String, Int>()
        val totalFilesCount = mutableListOf(0)
        val totalLinesCount = mutableListOf(0)

        // Traverse directory and collect statistics
        traverseDirectory(repoDir, languageStats, totalFilesCount, totalLinesCount)

        logger.info("Languages detected: ${languageStats.keys.joinToString(", ")}")
        logger.info("Total files: ${totalFilesCount[0]}")
        logger.debug("Language breakdown: $languageStats")

        // Store in storage
        storage.set(languagesKey, languageStats)
        storage.set(totalFilesKey, totalFilesCount[0])
        storage.set(totalLinesKey, totalLinesCount[0])

        setupResult
    }

/**
 * Node: Count files and finalize structure result
 *
 * Combines all collected data into StructureResult.
 */
private fun AIAgentSubgraphBuilderBase<SetupResult, StructureResult>.nodeCountFiles() =
    node<SetupResult, StructureResult>("count-files") { setupResult ->
        logger.info("Finalizing structure analysis")

        val fileTree = storage.get(fileTreeKey) ?: ""
        val languages = storage.get(languagesKey) ?: emptyMap()
        val totalFiles = storage.get(totalFilesKey) ?: 0
        val totalLines = storage.get(totalLinesKey) ?: 0

        val structureResult = StructureResult(
            fileTree = fileTree,
            languages = languages,
            totalFiles = totalFiles,
            totalLines = totalLines
        )

        storage.set(structureResultKey, structureResult)

        logger.info("Structure analysis completed:")
        logger.info("  - Total files: $totalFiles")
        logger.info("  - Total lines: $totalLines")
        logger.info("  - Languages: ${languages.keys.joinToString(", ")}")

        structureResult
    }
