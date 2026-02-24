package ru.andvl.chatter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.app.models.AnalysisEvent
import ru.andvl.chatter.app.models.AnalysisUiState
import ru.andvl.chatter.app.models.LLMProvider
import ru.andvl.chatter.app.platform.PlatformCapabilities

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InputSection(
    state: AnalysisUiState,
    onEvent: (AnalysisEvent) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.userInput,
                onValueChange = { onEvent(AnalysisEvent.UpdateUserInput(it)) },
                label = { Text("Task Description") },
                placeholder = { Text("Provide github repository and task. Task may be from google docs") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            LLMProviderDropdown(state.llmProvider, state.availableProviders, onEvent)
            ModelSelectionSection(state, onEvent)

            if (state.llmProvider == LLMProvider.CUSTOM) {
                CustomProviderFields(state, onEvent)
            }

            if (state.llmProvider != LLMProvider.OLLAMA) {
                APIKeyField(state, onEvent)
            }

            GoogleSheetsSection(state, onEvent)
            AdvancedSettingsSection(state, onEvent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LLMProviderDropdown(
    llmProvider: LLMProvider,
    availableProviders: List<LLMProvider>,
    onEvent: (AnalysisEvent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = llmProvider.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("LLM Provider") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableProviders.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        onEvent(AnalysisEvent.SelectLLMProvider(provider))
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSection(state: AnalysisUiState, onEvent: (AnalysisEvent) -> Unit) {
    if (state.llmProvider == LLMProvider.CUSTOM) {
        OutlinedTextField(
            value = state.customModel,
            onValueChange = { onEvent(AnalysisEvent.UpdateCustomModel(it)) },
            label = { Text("Model Name") },
            placeholder = { Text("e.g., gpt-4, claude-3-opus") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    } else {
        var modelExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }) {
            OutlinedTextField(
                value = state.selectedModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                val models = if (state.llmProvider == LLMProvider.OLLAMA) state.ollamaModels
                else state.llmProvider.availableModels

                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onEvent(AnalysisEvent.SelectModel(model))
                            modelExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomProviderFields(state: AnalysisUiState, onEvent: (AnalysisEvent) -> Unit) {
    OutlinedTextField(
        value = state.customBaseUrl,
        onValueChange = { onEvent(AnalysisEvent.UpdateCustomBaseUrl(it)) },
        label = { Text("Base URL") },
        placeholder = { Text("e.g., https://api.openai.com/v1") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    OutlinedTextField(
        value = state.customMaxContextTokens.toString(),
        onValueChange = { value ->
            value.toLongOrNull()?.let { tokens ->
                onEvent(AnalysisEvent.UpdateCustomMaxContextTokens(tokens))
            }
        },
        label = { Text("Max Context Tokens") },
        placeholder = { Text("e.g., 100000") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = {
            Text(text = "Maximum context window size for the main model", style = MaterialTheme.typography.bodySmall)
        }
    )
}

@Composable
private fun APIKeyField(state: AnalysisUiState, onEvent: (AnalysisEvent) -> Unit) {
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = { onEvent(AnalysisEvent.UpdateApiKey(it)) },
        label = { Text("API Key *") },
        placeholder = { Text("Enter your ${state.llmProvider.displayName} API key") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = { Text(text = "Required field", style = MaterialTheme.typography.bodySmall) },
        isError = state.error?.contains("API Key is required", ignoreCase = true) == true
    )
}

@Composable
private fun GoogleSheetsSection(state: AnalysisUiState, onEvent: (AnalysisEvent) -> Unit) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    Text(text = "Google Sheets Integration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.attachGoogleSheets,
            onCheckedChange = { onEvent(AnalysisEvent.ToggleAttachGoogleSheets(it)) }
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = "Attach Google Sheets for results", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Fill Google Sheets with structured analysis data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (state.attachGoogleSheets) {
        OutlinedTextField(
            value = state.googleSheetsUrl,
            onValueChange = { onEvent(AnalysisEvent.UpdateGoogleSheetsUrl(it)) },
            label = { Text("Google Sheets URL *") },
            placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedSettingsSection(state: AnalysisUiState, onEvent: (AnalysisEvent) -> Unit) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    Text(text = "Advanced Settings", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

    if (PlatformCapabilities.supportsDocker) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.forceSkipDocker,
                onCheckedChange = { onEvent(AnalysisEvent.ToggleForceSkipDocker(it)) }
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = "Skip Docker build", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Uncheck to enable Docker image building and analysis",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.useMainModelForFixing,
            onCheckedChange = { onEvent(AnalysisEvent.ToggleUseMainModelForFixing(it)) }
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = "Use main model for fixing", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Uncheck to use a separate faster/cheaper model for JSON parsing fixes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (!state.useMainModelForFixing) {
        FixingModelSelection(state, onEvent)
    }

    if (PlatformCapabilities.supportsEmbeddings) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.enableEmbeddings,
                onCheckedChange = { onEvent(AnalysisEvent.ToggleEnableEmbeddings(it)) }
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = "Generate embeddings", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Create searchable index using Ollama (requires zylonai/multilingual-e5-large)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixingModelSelection(state: AnalysisUiState, onEvent: (AnalysisEvent) -> Unit) {
    if (state.llmProvider == LLMProvider.CUSTOM) {
        OutlinedTextField(
            value = state.fixingModel,
            onValueChange = { onEvent(AnalysisEvent.SelectFixingModel(it)) },
            label = { Text("Fixing Model Name") },
            placeholder = { Text("e.g., gpt-3.5-turbo, claude-haiku") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = state.customFixingMaxContextTokens.toString(),
            onValueChange = { value ->
                value.toLongOrNull()?.let { tokens ->
                    onEvent(AnalysisEvent.UpdateCustomFixingMaxContextTokens(tokens))
                }
            },
            label = { Text("Fixing Model Max Context Tokens") },
            placeholder = { Text("e.g., 20000") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    } else {
        var fixingModelExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = fixingModelExpanded, onExpandedChange = { fixingModelExpanded = it }) {
            OutlinedTextField(
                value = state.fixingModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Fixing Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fixingModelExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = fixingModelExpanded, onDismissRequest = { fixingModelExpanded = false }) {
                val models = if (state.llmProvider == LLMProvider.OLLAMA) state.ollamaModels
                else state.llmProvider.availableModels

                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onEvent(AnalysisEvent.SelectFixingModel(model))
                            fixingModelExpanded = false
                        }
                    )
                }
            }
        }
    }
}
