package ru.andvl.chatter.cli.utils

/**
 * Utility for wrapping text with proper Russian hyphenation
 */
object TextWrapper {
    
    /**
     * Wrap text to specified width with proper Russian word breaks
     */
    fun wrap(text: String, maxWidth: Int = 80): String {
        if (text.length <= maxWidth) return text
        
        val words = text.split(" ")
        val result = mutableListOf<String>()
        var currentLine = StringBuilder()
        
        for (word in words) {
            // If adding this word would exceed the line length
            if (currentLine.length + word.length + 1 > maxWidth) {
                if (currentLine.isNotEmpty()) {
                    result.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
                
                // If single word is longer than max width, hyphenate it
                if (word.length > maxWidth) {
                    val hyphenatedParts = hyphenateRussianWord(word, maxWidth)
                    hyphenatedParts.dropLast(1).forEach { part ->
                        result.add(part)
                    }
                    currentLine.append(hyphenatedParts.last())
                } else {
                    currentLine.append(word)
                }
            } else {
                if (currentLine.isNotEmpty()) {
                    currentLine.append(" ")
                }
                currentLine.append(word)
            }
        }
        
        if (currentLine.isNotEmpty()) {
            result.add(currentLine.toString())
        }
        
        return result.joinToString("\n")
    }
    
    /**
     * Hyphenate a Russian word when it's too long for a line
     */
    private fun hyphenateRussianWord(word: String, maxWidth: Int): List<String> {
        if (word.length <= maxWidth) return listOf(word)
        
        val parts = mutableListOf<String>()
        var remaining = word
        
        while (remaining.length > maxWidth) {
            val breakPoint = findBestBreakPoint(remaining, maxWidth)
            val part = remaining.substring(0, breakPoint) + "-"
            parts.add(part)
            remaining = remaining.substring(breakPoint)
        }
        
        if (remaining.isNotEmpty()) {
            parts.add(remaining)
        }
        
        return parts
    }
    
    /**
     * Find the best place to break a Russian word
     */
    private fun findBestBreakPoint(word: String, maxWidth: Int): Int {
        val availableWidth = maxWidth - 1 // Reserve space for hyphen
        
        // Russian vowels for syllable detection
        val vowels = setOf('а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я', 
                          'А', 'Е', 'Ё', 'И', 'О', 'У', 'Ы', 'Э', 'Ю', 'Я')
        
        // Try to break after a vowel + consonant pattern
        for (i in (availableWidth - 2) downTo 2) {
            if (i < word.length - 1 && 
                vowels.contains(word[i - 1]) && 
                !vowels.contains(word[i])) {
                return i
            }
        }
        
        // Fallback: break at available width
        return availableWidth.coerceAtMost(word.length)
    }
    
    /**
     * Wrap text with indentation
     */
    fun wrapWithIndent(text: String, indent: String = "  ", maxWidth: Int = 80): String {
        val wrappedText = wrap(text, maxWidth - indent.length)
        return wrappedText.lines().joinToString("\n") { line ->
            if (line.isBlank()) line else indent + line
        }
    }
}