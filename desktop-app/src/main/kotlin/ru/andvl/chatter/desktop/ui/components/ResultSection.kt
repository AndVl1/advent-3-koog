package ru.andvl.chatter.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse

/**
 * Result section displaying analysis results
 */
@Composable
internal fun ResultSection(
    response: GithubAnalysisResponse,
    userInput: String,
    onClear: () -> Unit,
    clipboardManager: ClipboardManager,
    onSaveReport: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            ResultHeader(
                response = response,
                userInput = userInput,
                onClear = onClear,
                clipboardManager = clipboardManager,
                onSaveReport = onSaveReport
            )

            HorizontalDivider()

            // TL;DR Section
            TLDRSection(response.tldr, clipboardManager)

            // Main Analysis
            AnalysisSection(response.analysis, clipboardManager)

            // Requirements Analysis
            response.requirements?.let { requirements ->
                RequirementsSection(requirements)
            }

            // User Request Analysis (if exists)
            response.userRequestAnalysis?.let { userAnalysis ->
                UserRequestAnalysisSection(userAnalysis, clipboardManager)
            }

            // Repository Review
            response.repositoryReview?.let { review ->
                RepositoryReviewSection(review)
            }

            // Docker Info
            response.dockerInfo?.let { dockerInfo ->
                DockerInfoSection(dockerInfo)
            }

            // Tool Calls
            if (response.toolCalls.isNotEmpty()) {
                ToolCallsSection(response.toolCalls, clipboardManager)
            }

            // Metadata (Model & Usage)
            MetadataSection(response)
        }
    }
}

@Composable
private fun ResultHeader(
    response: GithubAnalysisResponse,
    userInput: String,
    onClear: () -> Unit,
    clipboardManager: ClipboardManager,
    onSaveReport: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Analysis Result",
            style = MaterialTheme.typography.titleLarge
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Save Report Button
            IconButton(
                onClick = {
                    onSaveReport(generateMarkdownReport(response, userInput))
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = "Save full report",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Copy Full Report Button
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(generateMarkdownReport(response, userInput)))
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy full report",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun TLDRSection(tldr: String, clipboardManager: ClipboardManager) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TL;DR",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(tldr))
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy TL;DR",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tldr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun AnalysisSection(analysis: String, clipboardManager: ClipboardManager) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Detailed Analysis",
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(analysis))
            }
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy analysis",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Markdown(content = analysis)
}

@Composable
private fun RequirementsSection(requirements: ru.andvl.chatter.shared.models.github.RequirementsAnalysisDto) {
    HorizontalDivider()
    Text(
        text = "Requirements Analysis",
        style = MaterialTheme.typography.titleMedium
    )

    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "General Conditions",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(requirements.generalConditions, style = MaterialTheme.typography.bodyMedium)

            if (requirements.importantConstraints.isNotEmpty()) {
                Text(
                    text = "Important Constraints",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                requirements.importantConstraints.forEach { constraint ->
                    Text("• $constraint", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (requirements.additionalAdvantages.isNotEmpty()) {
                Text(
                    text = "Additional Advantages",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                requirements.additionalAdvantages.forEach { advantage ->
                    Text("• $advantage", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (requirements.attentionPoints.isNotEmpty()) {
                Text(
                    text = "Attention Points",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                requirements.attentionPoints.forEach { point ->
                    Text("• $point", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun UserRequestAnalysisSection(userAnalysis: String, clipboardManager: ClipboardManager) {
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Additional Analysis",
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(userAnalysis))
            }
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy additional analysis",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Markdown(content = userAnalysis)
        }
    }
}

@Composable
private fun RepositoryReviewSection(review: ru.andvl.chatter.shared.models.github.RepositoryReviewDto) {
    HorizontalDivider()
    Text(
        text = "Repository Review",
        style = MaterialTheme.typography.titleMedium
    )

    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // General Conditions Review
            ReviewCommentCard(review.generalConditionsReview)

            // Constraints Review
            if (review.constraintsReview.isNotEmpty()) {
                Text(
                    text = "Constraints Review",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                review.constraintsReview.forEach { comment ->
                    ReviewCommentCard(comment)
                }
            }

            // Advantages Review
            if (review.advantagesReview.isNotEmpty()) {
                Text(
                    text = "Advantages Review",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                review.advantagesReview.forEach { comment ->
                    ReviewCommentCard(comment)
                }
            }

            // Attention Points Review
            if (review.attentionPointsReview.isNotEmpty()) {
                Text(
                    text = "Attention Points Review",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                review.attentionPointsReview.forEach { comment ->
                    ReviewCommentCard(comment)
                }
            }
        }
    }
}

@Composable
private fun DockerInfoSection(dockerInfo: ru.andvl.chatter.shared.models.github.DockerInfoDto) {
    HorizontalDivider()
    Text(
        text = "Docker Information",
        style = MaterialTheme.typography.titleMedium
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (dockerInfo.buildResult.buildStatus == "SUCCESS")
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Docker Environment",
                style = MaterialTheme.typography.titleSmall
            )
            Text("Base Image: ${dockerInfo.dockerEnv.baseImage}")
            Text("Build Command: ${dockerInfo.dockerEnv.buildCommand}")
            Text("Run Command: ${dockerInfo.dockerEnv.runCommand}")
            dockerInfo.dockerEnv.port?.let { Text("Port: $it") }
            dockerInfo.dockerEnv.additionalNotes?.let { Text("Notes: $it") }

            HorizontalDivider()

            Text(
                text = "Build Result: ${dockerInfo.buildResult.buildStatus}",
                style = MaterialTheme.typography.titleSmall
            )
            dockerInfo.buildResult.imageSize?.let { Text("Image Size: $it") }
            dockerInfo.buildResult.buildDurationSeconds?.let { Text("Build Duration: $it seconds") }
            dockerInfo.buildResult.errorMessage?.let {
                Text("Error: $it", color = MaterialTheme.colorScheme.error)
            }
            if (dockerInfo.dockerfileGenerated) {
                Text("✓ Dockerfile Generated", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ToolCallsSection(toolCalls: List<String>, clipboardManager: ClipboardManager) {
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Tool Calls (${toolCalls.size})",
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(
            onClick = {
                val toolCallsText = toolCalls.joinToString("\n") { "• $it" }
                clipboardManager.setText(AnnotatedString(toolCallsText))
            }
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy tool calls",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            toolCalls.forEach { toolCall ->
                Text("• $toolCall", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MetadataSection(response: GithubAnalysisResponse) {
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        response.model?.let { model ->
            Text(
                text = "Model: $model",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        response.usage?.let { usage ->
            Text(
                text = "Tokens: ${usage.totalTokens} (${usage.promptTokens}↑ / ${usage.completionTokens}↓)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
