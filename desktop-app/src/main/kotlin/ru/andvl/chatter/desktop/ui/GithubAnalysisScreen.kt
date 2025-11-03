package ru.andvl.chatter.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import ru.andvl.chatter.desktop.models.AppState
import ru.andvl.chatter.desktop.models.GithubAnalysisAction
import ru.andvl.chatter.desktop.models.LLMProvider

/**
 * Main GitHub analysis screen
 */
@Composable
fun GithubAnalysisScreen(
    state: AppState,
    onAction: (GithubAnalysisAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item("header") {
            Text(
                text = "GitHub Repository Analyzer",
                style = MaterialTheme.typography.headlineLarge
            )
        }

        item("divider") {
            Divider()
        }

        item("input") {
            // Input Section
            InputSection(
                state = state,
                onAction = onAction
            )
        }

        item("analyze-button") {
            // Analysis Button
            Button(
                onClick = { onAction(GithubAnalysisAction.StartAnalysis) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isLoading) "Analyzing..." else "Analyze Repository")
            }
        }

        // Loading Indicator
        if (state.isLoading) {
            item("loading") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Analyzing repository... This may take a minute.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Error Display
        state.error?.let { error ->
            item("error") {
                ErrorCard(
                    message = error,
                    onDismiss = { onAction(GithubAnalysisAction.ClearError) }
                )
            }
        }

        // Result Display
        state.analysisResult?.let { result ->
            item("result") {
                ResultSection(
                    response = result,
                    onClear = { onAction(GithubAnalysisAction.ClearResult) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputSection(
    state: AppState,
    onAction: (GithubAnalysisAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Single Input Field
            OutlinedTextField(
                value = state.userInput,
                onValueChange = { onAction(GithubAnalysisAction.UpdateUserInput(it)) },
                label = { Text("Task Description") },
                placeholder = { Text("Provide github repository and task. Task may be from google docs") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            // LLM Provider Selection
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = state.llmProvider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("LLM Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    LLMProvider.entries.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName) },
                            onClick = {
                                onAction(GithubAnalysisAction.SelectLLMProvider(provider))
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Model Selection
            if (state.llmProvider == LLMProvider.CUSTOM) {
                // Custom Model - Free Text Input
                OutlinedTextField(
                    value = state.customModel,
                    onValueChange = { onAction(GithubAnalysisAction.UpdateCustomModel(it)) },
                    label = { Text("Model Name") },
                    placeholder = { Text("e.g., gpt-4, claude-3-opus") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                // Standard Providers - Model Dropdown
                var modelExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        state.llmProvider.availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    onAction(GithubAnalysisAction.SelectModel(model))
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Custom Base URL (only for custom provider)
            if (state.llmProvider == LLMProvider.CUSTOM) {
                OutlinedTextField(
                    value = state.customBaseUrl,
                    onValueChange = { onAction(GithubAnalysisAction.UpdateCustomBaseUrl(it)) },
                    label = { Text("Base URL") },
                    placeholder = { Text("e.g., https://api.openai.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // API Key Input (Required)
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { onAction(GithubAnalysisAction.UpdateApiKey(it)) },
                label = { Text("API Key *") },
                placeholder = { Text("Enter your ${state.llmProvider.displayName} API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text(
                        text = "Required field",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                isError = state.error?.contains("API Key is required", ignoreCase = true) == true
            )

            // Advanced Settings Divider
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Advanced Settings",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            // Fixing Model Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.useMainModelForFixing,
                    onCheckedChange = { onAction(GithubAnalysisAction.ToggleUseMainModelForFixing(it)) }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Use main model for fixing",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Uncheck to use a separate faster/cheaper model for JSON parsing fixes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Fixing Model Selection (shown only when checkbox is unchecked)
            if (!state.useMainModelForFixing) {
                if (state.llmProvider == LLMProvider.CUSTOM) {
                    // Custom Fixing Model - Free Text Input
                    OutlinedTextField(
                        value = state.fixingModel,
                        onValueChange = { onAction(GithubAnalysisAction.SelectFixingModel(it)) },
                        label = { Text("Fixing Model Name") },
                        placeholder = { Text("e.g., gpt-3.5-turbo, claude-haiku") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    // Standard Providers - Fixing Model Dropdown
                    var fixingModelExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = fixingModelExpanded,
                        onExpandedChange = { fixingModelExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = state.fixingModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Fixing Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fixingModelExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = fixingModelExpanded,
                            onDismissRequest = { fixingModelExpanded = false }
                        ) {
                            state.llmProvider.availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        onAction(GithubAnalysisAction.SelectFixingModel(model))
                                        fixingModelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun ResultSection(
    response: ru.andvl.chatter.shared.models.github.GithubAnalysisResponse,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Analysis Result",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }

            HorizontalDivider()

            // TL;DR Section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "TL;DR",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = response.tldr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Main Analysis
            Text(
                text = "Detailed Analysis",
                style = MaterialTheme.typography.titleMedium
            )
            Markdown(content = response.analysis)

            // Requirements Analysis
            response.requirements?.let { requirements ->
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

            // Repository Review
            response.repositoryReview?.let { review ->
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

            // Docker Info
            response.dockerInfo?.let { dockerInfo ->
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

            // Tool Calls
            if (response.toolCalls.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Tool Calls (${response.toolCalls.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        response.toolCalls.forEach { toolCall ->
                            Text("• $toolCall", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Metadata (Model & Usage)
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
    }
}

@Composable
private fun ReviewCommentCard(comment: ru.andvl.chatter.shared.models.github.RequirementReviewCommentDto) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (comment.commentType) {
                "PROBLEM" -> MaterialTheme.colorScheme.errorContainer
                "ADVANTAGE" -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = comment.commentType,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (comment.commentType) {
                        "PROBLEM" -> MaterialTheme.colorScheme.onErrorContainer
                        "ADVANTAGE" -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                comment.fileReference?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = comment.comment,
                style = MaterialTheme.typography.bodySmall
            )
            comment.codeQuote?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
