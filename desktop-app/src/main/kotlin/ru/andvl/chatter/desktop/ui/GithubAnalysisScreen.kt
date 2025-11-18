package ru.andvl.chatter.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.desktop.models.AppState
import ru.andvl.chatter.desktop.models.GithubAnalysisAction
import ru.andvl.chatter.desktop.ui.components.*

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
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(24.dp)
    ) {
        // Header
        item("header") {
            Text(
                text = "GitHub Repository Analyzer",
                style = MaterialTheme.typography.headlineLarge
            )
        }

        item("divider") {
            HorizontalDivider()
        }

        item("input") {
            InputSection(
                state = state,
                onAction = onAction
            )
        }

        item("analyze-button") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onAction(GithubAnalysisAction.StartAnalysis) },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isLoading) "Analyzing..." else "Analyze Repository")
                }

                AnimatedVisibility(state.isLoading) {
                    Button(
                        onClick = { onAction(GithubAnalysisAction.CancelAnalysis) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(0.3f)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }

        // Analysis Progress with Events
        if (state.isLoading) {
            item("progress") {
                AnalysisProgressView(
                    currentEvent = state.currentEvent,
                    progress = state.analysisProgress,
                    recentEvents = state.recentEvents,
                    currentStep = state.currentStep,
                    totalSteps = state.totalSteps,
                    currentStepName = state.currentStepName
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
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
