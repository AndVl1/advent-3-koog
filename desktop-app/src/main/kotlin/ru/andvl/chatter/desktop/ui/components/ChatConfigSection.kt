package ru.andvl.chatter.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.desktop.models.ChatProvider
import ru.andvl.chatter.desktop.models.ChatState
import ru.andvl.chatter.desktop.models.GithubAnalysisAction

/**
 * Configuration section for chat settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConfigSection(
    chatState: ChatState,
    onAction: (GithubAnalysisAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with expand/collapse button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chat Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            // Expandable content
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Provider Selection
                    ChatProviderDropdown(chatState.provider, onAction)

                    // Model Selection Dropdown
                    ChatModelDropdown(
                        provider = chatState.provider,
                        selectedModel = chatState.model,
                        onAction = onAction
                    )

                    // API Key
                    OutlinedTextField(
                        value = chatState.apiKey,
                        onValueChange = { onAction(GithubAnalysisAction.UpdateChatApiKey(it)) },
                        label = { Text("API Key *") },
                        placeholder = { Text("Enter your ${chatState.provider.displayName} API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text(
                                text = "Required for chat to work",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        isError = chatState.error?.contains("API Key", ignoreCase = true) == true
                    )

                    // History Saving Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = chatState.saveHistoryEnabled,
                            onCheckedChange = { onAction(GithubAnalysisAction.ToggleChatHistorySaving(it)) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Save chat history",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Automatically save conversation to ${chatState.historyFilePath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Show minimal info when collapsed
            if (!isExpanded) {
                Text(
                    text = "Provider: ${chatState.provider.displayName} | Model: ${chatState.model.take(30)}${if (chatState.model.length > 30) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatProviderDropdown(
    provider: ChatProvider,
    onAction: (GithubAnalysisAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = provider.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provider") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ChatProvider.entries.forEach { prov ->
                DropdownMenuItem(
                    text = { Text(prov.displayName) },
                    onClick = {
                        onAction(GithubAnalysisAction.SelectChatProvider(prov))
                        onAction(GithubAnalysisAction.UpdateChatModel(prov.defaultModel))
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatModelDropdown(
    provider: ChatProvider,
    selectedModel: String,
    onAction: (GithubAnalysisAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedModel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            provider.availableModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onAction(GithubAnalysisAction.UpdateChatModel(model))
                        expanded = false
                    }
                )
            }
        }
    }
}
