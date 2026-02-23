package ru.andvl.chatter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.andvl.chatter.app.models.ChatEvent
import ru.andvl.chatter.app.models.ChatMessage
import ru.andvl.chatter.app.models.ChatUiState
import ru.andvl.chatter.app.models.MessageRole
import ru.andvl.chatter.app.platform.PlatformCapabilities
import ru.andvl.chatter.app.ui.chat.PersonalizationConfigCard
import ru.andvl.chatter.app.ui.components.ChatConfigSection

@Composable
fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // RootContent already handles navigationBars via navigationBarsPadding().
    // imePadding() in CMP doesn't subtract consumed navBars from parent, causing double gap.
    // Solution: manually compute only the keyboard-above-navBar portion.
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBarBottom = WindowInsets.navigationBars.getBottom(density)
    val keyboardPadding = with(density) { (imeBottom - navBarBottom).coerceAtLeast(0).toDp() }

    LaunchedEffect(state.messages.size, state.isLoading) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = keyboardPadding)
    ) {
        val hasMessages = state.messages.isNotEmpty()
        val isKeyboardVisible = imeBottom > navBarBottom
        val lazyColumnFillSpace = hasMessages || isKeyboardVisible

        LazyColumn(
            modifier = if (lazyColumnFillSpace) Modifier.weight(1f).fillMaxWidth()
                       else Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Chat", style = MaterialTheme.typography.headlineLarge)
                    if (state.messages.isNotEmpty()) {
                        OutlinedButton(onClick = { onEvent(ChatEvent.ClearChat) }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Chat")
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                PersonalizationConfigCard(
                    config = state.personalizationConfig,
                    onConfigChange = { onEvent(ChatEvent.UpdatePersonalization(it)) },
                    isConversationStarted = state.messages.isNotEmpty()
                )
            }

            item {
                ChatConfigSection(state = state, onEvent = onEvent)
            }

            // Empty state
            if (state.messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start a conversation by typing a message below",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Messages
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message = message, chatState = state, onEvent = onEvent)
            }

            // Loading indicator
            if (state.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Thinking...")
                            }
                        }
                    }
                }
            }
        }

        if (!lazyColumnFillSpace) {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Error fixed at bottom
        state.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onEvent(ChatEvent.ClearError) }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        ChatInputArea(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    chatState: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val isPlaying = chatState.playingMessageId == message.id

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (message.isVoice) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) onEvent(ChatEvent.PauseVoiceMessage(message.id))
                                else onEvent(ChatEvent.PlayVoiceMessage(message.id))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Voice message",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )

                        if (isPlaying && chatState.playbackDuration > 0) {
                            Text(
                                text = "${chatState.playbackPosition / 1000}s / ${chatState.playbackDuration / 1000}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Text(
                    text = message.content,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = message.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                        .let { "${it.hour}:${it.minute.toString().padStart(2, '0')}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ChatInputArea(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = state.currentInput,
            onValueChange = { onEvent(ChatEvent.UpdateInput(it)) },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Enter message...") },
            enabled = !state.isLoading && !state.isRecording,
            maxLines = 4,
            trailingIcon = {
                if (state.currentInput.isNotBlank()) {
                    IconButton(
                        onClick = { onEvent(ChatEvent.SendMessage(state.currentInput)) },
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        )

        if (PlatformCapabilities.supportsVoiceInput) {
            Card(
                onClick = {
                    if (state.isRecording) onEvent(ChatEvent.StopVoiceRecording)
                    else onEvent(ChatEvent.StartVoiceRecording)
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isRecording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
                enabled = !state.isLoading
            ) {
                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (state.isRecording) "Stop recording" else "Start recording",
                        tint = if (state.isRecording) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }

    if (PlatformCapabilities.supportsVoiceInput && state.isRecording) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    text = "Recording... (${state.recordingDuration / 1000}s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(0.5f).height(24.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(20) { index ->
                    val barHeight = if (index < (state.audioLevel * 20).toInt()) 1f else 0.2f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(barHeight)
                            .background(
                                color = if (index < (state.audioLevel * 20).toInt())
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}
