package ru.andvl.chatter.codeagent.ui.codeqa

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp

/**
 * Question input area with text field and send button
 *
 * Provides a text field for user to type questions and a send button.
 * Supports Enter key shortcut to send question.
 * This is a stateless composable that follows Material Design 3 principles.
 *
 * @param question Current question text
 * @param enabled Whether input is enabled (disabled during loading)
 * @param onQuestionChange Callback when question text changes
 * @param onSend Callback when send button is clicked or Enter is pressed
 * @param modifier Optional modifier
 */
@Composable
internal fun QuestionInputArea(
    question: String,
    enabled: Boolean,
    onQuestionChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Text field
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                            if (enabled && question.isNotBlank()) {
                                onSend()
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    },
                placeholder = {
                    Text("Ask a question about the code...")
                },
                enabled = enabled,
                singleLine = false,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Send button
            FilledTonalButton(
                onClick = onSend,
                enabled = enabled && question.isNotBlank(),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send")
            }
        }
    }
}
