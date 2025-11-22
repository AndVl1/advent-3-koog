package ru.andvl.chatter.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.desktop.models.AppState
import ru.andvl.chatter.desktop.models.GithubAnalysisAction
import ru.andvl.chatter.desktop.models.LLMProvider

/**
 * Input section with all configuration fields
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InputSection(
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
            // Task Description Input
            TaskDescriptionField(state.userInput, onAction)

            // LLM Provider Selection
            LLMProviderDropdown(state.llmProvider, state.availableProviders, onAction)

            // Model Selection
            ModelSelectionSection(state, onAction)

            // Custom Provider Fields
            if (state.llmProvider == LLMProvider.CUSTOM) {
                CustomProviderFields(state, onAction)
            }

            // API Key Input (hidden for Ollama, but value is preserved)
            if (state.llmProvider != LLMProvider.OLLAMA) {
                APIKeyField(state, onAction)
            }

            // Google Sheets Integration
            GoogleSheetsSection(state, onAction)

            // Advanced Settings
            AdvancedSettingsSection(state, onAction)
        }
    }
}

@Composable
private fun TaskDescriptionField(
    userInput: String,
    onAction: (GithubAnalysisAction) -> Unit
) {
    OutlinedTextField(
        value = userInput,
        onValueChange = { onAction(GithubAnalysisAction.UpdateUserInput(it)) },
        label = { Text("Task Description") },
        placeholder = { Text("Provide github repository and task. Task may be from google docs") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 6
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LLMProviderDropdown(
    llmProvider: LLMProvider,
    availableProviders: List<LLMProvider>,
    onAction: (GithubAnalysisAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = llmProvider.displayName,
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
            availableProviders.forEach { provider ->
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSection(
    state: AppState,
    onAction: (GithubAnalysisAction) -> Unit
) {
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
                // For Ollama, use dynamically loaded models
                val models = if (state.llmProvider == LLMProvider.OLLAMA) {
                    state.ollamaModels
                } else {
                    state.llmProvider.availableModels
                }

                models.forEach { model ->
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
}

@Composable
private fun CustomProviderFields(
    state: AppState,
    onAction: (GithubAnalysisAction) -> Unit
) {
    OutlinedTextField(
        value = state.customBaseUrl,
        onValueChange = { onAction(GithubAnalysisAction.UpdateCustomBaseUrl(it)) },
        label = { Text("Base URL") },
        placeholder = { Text("e.g., https://api.openai.com/v1") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    OutlinedTextField(
        value = state.customMaxContextTokens.toString(),
        onValueChange = { value ->
            value.toLongOrNull()?.let { tokens ->
                onAction(GithubAnalysisAction.UpdateCustomMaxContextTokens(tokens))
            }
        },
        label = { Text("Max Context Tokens") },
        placeholder = { Text("e.g., 100000") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = {
            Text(
                text = "Maximum context window size for the main model",
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}

@Composable
private fun APIKeyField(
    state: AppState,
    onAction: (GithubAnalysisAction) -> Unit
) {
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
}

@Composable
private fun GoogleSheetsSection(
    state: AppState,
    onAction: (GithubAnalysisAction) -> Unit
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    Text(
        text = "Google Sheets Integration",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = state.attachGoogleSheets,
            onCheckedChange = { onAction(GithubAnalysisAction.ToggleAttachGoogleSheets(it)) }
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "Attach Google Sheets for results",
                style = MaterialTheme.typography.bodyMedium
            )
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
            onValueChange = { onAction(GithubAnalysisAction.UpdateGoogleSheetsUrl(it)) },
            label = { Text("Google Sheets URL *") },
            placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text(
                    text = "Required when Google Sheets integration is enabled",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        )
    }
}

@Composable
private fun AdvancedSettingsSection(
    state: AppState,
    onAction: (GithubAnalysisAction) -> Unit
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    Text(
        text = "Advanced Settings",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )

    // Force Skip Docker Checkbox
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = state.forceSkipDocker,
            onCheckedChange = { onAction(GithubAnalysisAction.ToggleForceSkipDocker(it)) }
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "Skip Docker build",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Uncheck to enable Docker image building and analysis",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

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

    // Fixing Model Selection
    if (!state.useMainModelForFixing) {
        FixingModelSelection(state, onAction)
    }

    // Embeddings Checkbox
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = state.enableEmbeddings,
            onCheckedChange = { onAction(GithubAnalysisAction.ToggleEnableEmbeddings(it)) }
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "Generate embeddings",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Create searchable index using Ollama (requires zylonai/multilingual-e5-large)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixingModelSelection(
    state: AppState,
    onAction: (GithubAnalysisAction) -> Unit
) {
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

        // Custom Fixing Max Context Tokens
        OutlinedTextField(
            value = state.customFixingMaxContextTokens.toString(),
            onValueChange = { value ->
                value.toLongOrNull()?.let { tokens ->
                    onAction(GithubAnalysisAction.UpdateCustomFixingMaxContextTokens(tokens))
                }
            },
            label = { Text("Fixing Model Max Context Tokens") },
            placeholder = { Text("e.g., 20000") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text(
                    text = "Maximum context window size for the fixing model",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
                // For Ollama, use dynamically loaded models
                val models = if (state.llmProvider == LLMProvider.OLLAMA) {
                    state.ollamaModels
                } else {
                    state.llmProvider.availableModels
                }

                models.forEach { model ->
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
