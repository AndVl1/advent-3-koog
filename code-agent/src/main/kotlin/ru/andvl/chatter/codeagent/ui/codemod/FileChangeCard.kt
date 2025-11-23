package ru.andvl.chatter.codeagent.ui.codemod

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.codeagent.viewmodel.ChangeStatus
import ru.andvl.chatter.codeagent.viewmodel.ChangeType
import ru.andvl.chatter.codeagent.viewmodel.FileChangeUi

/**
 * File change card
 *
 * Displays a single file change with checkbox and view details button.
 *
 * @param change File change to display
 * @param isSelected Whether this change is selected
 * @param isApplied Whether this change has been applied
 * @param onToggleSelection Callback when checkbox is toggled
 * @param onViewDetails Callback when view details is clicked
 */
@Composable
fun FileChangeCard(
    change: FileChangeUi,
    isSelected: Boolean,
    isApplied: Boolean,
    onToggleSelection: () -> Unit,
    onViewDetails: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isApplied)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox and info
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    enabled = !isApplied
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // File path
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = change.displayPath,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ChangeTypeBadge(type = change.changeType)

                        if (isApplied) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Applied",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Line range and confidence
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = change.lineRangeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Text(
                            text = "Confidence: ${change.confidencePercentage}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    // Reasoning (truncated)
                    Text(
                        text = change.reasoning.take(80) + if (change.reasoning.length > 80) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    // Status badge
                    ChangeStatusBadge(status = change.status)
                }
            }

            // View details button
            IconButton(
                onClick = onViewDetails
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "View details",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Change type badge
 */
@Composable
private fun ChangeTypeBadge(type: ChangeType) {
    val (color, text) = when (type) {
        ChangeType.CREATE -> MaterialTheme.colorScheme.tertiary to "CREATE"
        ChangeType.MODIFY -> MaterialTheme.colorScheme.primary to "MODIFY"
        ChangeType.DELETE -> MaterialTheme.colorScheme.error to "DELETE"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Change status badge
 */
@Composable
private fun ChangeStatusBadge(status: ChangeStatus) {
    val (color, text) = when (status) {
        ChangeStatus.PENDING -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) to "Pending"
        ChangeStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary to "In Progress"
        ChangeStatus.APPLIED -> MaterialTheme.colorScheme.tertiary to "Applied"
        ChangeStatus.FAILED -> MaterialTheme.colorScheme.error to "Failed"
    }

    if (status != ChangeStatus.PENDING) {
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}
