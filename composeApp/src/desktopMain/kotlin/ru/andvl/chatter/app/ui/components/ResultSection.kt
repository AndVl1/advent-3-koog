package ru.andvl.chatter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import ru.andvl.chatter.shared.models.github.GithubAnalysisResponse

@Composable
internal fun ResultSection(
    response: GithubAnalysisResponse,
    userInput: String,
    onClear: () -> Unit,
    clipboardManager: ClipboardManager,
    onSaveReport: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Analysis Result", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onSaveReport(generateMarkdownReport(response, userInput)) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save full report", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(generateMarkdownReport(response, userInput))) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy full report", tint = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }

            HorizontalDivider()

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "TL;DR", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(response.tldr)) },
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
                    Text(text = response.tldr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Detailed Analysis", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(response.analysis)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy analysis", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Markdown(content = response.analysis)

            response.requirements?.let { requirements ->
                HorizontalDivider()
                Text(text = "Requirements Analysis", style = MaterialTheme.typography.titleMedium)
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "General Conditions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(requirements.generalConditions, style = MaterialTheme.typography.bodyMedium)

                        if (requirements.importantConstraints.isNotEmpty()) {
                            Text(text = "Important Constraints", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            requirements.importantConstraints.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        }

                        if (requirements.additionalAdvantages.isNotEmpty()) {
                            Text(text = "Additional Advantages", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            requirements.additionalAdvantages.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        }

                        if (requirements.attentionPoints.isNotEmpty()) {
                            Text(text = "Attention Points", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            requirements.attentionPoints.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }

            response.userRequestAnalysis?.let { userAnalysis ->
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Additional Analysis", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(userAnalysis)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy additional analysis", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) { Markdown(content = userAnalysis) }
                }
            }

            response.repositoryReview?.let { review ->
                HorizontalDivider()
                Text(text = "Repository Review", style = MaterialTheme.typography.titleMedium)
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewCommentCard(review.generalConditionsReview)

                        if (review.constraintsReview.isNotEmpty()) {
                            Text(text = "Constraints Review", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            review.constraintsReview.forEach { ReviewCommentCard(it) }
                        }

                        if (review.advantagesReview.isNotEmpty()) {
                            Text(text = "Advantages Review", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            review.advantagesReview.forEach { ReviewCommentCard(it) }
                        }

                        if (review.attentionPointsReview.isNotEmpty()) {
                            Text(text = "Attention Points Review", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            review.attentionPointsReview.forEach { ReviewCommentCard(it) }
                        }
                    }
                }
            }

            response.dockerInfo?.let { dockerInfo ->
                HorizontalDivider()
                Text(text = "Docker Information", style = MaterialTheme.typography.titleMedium)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (dockerInfo.buildResult.buildStatus == "SUCCESS")
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Docker Environment", style = MaterialTheme.typography.titleSmall)
                        Text("Base Image: ${dockerInfo.dockerEnv.baseImage}")
                        Text("Build Command: ${dockerInfo.dockerEnv.buildCommand}")
                        Text("Run Command: ${dockerInfo.dockerEnv.runCommand}")
                        dockerInfo.dockerEnv.port?.let { Text("Port: $it") }
                        dockerInfo.dockerEnv.additionalNotes?.let { Text("Notes: $it") }
                        HorizontalDivider()
                        Text("Build Result: ${dockerInfo.buildResult.buildStatus}", style = MaterialTheme.typography.titleSmall)
                        dockerInfo.buildResult.imageSize?.let { Text("Image Size: $it") }
                        dockerInfo.buildResult.buildDurationSeconds?.let { Text("Build Duration: $it seconds") }
                        dockerInfo.buildResult.errorMessage?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
                        if (dockerInfo.dockerfileGenerated) {
                            Text("Dockerfile Generated", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (response.toolCalls.isNotEmpty()) {
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Tool Calls (${response.toolCalls.size})", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(response.toolCalls.joinToString("\n") { "• $it" })) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy tool calls", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        response.toolCalls.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                response.model?.let { model ->
                    Text(text = "Model: $model", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                response.usage?.let { usage ->
                    Text(
                        text = "Tokens: ${usage.totalTokens} (${usage.promptTokens} / ${usage.completionTokens})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
