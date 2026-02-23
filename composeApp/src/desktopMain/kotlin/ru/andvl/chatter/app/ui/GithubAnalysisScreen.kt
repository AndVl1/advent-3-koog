package ru.andvl.chatter.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.app.models.AnalysisEvent
import ru.andvl.chatter.app.models.AnalysisUiState
import ru.andvl.chatter.app.ui.components.AnalysisProgressView
import ru.andvl.chatter.app.ui.components.ErrorCard
import ru.andvl.chatter.app.ui.components.InputSection
import ru.andvl.chatter.app.ui.components.ResultSection

@Composable
fun GithubAnalysisScreen(
    state: AnalysisUiState,
    onEvent: (AnalysisEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(24.dp)
    ) {
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
            InputSection(state = state, onEvent = onEvent)
        }

        item("analyze-button") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onEvent(AnalysisEvent.StartAnalysis) },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isLoading) "Analyzing..." else "Analyze Repository")
                }

                AnimatedVisibility(state.isLoading) {
                    Button(
                        onClick = { onEvent(AnalysisEvent.CancelAnalysis) },
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

        if (state.isLoading) {
            item("progress") {
                AnalysisProgressView(
                    currentEventText = state.currentEventText,
                    progress = state.analysisProgress,
                    recentEventTexts = state.recentEvents,
                    currentStep = state.currentStep,
                    totalSteps = state.totalSteps,
                    currentStepName = state.currentStepName
                )
            }
        }

        state.error?.let { error ->
            item("error") {
                ErrorCard(
                    message = error,
                    onDismiss = { onEvent(AnalysisEvent.ClearError) }
                )
            }
        }

        state.analysisResult?.let { result ->
            item("result") {
                ResultSection(
                    response = result,
                    userInput = state.userInput,
                    onClear = { onEvent(AnalysisEvent.ClearResult) },
                    clipboardManager = clipboardManager,
                    onSaveReport = { content -> onEvent(AnalysisEvent.SaveReport(content)) }
                )
            }
        }

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
