package ru.andvl.chatter.app.ui.components

import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse

internal fun generateMarkdownReport(
    response: GithubAnalysisResponse,
    userInput: String
): String {
    return buildString {
        appendLine("# GitHub Repository Analysis Report")
        appendLine()
        appendLine("## User Request")
        appendLine("```")
        appendLine(userInput)
        appendLine("```")
        appendLine()

        appendLine("## TL;DR")
        appendLine(response.tldr)
        appendLine()

        appendLine("## Detailed Analysis")
        appendLine(response.analysis)
        appendLine()

        response.requirements?.let { requirements ->
            appendLine("## Requirements Analysis")
            appendLine()
            appendLine("### General Conditions")
            appendLine(requirements.generalConditions)
            appendLine()

            if (requirements.importantConstraints.isNotEmpty()) {
                appendLine("### Important Constraints")
                requirements.importantConstraints.forEach { appendLine("- $it") }
                appendLine()
            }

            if (requirements.additionalAdvantages.isNotEmpty()) {
                appendLine("### Additional Advantages")
                requirements.additionalAdvantages.forEach { appendLine("- $it") }
                appendLine()
            }

            if (requirements.attentionPoints.isNotEmpty()) {
                appendLine("### Attention Points")
                requirements.attentionPoints.forEach { appendLine("- $it") }
                appendLine()
            }
        }

        response.userRequestAnalysis?.let { userAnalysis ->
            appendLine("## Additional Analysis")
            appendLine(userAnalysis)
            appendLine()
        }

        response.repositoryReview?.let { review ->
            appendLine("## Repository Review")
            appendLine()
            appendLine("### General Conditions Review")
            appendLine("**Comment:** ${review.generalConditionsReview.comment}")
            appendLine("**Type:** ${review.generalConditionsReview.commentType}")
            appendLine()
        }

        if (response.toolCalls.isNotEmpty()) {
            appendLine("## Tool Calls")
            response.toolCalls.forEachIndexed { index, toolCall ->
                appendLine("${index + 1}. $toolCall")
            }
            appendLine()
        }

        appendLine("---")
        appendLine()
        response.model?.let { appendLine("**Model Used:** $it") }
        response.usage?.let {
            appendLine("**Token Usage:** ${it.totalTokens} total (${it.promptTokens} prompt / ${it.completionTokens} completion)")
        }
    }
}
