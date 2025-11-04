package ru.andvl.chatter.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import ru.andvl.chatter.desktop.models.AppState
import ru.andvl.chatter.desktop.models.GithubAnalysisAction
import ru.andvl.chatter.desktop.models.LLMProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generate full markdown report from analysis response
 */
private fun generateMarkdownReport(
    response: ru.andvl.chatter.shared.models.github.GithubAnalysisResponse,
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
private fun saveMarkdownReport(
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

/**
 * Main GitHub analysis screen
 */
@Composable
fun GithubAnalysisScreen(
    state: AppState,
    onAction: (GithubAnalysisAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

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
                    "Analyzing repository... This may take a few minutes.",
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
                    userInput = state.userInput,
                    onClear = { onAction(GithubAnalysisAction.ClearResult) },
                    clipboardManager = clipboardManager,
                    onSaveReport = { content ->
                        saveMarkdownReport(content) { message ->
                            saveMessage = message
                        }
                    }
                )
            }
        }

        // Save Message Display
        saveMessage?.let { message ->
            item("save-message") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.startsWith("Report saved"))
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            color = if (message.startsWith("Report saved"))
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { saveMessage = null }) {
                            Text("Dismiss")
                        }
                    }
                }
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
    userInput: String,
    onClear: () -> Unit,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onSaveReport: (String) -> Unit
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

            HorizontalDivider()

            // TL;DR Section
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
                                clipboardManager.setText(AnnotatedString(response.tldr))
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
                        text = response.tldr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Main Analysis
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
                        clipboardManager.setText(AnnotatedString(response.analysis))
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

            // User Request Analysis (if exists)
            response.userRequestAnalysis?.let { userAnalysis ->
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tool Calls (${response.toolCalls.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = {
                            val toolCallsText = response.toolCalls.joinToString("\n") { "• $it" }
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
