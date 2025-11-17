package ru.andvl.chatter.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.andvl.chatter.desktop.models.ChatMessage
import ru.andvl.chatter.desktop.models.ChatState
import ru.andvl.chatter.desktop.models.GithubAnalysisAction
import ru.andvl.chatter.desktop.models.MessageRole
import ru.andvl.chatter.desktop.ui.components.ChatConfigSection

/**
 * Chat screen with text and voice input
 */
@Composable
fun ChatScreen(
    chatState: ChatState,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chat",
                style = MaterialTheme.typography.headlineLarge
            )

            // Clear chat button
            if (chatState.messages.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onAction(GithubAnalysisAction.ClearChat) }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Chat")
                }
            }
        }

        HorizontalDivider()

        // Chat Configuration
        ChatConfigSection(
            chatState = chatState,
            onAction = onAction
        )

        // Messages list
        val listState = rememberLazyListState()

        LaunchedEffect(chatState.messages.size) {
            if (chatState.messages.isNotEmpty()) {
                listState.animateScrollToItem(chatState.messages.size - 1)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chatState.messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Начните диалог, введите сообщение или запишите голосовое сообщение",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(chatState.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    chatState = chatState,
                    onAction = onAction
                )
            }

            if (chatState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("Думаю...")
                            }
                        }
                    }
                }
            }
        }

        // Error display
        chatState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
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
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onAction(GithubAnalysisAction.ClearChatError) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Input area
        ChatInputArea(
            chatState = chatState,
            onAction = onAction
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    chatState: ChatState,
    onAction: (GithubAnalysisAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val isPlaying = chatState.playingMessageId == message.id

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voice indicator with play button
                if (message.isVoice) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Play/Pause button
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    onAction(GithubAnalysisAction.PauseVoiceMessage(message.id))
                                } else {
                                    onAction(GithubAnalysisAction.PlayVoiceMessage(message.id))
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isUser) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        Text(
                            text = "Голосовое сообщение",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // Playback progress
                        if (isPlaying && chatState.playbackDuration > 0) {
                            Text(
                                text = "${chatState.playbackPosition / 1000}s / ${chatState.playbackDuration / 1000}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                        }
                    }
                }

                // Message content
                Text(
                    text = message.content,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // Timestamp
                Text(
                    text = message.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                        .let { "${it.hour}:${it.minute.toString().padStart(2, '0')}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatInputArea(
    chatState: ChatState,
    onAction: (GithubAnalysisAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Text input
        OutlinedTextField(
            value = chatState.currentInput,
            onValueChange = { onAction(GithubAnalysisAction.UpdateChatInput(it)) },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Введите сообщение...") },
            enabled = !chatState.isLoading && !chatState.isRecording,
            maxLines = 4,
            trailingIcon = {
                if (chatState.currentInput.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onAction(GithubAnalysisAction.SendChatMessage(chatState.currentInput))
                        },
                        enabled = !chatState.isLoading
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        )

        // Voice recording button
        Card(
            onClick = {
                if (chatState.isRecording) {
                    onAction(GithubAnalysisAction.StopVoiceRecording)
                    onAction(GithubAnalysisAction.SendVoiceMessage)
                } else {
                    onAction(GithubAnalysisAction.StartVoiceRecording)
                }
            },
            colors = CardDefaults.cardColors(
                containerColor = if (chatState.isRecording) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ),
            enabled = !chatState.isLoading
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (chatState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (chatState.isRecording) "Stop recording" else "Start recording",
                    tint = if (chatState.isRecording) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }
    }

    // Recording indicator with audio level visualization
    if (chatState.isRecording) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Запись... (${chatState.recordingDuration / 1000}s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Audio level visualization
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(24.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Draw bars representing audio level
                repeat(20) { index ->
                    val barHeight = if (index < (chatState.audioLevel * 20).toInt()) {
                        1f
                    } else {
                        0.2f
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(barHeight)
                            .background(
                                color = if (index < (chatState.audioLevel * 20).toInt()) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}
