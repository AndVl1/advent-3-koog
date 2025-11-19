package ru.andvl.chatter.desktop.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.andvl.chatter.core.model.conversation.PersonalizationConfig

@Composable
fun PersonalizationConfigCard(
    config: PersonalizationConfig,
    onConfigChange: (PersonalizationConfig) -> Unit,
    isConversationStarted: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(!isConversationStarted) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Personalization",
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "AI Personalization",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded }
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            // Collapsed summary
            if (!isExpanded && config.isEnabled()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildCollapsedSummary(config),
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            // Expanded content
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Character Style
                    ConfigTextField(
                        label = "Character Style",
                        value = config.characterStyle,
                        onValueChange = { onConfigChange(config.copy(characterStyle = it)) },
                        placeholder = "e.g., friendly assistant, professional consultant"
                    )

                    // User Context
                    ConfigTextField(
                        label = "About You",
                        value = config.userContext,
                        onValueChange = { onConfigChange(config.copy(userContext = it)) },
                        placeholder = "e.g., Software engineer named Alex",
                        maxLines = 2
                    )

                    // Preferences
                    ConfigTextField(
                        label = "Your Preferences",
                        value = config.preferences,
                        onValueChange = { onConfigChange(config.copy(preferences = it)) },
                        placeholder = "e.g., Prefers concise answers, works with Kotlin",
                        maxLines = 2
                    )

                    // Additional Context
                    ConfigTextField(
                        label = "Additional Context",
                        value = config.additionalContext,
                        onValueChange = { onConfigChange(config.copy(additionalContext = it)) },
                        placeholder = "e.g., Always respond in Russian",
                        maxLines = 2
                    )

                    if (config.isEnabled()) {
                        Divider()
                        Text(
                            text = "✓ Personalization is active",
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                )
            },
            maxLines = maxLines,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colors.primary,
                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f)
            ),
            textStyle = MaterialTheme.typography.body2.copy(fontSize = 13.sp)
        )
    }
}

private fun buildCollapsedSummary(config: PersonalizationConfig): String {
    val parts = mutableListOf<String>()

    if (config.characterStyle != PersonalizationConfig.DEFAULT.characterStyle) {
        parts.add("Style: ${config.characterStyle}")
    }
    if (config.userContext.isNotBlank()) {
        parts.add("User context set")
    }
    if (config.preferences.isNotBlank()) {
        parts.add("Preferences set")
    }
    if (config.additionalContext.isNotBlank()) {
        parts.add("Additional context set")
    }

    return parts.joinToString(" • ")
}
