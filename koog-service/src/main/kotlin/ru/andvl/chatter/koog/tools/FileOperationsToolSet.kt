package ru.andvl.chatter.koog.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.isDirectory

@LLMDescription("Tools for file system operations: reading, searching, modifying files in a repository")
internal class FileOperationsToolSet(
    private val allowedBasePath: String? = null
) : ToolSet {
    private val logger = LoggerFactory.getLogger(FileOperationsToolSet::class.java)
    private val workDir = File("/tmp/code-modifications")
    private val repositoryDir = File("/tmp/repository-analyzer")
    private val maxFileSize = 10 * 1024 * 1024 // 10 MB
    private val maxFilesForSearch = 10_000

    init {
        workDir.mkdirs()
        repositoryDir.mkdirs()
    }

    @Tool("get-file-tree")
    @LLMDescription("Get directory tree structure with files and directories")
    fun getFileTree(
        @LLMDescription("Directory path to get tree from")
        directoryPath: String,
        @LLMDescription("Maximum depth to traverse (null = unlimited)")
        maxDepth: Int? = null,
        @LLMDescription("Include hidden files and directories")
        includeHidden: Boolean = false,
        @LLMDescription("Patterns to exclude (e.g., [\"node_modules\", \".git\", \"*.log\"])")
        excludePatterns: List<String> = emptyList()
    ): FileTreeResult {
        return try {
            val dir = File(directoryPath)

            if (!isPathSafe(dir)) {
                return FileTreeResult(
                    success = false,
                    tree = "",
                    totalFiles = 0,
                    totalDirectories = 0,
                    message = "Path is outside allowed directory: /tmp/code-modifications"
                )
            }

            if (!dir.exists() || !dir.isDirectory) {
                return FileTreeResult(
                    success = false,
                    tree = "",
                    totalFiles = 0,
                    totalDirectories = 0,
                    message = "Directory does not exist or is not a directory"
                )
            }

            var totalFiles = 0
            var totalDirectories = 0

            val tree = buildString {
                appendLine(dir.name + "/")
                buildTree(dir, "", maxDepth, 0, includeHidden, excludePatterns) { isFile ->
                    if (isFile) totalFiles++ else totalDirectories++
                }
            }

            logger.info("Generated file tree for $directoryPath: $totalFiles files, $totalDirectories directories")

            FileTreeResult(
                success = true,
                tree = tree,
                totalFiles = totalFiles,
                totalDirectories = totalDirectories,
                message = "Tree generated successfully"
            )
        } catch (e: Exception) {
            logger.error("Error generating file tree", e)
            FileTreeResult(
                success = false,
                tree = "",
                totalFiles = 0,
                totalDirectories = 0,
                message = "Error: ${e.message}"
            )
        }
    }

    private fun StringBuilder.buildTree(
        dir: File,
        prefix: String,
        maxDepth: Int?,
        currentDepth: Int,
        includeHidden: Boolean,
        excludePatterns: List<String>,
        counter: (Boolean) -> Unit
    ) {
        if (maxDepth != null && currentDepth >= maxDepth) return

        val files = dir.listFiles()?.filter { file ->
            val name = file.name
            val shouldInclude = (includeHidden || !name.startsWith(".")) &&
                    !excludePatterns.any { pattern ->
                        name.matches(pattern.replace("*", ".*").toRegex())
                    }
            shouldInclude
        }?.sortedBy { it.name } ?: return

        files.forEachIndexed { index, file ->
            val isLast = index == files.size - 1
            val connector = if (isLast) "└── " else "├── "
            val newPrefix = if (isLast) "$prefix    " else "$prefix│   "

            if (file.isDirectory) {
                appendLine("$prefix$connector${file.name}/")
                counter(false)
                buildTree(file, newPrefix, maxDepth, currentDepth + 1, includeHidden, excludePatterns, counter)
            } else {
                appendLine("$prefix$connector${file.name}")
                counter(true)
            }
        }
    }

    @Tool("read-file-content")
    @LLMDescription("Read file content with optional line range")
    fun readFileContent(
        @LLMDescription("Absolute path to the file")
        filePath: String,
        @LLMDescription("Start line number (1-indexed, inclusive). Null = from beginning")
        startLine: Int? = null,
        @LLMDescription("End line number (1-indexed, inclusive). Null = to end")
        endLine: Int? = null
    ): FileContentResult {
        return try {
            val file = File(filePath)

            if (!isPathSafe(file)) {
                return FileContentResult(
                    success = false,
                    filePath = filePath,
                    content = "",
                    totalLines = 0,
                    startLine = null,
                    endLine = null,
                    message = "Path is outside allowed directory: /tmp/code-modifications"
                )
            }

            if (!file.exists() || !file.isFile) {
                return FileContentResult(
                    success = false,
                    filePath = filePath,
                    content = "",
                    totalLines = 0,
                    startLine = null,
                    endLine = null,
                    message = "File does not exist or is not a file"
                )
            }

            if (file.length() > maxFileSize) {
                return FileContentResult(
                    success = false,
                    filePath = filePath,
                    content = "",
                    totalLines = 0,
                    startLine = null,
                    endLine = null,
                    message = "File size exceeds maximum allowed size of 10 MB"
                )
            }

            val lines = file.readLines()
            val totalLines = lines.size

            val actualStartLine = startLine?.coerceIn(1, totalLines) ?: 1
            val actualEndLine = endLine?.coerceIn(1, totalLines) ?: totalLines

            val content = lines.subList(actualStartLine - 1, actualEndLine).joinToString("\n")

            logger.info("Read file $filePath: lines $actualStartLine-$actualEndLine of $totalLines")

            FileContentResult(
                success = true,
                filePath = filePath,
                content = content,
                totalLines = totalLines,
                startLine = actualStartLine,
                endLine = actualEndLine,
                message = "File read successfully"
            )
        } catch (e: Exception) {
            logger.error("Error reading file", e)
            FileContentResult(
                success = false,
                filePath = filePath,
                content = "",
                totalLines = 0,
                startLine = null,
                endLine = null,
                message = "Error: ${e.message}"
            )
        }
    }

    @Tool("search-in-files")
    @LLMDescription("Search for regex pattern in files within a directory")
    fun searchInFiles(
        @LLMDescription("Directory path to search in")
        directoryPath: String,
        @LLMDescription("Regex pattern to search for")
        pattern: String,
        @LLMDescription("File glob patterns to include (e.g., [\"*.kt\", \"*.java\"])")
        filePatterns: List<String> = listOf("*"),
        @LLMDescription("Patterns to exclude directories/files")
        excludePatterns: List<String> = emptyList(),
        @LLMDescription("Number of context lines before and after match")
        contextLines: Int = 2,
        @LLMDescription("Case sensitive search")
        caseSensitive: Boolean = true
    ): SearchInFilesResult {
        return try {
            val dir = File(directoryPath)

            if (!isPathSafe(dir)) {
                return SearchInFilesResult(
                    success = false,
                    matches = emptyList(),
                    totalMatches = 0,
                    filesSearched = 0,
                    message = "Path is outside allowed directory: /tmp/code-modifications"
                )
            }

            if (!dir.exists() || !dir.isDirectory) {
                return SearchInFilesResult(
                    success = false,
                    matches = emptyList(),
                    totalMatches = 0,
                    filesSearched = 0,
                    message = "Directory does not exist or is not a directory"
                )
            }

            val regex = if (caseSensitive) {
                pattern.toRegex()
            } else {
                pattern.toRegex(RegexOption.IGNORE_CASE)
            }

            val matches = mutableListOf<SearchMatch>()
            var filesSearched = 0

            val files = Files.walk(Paths.get(directoryPath))
                .filter { !it.isDirectory() }
                .filter { path ->
                    val fileName = path.fileName.toString()
                    val shouldInclude = filePatterns.any { filePattern ->
                        fileName.matches(filePattern.replace("*", ".*").toRegex())
                    } && excludePatterns.none { excludePattern ->
                        path.toString().contains(excludePattern)
                    }
                    shouldInclude
                }
                .limit(maxFilesForSearch.toLong())
                .toList()

            for (path in files) {
                val file = path.toFile()
                if (file.length() > maxFileSize) continue

                try {
                    val lines = file.readLines()
                    filesSearched++

                    lines.forEachIndexed { index, line ->
                        val matchResult = regex.find(line)
                        if (matchResult != null) {
                            val lineNumber = index + 1
                            val contextBefore = lines.subList(
                                maxOf(0, index - contextLines),
                                index
                            )
                            val contextAfter = lines.subList(
                                minOf(lines.size, index + 1),
                                minOf(lines.size, index + 1 + contextLines)
                            )

                            matches.add(
                                SearchMatch(
                                    filePath = file.absolutePath,
                                    lineNumber = lineNumber,
                                    columnNumber = matchResult.range.first + 1,
                                    matchedText = matchResult.value,
                                    lineContent = line,
                                    contextBefore = contextBefore,
                                    contextAfter = contextAfter
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to search in file ${file.absolutePath}: ${e.message}")
                }
            }

            logger.info("Search completed: ${matches.size} matches in $filesSearched files")

            SearchInFilesResult(
                success = true,
                matches = matches,
                totalMatches = matches.size,
                filesSearched = filesSearched,
                message = "Search completed successfully"
            )
        } catch (e: Exception) {
            logger.error("Error searching in files", e)
            SearchInFilesResult(
                success = false,
                matches = emptyList(),
                totalMatches = 0,
                filesSearched = 0,
                message = "Error: ${e.message}"
            )
        }
    }

    @Tool("apply-patch")
    @LLMDescription("Apply a patch to a file by replacing lines with new content")
    fun applyPatch(
        @LLMDescription("Absolute path to the file")
        filePath: String,
        @LLMDescription("Start line to remove (1-indexed, inclusive)")
        startLine: Int,
        @LLMDescription("End line to remove (1-indexed, inclusive)")
        endLine: Int,
        @LLMDescription("New content to insert in place of removed lines")
        replacementContent: String
    ): ApplyPatchResult {
        return try {
            val file = File(filePath)

            if (!isPathSafe(file)) {
                return ApplyPatchResult(
                    success = false,
                    filePath = filePath,
                    linesRemoved = 0,
                    linesAdded = 0,
                    preview = "",
                    message = "Path is outside allowed directory: /tmp/code-modifications"
                )
            }

            if (!file.exists() || !file.isFile) {
                return ApplyPatchResult(
                    success = false,
                    filePath = filePath,
                    linesRemoved = 0,
                    linesAdded = 0,
                    preview = "",
                    message = "File does not exist or is not a file"
                )
            }

            val lines = file.readLines().toMutableList()
            val totalLines = lines.size

            if (startLine < 1 || endLine < 1 || startLine > totalLines || endLine > totalLines) {
                return ApplyPatchResult(
                    success = false,
                    filePath = filePath,
                    linesRemoved = 0,
                    linesAdded = 0,
                    preview = "",
                    message = "Invalid line range: $startLine-$endLine (file has $totalLines lines)"
                )
            }

            if (startLine > endLine) {
                return ApplyPatchResult(
                    success = false,
                    filePath = filePath,
                    linesRemoved = 0,
                    linesAdded = 0,
                    preview = "",
                    message = "Start line must be <= end line"
                )
            }

            val linesRemoved = endLine - startLine + 1
            val replacementLines = if (replacementContent.isEmpty()) {
                emptyList()
            } else {
                replacementContent.split("\n")
            }
            val linesAdded = replacementLines.size

            // Remove lines and insert replacement
            repeat(linesRemoved) {
                lines.removeAt(startLine - 1)
            }
            lines.addAll(startLine - 1, replacementLines)

            // Write file
            file.writeText(lines.joinToString("\n"))

            // Generate preview (10 lines before and after the change)
            val previewStart = maxOf(0, startLine - 11)
            val previewEnd = minOf(lines.size, startLine + linesAdded + 10)
            val preview = lines.subList(previewStart, previewEnd)
                .mapIndexed { index, line ->
                    val lineNum = previewStart + index + 1
                    val marker = if (lineNum >= startLine && lineNum < startLine + linesAdded) "*" else " "
                    "$marker $lineNum: $line"
                }
                .joinToString("\n")

            logger.info("Applied patch to $filePath: removed $linesRemoved lines, added $linesAdded lines")

            ApplyPatchResult(
                success = true,
                filePath = filePath,
                linesRemoved = linesRemoved,
                linesAdded = linesAdded,
                preview = preview,
                message = "Patch applied successfully"
            )
        } catch (e: Exception) {
            logger.error("Error applying patch", e)
            ApplyPatchResult(
                success = false,
                filePath = filePath,
                linesRemoved = 0,
                linesAdded = 0,
                preview = "",
                message = "Error: ${e.message}"
            )
        }
    }

    @Tool("apply-patches")
    @LLMDescription("Apply multiple patches to a single file atomically")
    fun applyPatches(
        @LLMDescription("Absolute path to the file")
        filePath: String,
        @LLMDescription("List of patches to apply (will be sorted from end to start)")
        patches: List<PatchSpec>
    ): ApplyPatchesResult {
        return try {
            val file = File(filePath)

            if (!isPathSafe(file)) {
                return ApplyPatchesResult(
                    success = false,
                    filePath = filePath,
                    patchesApplied = 0,
                    totalLinesRemoved = 0,
                    totalLinesAdded = 0,
                    preview = "",
                    message = "Path is outside allowed directory: /tmp/code-modifications"
                )
            }

            if (!file.exists() || !file.isFile) {
                return ApplyPatchesResult(
                    success = false,
                    filePath = filePath,
                    patchesApplied = 0,
                    totalLinesRemoved = 0,
                    totalLinesAdded = 0,
                    preview = "",
                    message = "File does not exist or is not a file"
                )
            }

            val lines = file.readLines().toMutableList()

            // Sort patches from end to start to avoid line number shifts
            val sortedPatches = patches.sortedByDescending { it.startLine }

            var totalLinesRemoved = 0
            var totalLinesAdded = 0

            for (patch in sortedPatches) {
                val linesRemoved = patch.endLine - patch.startLine + 1
                val replacementLines = if (patch.replacementContent.isEmpty()) {
                    emptyList()
                } else {
                    patch.replacementContent.split("\n")
                }

                // Validate patch
                if (patch.startLine < 1 || patch.endLine > lines.size || patch.startLine > patch.endLine) {
                    return ApplyPatchesResult(
                        success = false,
                        filePath = filePath,
                        patchesApplied = 0,
                        totalLinesRemoved = 0,
                        totalLinesAdded = 0,
                        preview = "",
                        message = "Invalid patch range: ${patch.startLine}-${patch.endLine}"
                    )
                }

                // Apply patch
                repeat(linesRemoved) {
                    lines.removeAt(patch.startLine - 1)
                }
                lines.addAll(patch.startLine - 1, replacementLines)

                totalLinesRemoved += linesRemoved
                totalLinesAdded += replacementLines.size
            }

            // Write file
            file.writeText(lines.joinToString("\n"))

            // Generate preview
            val preview = lines.take(20).mapIndexed { index, line ->
                "${index + 1}: $line"
            }.joinToString("\n")

            logger.info("Applied ${sortedPatches.size} patches to $filePath")

            ApplyPatchesResult(
                success = true,
                filePath = filePath,
                patchesApplied = sortedPatches.size,
                totalLinesRemoved = totalLinesRemoved,
                totalLinesAdded = totalLinesAdded,
                preview = preview,
                message = "All patches applied successfully"
            )
        } catch (e: Exception) {
            logger.error("Error applying patches", e)
            ApplyPatchesResult(
                success = false,
                filePath = filePath,
                patchesApplied = 0,
                totalLinesRemoved = 0,
                totalLinesAdded = 0,
                preview = "",
                message = "Error: ${e.message}"
            )
        }
    }

    @Tool("create-file")
    @LLMDescription("Create a new file with content")
    fun createFile(
        @LLMDescription("Absolute path to the new file")
        filePath: String,
        @LLMDescription("Content to write to the file")
        content: String,
        @LLMDescription("Create intermediate directories if they don't exist")
        createDirectories: Boolean = true
    ): CreateFileResult {
        return try {
            val file = File(filePath)

            if (!isPathSafe(file)) {
                return CreateFileResult(
                    success = false,
                    filePath = filePath,
                    linesWritten = 0,
                    message = "Path is outside allowed directory: /tmp/code-modifications"
                )
            }

            if (file.exists()) {
                return CreateFileResult(
                    success = false,
                    filePath = filePath,
                    linesWritten = 0,
                    message = "File already exists. Use apply-patch to modify existing files."
                )
            }

            if (createDirectories) {
                file.parentFile?.mkdirs()
            }

            file.writeText(content)

            val linesWritten = content.split("\n").size

            logger.info("Created file $filePath with $linesWritten lines")

            CreateFileResult(
                success = true,
                filePath = filePath,
                linesWritten = linesWritten,
                message = "File created successfully"
            )
        } catch (e: Exception) {
            logger.error("Error creating file", e)
            CreateFileResult(
                success = false,
                filePath = filePath,
                linesWritten = 0,
                message = "Error: ${e.message}"
            )
        }
    }

    @Tool("delete-file")
    @LLMDescription("Delete a file")
    fun deleteFile(
        @LLMDescription("Absolute path to the file to delete")
        filePath: String
    ): DeleteFileResult {
        return try {
            val file = File(filePath)

            if (!isPathSafe(file)) {
                return DeleteFileResult(
                    success = false,
                    filePath = filePath,
                    message = "Path is outside allowed directory: /tmp/code-modifications"
                )
            }

            if (!file.exists()) {
                return DeleteFileResult(
                    success = false,
                    filePath = filePath,
                    message = "File does not exist"
                )
            }

            if (!file.isFile) {
                return DeleteFileResult(
                    success = false,
                    filePath = filePath,
                    message = "Path is not a file (cannot delete directories)"
                )
            }

            file.delete()

            logger.info("Deleted file $filePath")

            DeleteFileResult(
                success = true,
                filePath = filePath,
                message = "File deleted successfully"
            )
        } catch (e: Exception) {
            logger.error("Error deleting file", e)
            DeleteFileResult(
                success = false,
                filePath = filePath,
                message = "Error: ${e.message}"
            )
        }
    }

    private fun isPathSafe(file: File): Boolean {
        val canonicalPath = file.canonicalPath
        val workDirPath = workDir.canonicalPath
        val repositoryDirPath = repositoryDir.canonicalPath

        // Check hardcoded paths
        val isInHardcodedPaths = canonicalPath.startsWith(workDirPath) ||
                                 canonicalPath.startsWith(repositoryDirPath)

        // Check custom allowed path if provided
        val isInCustomPath = allowedBasePath?.let { basePath ->
            val baseDir = File(basePath)
            canonicalPath.startsWith(baseDir.canonicalPath)
        } ?: false

        return isInHardcodedPaths || isInCustomPath
    }
}

// Result classes

@Serializable
data class FileTreeResult(
    @SerialName("success") val success: Boolean,
    @SerialName("tree") val tree: String,
    @SerialName("total_files") val totalFiles: Int,
    @SerialName("total_directories") val totalDirectories: Int,
    @SerialName("message") val message: String
)

@Serializable
data class FileContentResult(
    @SerialName("success") val success: Boolean,
    @SerialName("file_path") val filePath: String,
    @SerialName("content") val content: String,
    @SerialName("total_lines") val totalLines: Int,
    @SerialName("start_line") val startLine: Int?,
    @SerialName("end_line") val endLine: Int?,
    @SerialName("message") val message: String
)

@Serializable
data class SearchInFilesResult(
    @SerialName("success") val success: Boolean,
    @SerialName("matches") val matches: List<SearchMatch>,
    @SerialName("total_matches") val totalMatches: Int,
    @SerialName("files_searched") val filesSearched: Int,
    @SerialName("message") val message: String
)

@Serializable
data class SearchMatch(
    @SerialName("file_path") val filePath: String,
    @SerialName("line_number") val lineNumber: Int,
    @SerialName("column_number") val columnNumber: Int,
    @SerialName("matched_text") val matchedText: String,
    @SerialName("line_content") val lineContent: String,
    @SerialName("context_before") val contextBefore: List<String>,
    @SerialName("context_after") val contextAfter: List<String>
)

@Serializable
data class ApplyPatchResult(
    @SerialName("success") val success: Boolean,
    @SerialName("file_path") val filePath: String,
    @SerialName("lines_removed") val linesRemoved: Int,
    @SerialName("lines_added") val linesAdded: Int,
    @SerialName("preview") val preview: String,
    @SerialName("message") val message: String
)

@Serializable
data class PatchSpec(
    @SerialName("start_line") val startLine: Int,
    @SerialName("end_line") val endLine: Int,
    @SerialName("replacement_content") val replacementContent: String
)

@Serializable
data class ApplyPatchesResult(
    @SerialName("success") val success: Boolean,
    @SerialName("file_path") val filePath: String,
    @SerialName("patches_applied") val patchesApplied: Int,
    @SerialName("total_lines_removed") val totalLinesRemoved: Int,
    @SerialName("total_lines_added") val totalLinesAdded: Int,
    @SerialName("preview") val preview: String,
    @SerialName("message") val message: String
)

@Serializable
data class CreateFileResult(
    @SerialName("success") val success: Boolean,
    @SerialName("file_path") val filePath: String,
    @SerialName("lines_written") val linesWritten: Int,
    @SerialName("message") val message: String
)

@Serializable
data class DeleteFileResult(
    @SerialName("success") val success: Boolean,
    @SerialName("file_path") val filePath: String,
    @SerialName("message") val message: String
)
