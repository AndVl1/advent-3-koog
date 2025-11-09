package ru.andvl.chatter.desktop.ui.components

import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generate full markdown report from analysis response
 */
internal fun generateMarkdownReport(
    response: GithubAnalysisResponse,
    userInput: String
): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    return buildString {
        appendLine("# GitHub Repository Analysis Report")
        appendLine()
        appendLine("**Generated on:** $timestamp")
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
                requirements.importantConstraints.forEach { constraint ->
                    appendLine("- $constraint")
                }
                appendLine()
            }

            if (requirements.additionalAdvantages.isNotEmpty()) {
                appendLine("### Additional Advantages")
                requirements.additionalAdvantages.forEach { advantage ->
                    appendLine("- $advantage")
                }
                appendLine()
            }

            if (requirements.attentionPoints.isNotEmpty()) {
                appendLine("### Attention Points")
                requirements.attentionPoints.forEach { point ->
                    appendLine("- $point")
                }
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
            review.generalConditionsReview.fileReference?.let {
                appendLine("**File:** $it")
            }
            review.generalConditionsReview.codeQuote?.let {
                appendLine("**Code:**")
                appendLine("```")
                appendLine(it)
                appendLine("```")
            }
            appendLine()

            if (review.constraintsReview.isNotEmpty()) {
                appendLine("### Constraints Review")
                review.constraintsReview.forEachIndexed { index, comment ->
                    appendLine("${index + 1}. **Comment:** ${comment.comment}")
                    appendLine("   **Type:** ${comment.commentType}")
                    comment.fileReference?.let { appendLine("   **File:** $it") }
                    comment.codeQuote?.let {
                        appendLine("   **Code:**")
                        appendLine("   ```")
                        appendLine(it)
                        appendLine("   ```")
                    }
                    appendLine()
                }
            }

            if (review.advantagesReview.isNotEmpty()) {
                appendLine("### Advantages Review")
                review.advantagesReview.forEachIndexed { index, comment ->
                    appendLine("${index + 1}. **Comment:** ${comment.comment}")
                    appendLine("   **Type:** ${comment.commentType}")
                    comment.fileReference?.let { appendLine("   **File:** $it") }
                    comment.codeQuote?.let {
                        appendLine("   **Code:**")
                        appendLine("   ```")
                        appendLine(it)
                        appendLine("   ```")
                    }
                    appendLine()
                }
            }

            if (review.attentionPointsReview.isNotEmpty()) {
                appendLine("### Attention Points Review")
                review.attentionPointsReview.forEachIndexed { index, comment ->
                    appendLine("${index + 1}. **Comment:** ${comment.comment}")
                    appendLine("   **Type:** ${comment.commentType}")
                    comment.fileReference?.let { appendLine("   **File:** $it") }
                    comment.codeQuote?.let {
                        appendLine("   **Code:**")
                        appendLine("   ```")
                        appendLine(it)
                        appendLine("   ```")
                    }
                    appendLine()
                }
            }
        }

        response.dockerInfo?.let { dockerInfo ->
            appendLine("## Docker Information")
            appendLine()
            appendLine("### Docker Environment")
            appendLine("- **Base Image:** ${dockerInfo.dockerEnv.baseImage}")
            appendLine("- **Build Command:** ${dockerInfo.dockerEnv.buildCommand}")
            appendLine("- **Run Command:** ${dockerInfo.dockerEnv.runCommand}")
            dockerInfo.dockerEnv.port?.let { appendLine("- **Port:** $it") }
            dockerInfo.dockerEnv.additionalNotes?.let { appendLine("- **Notes:** $it") }
            appendLine()

            appendLine("### Build Result")
            appendLine("- **Status:** ${dockerInfo.buildResult.buildStatus}")
            dockerInfo.buildResult.imageSize?.let { appendLine("- **Image Size:** $it") }
            dockerInfo.buildResult.buildDurationSeconds?.let { appendLine("- **Build Duration:** $it seconds") }
            dockerInfo.buildResult.errorMessage?.let { appendLine("- **Error:** $it") }
            appendLine("- **Dockerfile Generated:** ${if (dockerInfo.dockerfileGenerated) "Yes" else "No"}")
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

/**
 * Save markdown report to file
 */
internal fun saveMarkdownReport(
    content: String,
    onResponse: (String) -> Unit
) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "github_analysis_report_$timestamp.md"
        val downloadsDir = File(System.getProperty("user.home"), "Downloads")
        val reportsDir = File(downloadsDir, "ChatterReports")

        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }

        val file = File(reportsDir, fileName)
        file.writeText(content)

        onResponse("Report saved to: ${file.absolutePath}")
    } catch (e: Exception) {
        onResponse("Error saving report: ${e.message}")
    }
}
