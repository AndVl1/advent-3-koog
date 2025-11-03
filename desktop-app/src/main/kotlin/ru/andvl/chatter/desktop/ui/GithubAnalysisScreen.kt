package ru.andvl.chatter.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "GitHub Repository Analyzer",
            style = MaterialTheme.typography.headlineLarge
        )

        Divider()

        // Input Section
        InputSection(
            state = state,
            onAction = onAction
        )

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

        // Loading Indicator
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "Analyzing repository... This may take a minute.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Error Display
        state.error?.let { error ->
            ErrorCard(
                message = error,
                onDismiss = { onAction(GithubAnalysisAction.ClearError) }
            )
        }

        // Result Display
        state.analysisResult?.let { result ->
            ResultSection(
                response = result.analysis,
                onClear = { onAction(GithubAnalysisAction.ClearResult) }
            )
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
            // GitHub URL Input
            OutlinedTextField(
                value = state.githubUrl,
                onValueChange = { onAction(GithubAnalysisAction.UpdateGithubUrl(it)) },
                label = { Text("GitHub Repository URL") },
                placeholder = { Text("https://github.com/owner/repo or owner/repo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // User Request Input
            OutlinedTextField(
                value = state.userRequest,
                onValueChange = { onAction(GithubAnalysisAction.UpdateUserRequest(it)) },
                label = { Text("Analysis Request (Optional)") },
                placeholder = { Text("Analyze the code quality and architecture") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
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

            // API Key Input
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { onAction(GithubAnalysisAction.UpdateApiKey(it)) },
                label = { Text("API Key") },
                placeholder = { Text("Enter your ${state.llmProvider.displayName} API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "Tip: Set OPENROUTER_API_KEY environment variable to avoid entering API key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun ColumnScope.ResultSection(
    response: String,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
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
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Markdown content with scrolling
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Markdown(
                    content = response
                )
            }
        }
    }
}
