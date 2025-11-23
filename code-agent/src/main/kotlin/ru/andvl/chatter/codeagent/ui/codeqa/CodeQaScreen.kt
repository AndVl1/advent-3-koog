package ru.andvl.chatter.codeagent.ui.codeqa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.andvl.chatter.codeagent.ui.components.ErrorCard
import ru.andvl.chatter.codeagent.viewmodel.CodeQaAction
import ru.andvl.chatter.codeagent.viewmodel.CodeQaUiState
import ru.andvl.chatter.codeagent.viewmodel.CodeQaViewModel

/**
 * Main screen for Code QA feature
 *
 * This screen displays a conversation interface where users can ask questions about code.
 * It follows MVVM pattern:
 * - Observes state from ViewModel via StateFlow
 * - Sends actions to ViewModel via dispatch()
 * - All composables are stateless
 *
 * Layout:
 * - Header with repository name and clear button
 * - Message list (scrollable)
 * - Thinking indicator (when loading)
 * - Error card (when error)
 * - Question input area (fixed at bottom)
 *
 * @param viewModel ViewModel managing state and actions
 * @param modifier Optional modifier
 */
@Composable
fun CodeQaScreen(
    viewModel: CodeQaViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (state.hasSession) {
            // Active session - show conversation UI
            CodeQaContent(
                state = state,
                onAction = { action ->
                    // Handle copy action with clipboard manager
                    if (action is CodeQaAction.CopyCodeSnippet) {
                        clipboardManager.setText(AnnotatedString(action.codeSnippet))
                    }
                    viewModel.dispatch(action)
                }
            )
        } else {
            // No session - show empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                SessionStatusCard(
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

/**
 * Main content layout for active Code QA session
 *
 * @param state Current UI state
 * @param onAction Action dispatcher
 * @param modifier Optional modifier
 */
@Composable
private fun CodeQaContent(
    state: CodeQaUiState,
    onAction: (CodeQaAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header
        CodeQaHeader(
            repositoryName = state.repositoryName,
            onClearClick = { onAction(CodeQaAction.ClearConversation) }
        )

        // Messages list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.hasMessages || state.isLoading) {
                MessageList(
                    state = state,
                    onAction = onAction
                )
            } else {
                // Empty conversation state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = "Ask your first question about the code!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Error card (if error)
        if (state.hasError) {
            ErrorCard(
                errorMessage = state.error!!,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Question input area
        QuestionInputArea(
            question = state.currentQuestion,
            enabled = !state.isLoading,
            onQuestionChange = { onAction(CodeQaAction.UpdateQuestion(it)) },
            onSend = { onAction(CodeQaAction.SendQuestion) }
        )
    }
}

/**
 * Scrollable message list with auto-scroll to bottom
 *
 * @param state Current UI state
 * @param onAction Action dispatcher
 */
@Composable
private fun MessageList(
    state: CodeQaUiState,
    onAction: (CodeQaAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(state.messages.size)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Messages
        items(
            items = state.messages,
            key = { it.id } // Stable key for performance
        ) { message ->
            CodeQaMessageBubble(
                message = message,
                onToggleReference = { referenceIndex ->
                    onAction(
                        CodeQaAction.ToggleCodeReference(
                            messageId = message.id,
                            referenceIndex = referenceIndex
                        )
                    )
                },
                onCopyCode = { codeSnippet ->
                    onAction(CodeQaAction.CopyCodeSnippet(codeSnippet))
                }
            )
        }

        // Thinking indicator (when loading)
        if (state.isLoading) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    ThinkingIndicator()
                }
            }
        }
    }
}
