package ru.andvl.chatter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking
import ru.andvl.chatter.cli.api.ChatApiClient
import ru.andvl.chatter.cli.history.ChatHistory
import ru.andvl.chatter.cli.interactive.InteractiveMode
import ru.andvl.chatter.cli.ui.ColorPrinter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChatterCli : CliktCommand(
    name = "chatter-cli"
) {
    private val host by option("-H", "--host")
        .help("Server host")
        .default("localhost")

    private val port by option("-p", "--port")
        .int()
        .help("Server port")
        .default(8081)

    private val message by option("-m", "--message")
        .help("Message to send to AI")

    private val interactive by option("-i", "--interactive")
        .flag()
        .help("Run in interactive mode")


    private val testSse by option("--sse")
        .flag()
        .help("Test Server-Sent Events connection")

    private val github by option("--github")
        .flag()
        .help("Analyze GitHub repository using the message")

    override fun run() {
        runBlocking {
        val client = ChatApiClient()
        val baseUrl = "http://$host:$port"

        try {
            when {
                testSse -> {
                    echo("📡 Testing Server-Sent Events connection...")
                    client.testSseConnection(baseUrl)
                }
                github && message != null -> {
                    analyzeGithubRepository(client, baseUrl, message!!)
                }
                interactive -> {
                    val interactiveMode = InteractiveMode(client, baseUrl)
                    interactiveMode.start()
                }
                message != null -> {
                    val history = ChatHistory()
                    client.sendMessage(baseUrl, message!!, history)
                }
                github && message == null -> {
                    echo("Error: --github flag requires a message (-m)", err = true)
                    echo("Example: ./chatter.sh -m \"Analyze https://github.com/owner/repo\" --github", err = true)
                }
                else -> {
                    echo("Please provide either --message or use --interactive mode", err = true)
                    echo("Use --help for more information", err = true)
                }
            }
        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)
        } finally {
            client.close()
        }
        }
    }

    private suspend fun analyzeGithubRepository(client: ChatApiClient, baseUrl: String, message: String) {
        try {
            // Call the GitHub analysis API with timeout
            val response = client.analyzeGithub(baseUrl, message)

            if (response != null) {
                // Generate filename with timestamp
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                val filename = "github-analysis-$timestamp.md"

                // Create markdown content
                val markdownContent = buildString {
                    appendLine("# GitHub Repository Analysis")
                    appendLine()
                    appendLine("**Generated:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
                    appendLine("**Request:** $message")
                    appendLine()

                    response.model?.let { model ->
                        appendLine("**Model:** $model")
                    }

                    response.usage?.let { usage ->
                        appendLine("**Token Usage:** ${usage.totalTokens} total (${usage.promptTokens} prompt + ${usage.completionTokens} completion)")
                    }

                    if (response.toolCalls.isNotEmpty()) {
                        appendLine("**Tool Calls:** ${response.toolCalls.size}")
                        appendLine("```")
                        response.toolCalls.forEach { appendLine("- $it") }
                        appendLine("```")
                    }

                    // Add structured requirements if present
                    response.requirements?.let { requirements ->
                        appendLine()
                        appendLine("## 📋 Analyzed Requirements")
                        appendLine()
                        appendLine("**General Conditions:** ${requirements.generalConditions}")
                        appendLine()

                        if (requirements.importantConstraints.isNotEmpty()) {
                            appendLine("**Important Constraints:**")
                            requirements.importantConstraints.forEach { constraint ->
                                appendLine("- $constraint")
                            }
                            appendLine()
                        }

                        if (requirements.additionalAdvantages.isNotEmpty()) {
                            appendLine("**Additional Advantages:**")
                            requirements.additionalAdvantages.forEach { advantage ->
                                appendLine("- $advantage")
                            }
                            appendLine()
                        }

                        if (requirements.attentionPoints.isNotEmpty()) {
                            appendLine("**Attention Points:**")
                            requirements.attentionPoints.forEach { point ->
                                appendLine("- $point")
                            }
                            appendLine()
                        }
                    }

                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**TLDR:** ${response.tldr}")

                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine(response.analysis)

                    // Add structured repository review if present
                    response.repositoryReview?.let { review ->
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("## 🔍 Requirements Review")
                        appendLine()

                        appendLine("### General Conditions Review")
                        appendLine("**Status:** ${review.generalConditionsReview.commentType}")
                        appendLine("**Comment:** ${review.generalConditionsReview.comment}")
                        review.generalConditionsReview.fileReference?.let { ref ->
                            appendLine("**Reference:** `$ref`")
                        }
                        review.generalConditionsReview.codeQuote?.let { code ->
                            appendLine("**Code:**")
                            appendLine("```")
                            appendLine(code)
                            appendLine("```")
                        }
                        appendLine()

                        if (review.constraintsReview.isNotEmpty()) {
                            appendLine("### Constraints Review")
                            review.constraintsReview.forEachIndexed { index, constraint ->
                                appendLine("#### Constraint ${index + 1}")
                                appendLine("**Status:** ${constraint.commentType}")
                                appendLine("**Comment:** ${constraint.comment}")
                                constraint.fileReference?.let { ref ->
                                    appendLine("**Reference:** `$ref`")
                                }
                                constraint.codeQuote?.let { code ->
                                    appendLine("**Code:**")
                                    appendLine("```")
                                    appendLine(code)
                                    appendLine("```")
                                }
                                appendLine()
                            }
                        }

                        if (review.advantagesReview.isNotEmpty()) {
                            appendLine("### Advantages Review")
                            review.advantagesReview.forEachIndexed { index, advantage ->
                                appendLine("#### Advantage ${index + 1}")
                                appendLine("**Status:** ${advantage.commentType}")
                                appendLine("**Comment:** ${advantage.comment}")
                                advantage.fileReference?.let { ref ->
                                    appendLine("**Reference:** `$ref`")
                                }
                                advantage.codeQuote?.let { code ->
                                    appendLine("**Code:**")
                                    appendLine("```")
                                    appendLine(code)
                                    appendLine("```")
                                }
                                appendLine()
                            }
                        }

                        if (review.attentionPointsReview.isNotEmpty()) {
                            appendLine("### Attention Points Review")
                            review.attentionPointsReview.forEachIndexed { index, point ->
                                appendLine("#### Point ${index + 1}")
                                appendLine("**Status:** ${point.commentType}")
                                appendLine("**Comment:** ${point.comment}")
                                point.fileReference?.let { ref ->
                                    appendLine("**Reference:** `$ref`")
                                }
                                point.codeQuote?.let { code ->
                                    appendLine("**Code:**")
                                    appendLine("```")
                                    appendLine(code)
                                    appendLine("```")
                                }
                                appendLine()
                            }
                        }
                    }
                }

                // Save to file
                val file = File(filename)
                file.writeText(markdownContent)

                // Print summary to console
                ColorPrinter.printSuccess("✅ GitHub Analysis Complete")
                ColorPrinter.printInfo("📊 TLDR: ${response.tldr}")

                response.requirements?.let { requirements ->
                    ColorPrinter.printInfo("📋 Requirements analyzed: ${requirements.generalConditions}")
                }

                response.repositoryReview?.let { review ->
                    val reviewCounts = mutableMapOf<String, Int>()

                    // Count review statuses
                    listOf(review.generalConditionsReview).plus(review.constraintsReview)
                        .plus(review.advantagesReview).plus(review.attentionPointsReview)
                        .forEach { comment ->
                            reviewCounts[comment.commentType] = reviewCounts.getOrDefault(comment.commentType, 0) + 1
                        }

                    val statusEmoji = mapOf(
                        "OK" to "✅",
                        "PROBLEM" to "❌",
                        "ADVANTAGE" to "⭐"
                    )

                    val statusSummary = reviewCounts.map { (status, count) ->
                        "${statusEmoji[status] ?: "📍"} $count $status"
                    }.joinToString(" | ")

                    ColorPrinter.printInfo("🔍 Requirements review: $statusSummary")
                }

                ColorPrinter.printSuccess("📄 Analysis saved to: $filename")
                ColorPrinter.printInfo("📁 File location: ${file.absolutePath}")

            } else {
                ColorPrinter.printError("❌ Failed to get analysis response")
            }
        } catch (e: Exception) {
            ColorPrinter.printError("❌ GitHub analysis failed: ${e.message}")
        }
    }
}

fun main(args: Array<String>) = ChatterCli().main(args)
