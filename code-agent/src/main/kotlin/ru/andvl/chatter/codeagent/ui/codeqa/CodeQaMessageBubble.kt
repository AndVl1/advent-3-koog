package ru.andvl.chatter.codeagent.ui.codeqa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.codeagent.viewmodel.CodeQaMessageUi
import ru.andvl.chatter.codeagent.viewmodel.CodeQaRole
import java.text.SimpleDateFormat
import java.util.*

/**
 * Message bubble component for Code QA conversation
 *
 * Displays a single message from user or assistant with appropriate styling.
 * User messages are aligned right with primary color, assistant messages left with surface color.
 * This is a stateless composable that follows Material Design 3 principles.
 *
 * @param message Message to display
 * @param onToggleReference Callback when code reference expand/collapse is clicked
 * @param onCopyCode Callback when code copy button is clicked
 * @param modifier Optional modifier
 */
@Composable
internal fun CodeQaMessageBubble(
    message: CodeQaMessageUi,
    onToggleReference: (referenceIndex: Int) -> Unit,
    onCopyCode: (codeSnippet: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == CodeQaRole.USER

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Message bubble
            Box(
                modifier = Modifier
                    .background(
                        color = if (isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Code references (only for assistant messages)
            if (!isUser && message.hasCodeReferences) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    message.codeReferences.forEachIndexed { index, reference ->
                        CodeReferenceCard(
                            reference = reference,
                            isExpanded = message.isExpanded,
                            onToggleExpand = { onToggleReference(index) },
                            onCopy = { onCopyCode(reference.codeSnippet) }
                        )
                    }
                }
            }

            // Timestamp
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(if (isUser) Alignment.End else Alignment.Start)
            )
        }
    }
}

/**
 * Format timestamp to readable time string
 */
private fun formatTimestamp(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
