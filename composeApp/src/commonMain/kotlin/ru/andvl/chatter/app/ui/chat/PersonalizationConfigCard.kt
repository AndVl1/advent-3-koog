package ru.andvl.chatter.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "AI Personalization",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            if (!isExpanded && config.isEnabled()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildCollapsedSummary(config),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    ConfigTextField(
                        label = "Character Style",
                        value = config.characterStyle,
                        onValueChange = { onConfigChange(config.copy(characterStyle = it)) },
                        placeholder = "e.g., friendly assistant, professional consultant"
                    )

                    ConfigTextField(
                        label = "About You",
                        value = config.userContext,
                        onValueChange = { onConfigChange(config.copy(userContext = it)) },
                        placeholder = "e.g., Software engineer named Alex",
                        maxLines = 2
                    )

                    ConfigTextField(
                        label = "Your Preferences",
                        value = config.preferences,
                        onValueChange = { onConfigChange(config.copy(preferences = it)) },
                        placeholder = "e.g., Prefers concise answers, works with Kotlin",
                        maxLines = 2
                    )

                    ConfigTextField(
                        label = "Additional Context",
                        value = config.additionalContext,
                        onValueChange = { onConfigChange(config.copy(additionalContext = it)) },
                        placeholder = "e.g., Always respond in Russian",
                        maxLines = 2
                    )

                    if (config.isEnabled()) {
                        HorizontalDivider()
                        Text(
                            text = "Personalization is active",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            },
            maxLines = maxLines,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
        )
    }
}

private fun buildCollapsedSummary(config: PersonalizationConfig): String {
    val parts = mutableListOf<String>()
    if (config.characterStyle != PersonalizationConfig.DEFAULT.characterStyle) {
        parts.add("Style: ${config.characterStyle}")
    }
    if (config.userContext.isNotBlank()) parts.add("User context set")
    if (config.preferences.isNotBlank()) parts.add("Preferences set")
    if (config.additionalContext.isNotBlank()) parts.add("Additional context set")
    return parts.joinToString(" • ")
}
