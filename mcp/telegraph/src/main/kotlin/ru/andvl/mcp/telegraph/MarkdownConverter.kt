package ru.andvl.mcp.telegraph

/**
 * Internal Markdown to Telegraph node converter
 * 
 * Converts Markdown strings to Telegraph JSON nodes and vice versa
 * without external dependencies
 */
object MarkdownConverter {
    
    /**
     * Convert Markdown string to Telegraph nodes
     */
    fun markdownToNodes(markdown: String): List<TelegraphNode> {
        if (markdown.isBlank()) return emptyList()
        
        val lines = markdown.split("\n")
        val nodes = mutableListOf<TelegraphNode>()
        var i = 0
        val maxIterations = lines.size * 3 // Safety guard
        
        var currentIteration = 0
        while (i < lines.size && currentIteration < maxIterations) {
            currentIteration++
            val line = lines[i]
            
            when {
                // Headers
                line.startsWith("#### ") -> {
                    nodes.add(TelegraphNode("h4", listOf(line.substring(5))))
                    i++
                }
                line.startsWith("### ") -> {
                    nodes.add(TelegraphNode("h3", listOf(line.substring(4))))
                    i++
                }
                line.startsWith("## ") -> {
                    nodes.add(TelegraphNode("h2", listOf(line.substring(3))))
                    i++
                }
                line.startsWith("# ") -> {
                    nodes.add(TelegraphNode("h1", listOf(line.substring(2))))
                    i++
                }
                
                // Horizontal rule
                line.trim() == "---" || line.trim() == "***" -> {
                    nodes.add(TelegraphNode("hr", emptyList()))
                    i++
                }
                
                // Blockquote
                line.startsWith("> ") -> {
                    val quoteLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].startsWith("> ")) {
                        quoteLines.add(lines[i].substring(2))
                        i++
                    }
                    nodes.add(TelegraphNode("blockquote", quoteLines))
                }
                
                // Code block
                line.startsWith("```") -> {
                    val language = line.substring(3).trim()
                    i++
                    val codeLines = mutableListOf<String>()
                    while (i < lines.size && !lines[i].startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    nodes.add(TelegraphNode("pre", listOf(codeLines.joinToString("\n"))))
                    if (i < lines.size) i++ // Skip closing ```
                }
                
                // Lists
                Regex("^[\\-\\*\\+]\\s+").matches(line.trim()) -> {
                    val listItems = mutableListOf<String>()
                    while (i < lines.size && Regex("^[\\-\\*\\+]\\s+").matches(lines[i].trim())) {
                        listItems.add(lines[i].trim().substring(2))
                        i++
                    }
                    nodes.add(TelegraphNode("ul", listItems))
                }
                
                Regex("^\\d+\\.\\s+").matches(line.trim()) -> {
                    val listItems = mutableListOf<String>()
                    while (i < lines.size && Regex("^\\d+\\.\\s+").matches(lines[i].trim())) {
                        listItems.add(lines[i].trim().substringAfter(". "))
                        i++
                    }
                    nodes.add(TelegraphNode("ol", listItems))
                }
                
                // Empty line
                line.isBlank() -> {
                    i++
                }
                
                // Regular paragraph (possibly with inline formatting)
                else -> {
                    val paragraphLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].isNotBlank() && 
                           !Regex("^(#{1,4}|\\s*[\\*\\-\\+]|\\s*\\d+\\.|>)").matches(lines[i]) &&
                           !lines[i].trim().startsWith("---") && !lines[i].trim().startsWith("```")) {
                        paragraphLines.add(lines[i])
                        i++
                    }
                    
                    if (paragraphLines.isNotEmpty()) {
                        val paragraphText = paragraphLines.joinToString(" ")
                        nodes.addAll(parseInlineFormatting(paragraphText))
                    }
                }
            }
        }
        
        return nodes
    }
    
    /**
     * Parse inline formatting (bold, italic, code, links)
     */
    private fun parseInlineFormatting(text: String): List<TelegraphNode> {
        val nodes = mutableListOf<TelegraphNode>()
        var remaining = text
        var iterations = 0
        val maxIterations = text.length * 2 // Safety guard against infinite loops
        
        // Links [text](url)
        val linkRegex = """\[([^\]]+)\]\(([^)]+)\)""".toRegex()
        // Bold **text** - non-greedy
        val boldRegex = """\*\*([^*]+?)\*\*""".toRegex()
        // Italic *text* - but not inside bold, non-greedy
        val italicRegex = """(?<!\*)\*([^*]+?)\*(?!\*)""".toRegex()
        // Code `text`
        val codeRegex = """`([^`]+?)`""".toRegex()
        
        // Process in order: links, bold, italic, code
        while (remaining.isNotEmpty() && iterations < maxIterations) {
            iterations++
            
            val linkMatch = linkRegex.find(remaining)
            val boldMatch = boldRegex.find(remaining)
            val italicMatch = italicRegex.find(remaining)
            val codeMatch = codeRegex.find(remaining)
            
            val matches = mutableListOf<MatchInfo>()
            linkMatch?.let { matches.add(MatchInfo(it.range.first, "link", it)) }
            boldMatch?.let { matches.add(MatchInfo(it.range.first, "bold", it)) }
            italicMatch?.let { matches.add(MatchInfo(it.range.first, "italic", it)) }
            codeMatch?.let { matches.add(MatchInfo(it.range.first, "code", it)) }
            
            val sortedMatches = matches.sortedBy { it.position }
            
            if (sortedMatches.isEmpty()) {
                if (remaining.isNotBlank()) {
                    nodes.add(TelegraphNode("p", listOf(remaining)))
                }
                break
            }
            
            val firstMatch = sortedMatches.first()
            
            // Add text before match
            if (firstMatch.position > 0) {
                val beforeText = remaining.substring(0, firstMatch.position)
                if (beforeText.isNotBlank()) {
                    nodes.add(TelegraphNode("p", listOf(beforeText)))
                }
            }
            
            // Add formatted node
            when (firstMatch.type) {
                "link" -> {
                    val linkText = firstMatch.match.groupValues[1]
                    val url = firstMatch.match.groupValues[2]
                    nodes.add(TelegraphNode("a", listOf(linkText), mapOf("href" to url)))
                    remaining = remaining.substring(firstMatch.match.range.last + 1)
                }
                "bold" -> {
                    val boldText = firstMatch.match.groupValues[1]
                    nodes.add(TelegraphNode("strong", listOf(boldText)))
                    remaining = remaining.substring(firstMatch.match.range.last + 1)
                }
                "italic" -> {
                    val italicText = firstMatch.match.groupValues[1]
                    nodes.add(TelegraphNode("em", listOf(italicText)))
                    remaining = remaining.substring(firstMatch.match.range.last + 1)
                }
                "code" -> {
                    val codeText = firstMatch.match.groupValues[1]
                    nodes.add(TelegraphNode("code", listOf(codeText)))
                    remaining = remaining.substring(firstMatch.match.range.last + 1)
                }
            }
        }
        
        // Safety check for infinite loops
        if (iterations >= maxIterations) {
            // Return original text as paragraph if parsing fails
            return listOf(TelegraphNode("p", listOf(text)))
        }
        
        return nodes.ifEmpty { listOf(TelegraphNode("p", listOf(text))) }
    }
    
    /**
     * Convert Telegraph nodes back to Markdown
     */
    fun nodesToMarkdown(nodes: List<TelegraphNode>): String {
        return nodes.joinToString("\n\n") { node ->
            when (node.tag) {
                "h1" -> "# ${node.children?.joinToString("") ?: ""}"
                "h2" -> "## ${node.children?.joinToString("") ?: ""}"
                "h3" -> "### ${node.children?.joinToString("") ?: ""}"
                "h4" -> "#### ${node.children?.joinToString("") ?: ""}"
                "p" -> node.children?.joinToString("") ?: ""
                "strong", "b" -> "**${node.children?.joinToString("") ?: ""}**"
                "em", "i" -> "*${node.children?.joinToString("") ?: ""}*"
                "code" -> "`${node.children?.joinToString("") ?: ""}`"
                "pre" -> "```\n${node.children?.joinToString("\n") ?: ""}\n```"
                "a" -> {
                    val text = node.children?.joinToString("") ?: ""
                    val href = node.attrs?.get("href") ?: ""
                    "[$text]($href)"
                }
                "ul" -> node.children?.joinToString("\n") { "- $it" } ?: ""
                "ol" -> node.children?.mapIndexed { i, item -> "${i + 1}. $item" }?.joinToString("\n") ?: ""
                "li" -> "- ${node.children?.joinToString("") ?: ""}"
                "blockquote" -> node.children?.joinToString("\n") { "> $it" }?.lines()?.joinToString("\n") ?: ""
                "hr" -> "---"
                "img" -> {
                    val src = node.attrs?.get("src") ?: ""
                    val alt = node.attrs?.get("alt") ?: ""
                    "![$alt]($src)"
                }
                else -> node.children?.joinToString("") ?: ""
            }
        }
    }
    
    private data class MatchInfo(
        val position: Int,
        val type: String,
        val match: MatchResult
    )
}