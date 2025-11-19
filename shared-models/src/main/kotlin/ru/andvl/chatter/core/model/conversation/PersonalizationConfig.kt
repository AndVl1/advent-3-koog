package ru.andvl.chatter.core.model.conversation

import kotlinx.serialization.Serializable

/**
 * Configuration for personalizing AI responses
 */
@Serializable
data class PersonalizationConfig(
    /**
     * Character or persona style for responses
     * Example: "friendly assistant", "professional consultant", "casual friend"
     */
    val characterStyle: String = "helpful AI assistant",

    /**
     * Information about the user
     * Example: "Software engineer named Alex, prefers concise answers"
     */
    val userContext: String = "",

    /**
     * User's preferences and habits
     * Example: "Likes detailed technical explanations, works with Kotlin and Python"
     */
    val preferences: String = "",

    /**
     * Additional context or instructions
     * Example: "Always respond in Russian", "Use emojis when appropriate"
     */
    val additionalContext: String = ""
) {
    companion object {
        val DEFAULT = PersonalizationConfig()
    }

    /**
     * Check if personalization is enabled (any field is non-empty besides default characterStyle)
     */
    fun isEnabled(): Boolean =
        userContext.isNotBlank() || preferences.isNotBlank() || additionalContext.isNotBlank()
                || characterStyle != DEFAULT.characterStyle

    /**
     * Build system prompt section for personalization
     */
    fun toSystemPromptSection(): String? {
        if (!isEnabled()) return null

        return buildString {
            appendLine("## Personalization")
            appendLine()

            if (characterStyle != DEFAULT.characterStyle) {
                appendLine("Your character: $characterStyle")
                appendLine()
            }

            if (userContext.isNotBlank()) {
                appendLine("About the user:")
                appendLine(userContext)
                appendLine()
            }

            if (preferences.isNotBlank()) {
                appendLine("User preferences:")
                appendLine(preferences)
                appendLine()
            }

            if (additionalContext.isNotBlank()) {
                appendLine("Additional context:")
                appendLine(additionalContext)
            }
        }.trim()
    }
}
