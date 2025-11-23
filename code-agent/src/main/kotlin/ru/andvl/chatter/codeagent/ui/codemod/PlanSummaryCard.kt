package ru.andvl.chatter.codeagent.ui.codemod

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.codeagent.viewmodel.ChangeType
import ru.andvl.chatter.codeagent.viewmodel.ModificationComplexity
import ru.andvl.chatter.codeagent.viewmodel.ModificationPlanUi

/**
 * Plan summary card
 *
 * Displays summary information about the modification plan.
 *
 * @param plan Modification plan
 * @param selectedCount Number of selected changes
 */
@Composable
fun PlanSummaryCard(
    plan: ModificationPlanUi,
    selectedCount: Int
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Summary text
            Text(
                text = plan.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Complexity badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Complexity:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ComplexityBadge(complexity = plan.complexity)
            }

            // Change type counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                plan.changeTypeCounts.forEach { (type, count) ->
                    ChangeTypeChip(type = type, count = count)
                }
            }

            // Selection info
            if (selectedCount > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "$selectedCount of ${plan.totalChanges} changes selected",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Progress bar (if changes applied)
            if (plan.appliedChanges.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Applied: ${plan.appliedChanges.size} of ${plan.totalChanges} (${plan.progressPercentage}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LinearProgressIndicator(
                        progress = { plan.progressPercentage / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Warnings
            if (plan.warnings.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                plan.warnings.forEach { warning ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )

                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Complexity badge
 */
@Composable
private fun ComplexityBadge(complexity: ModificationComplexity) {
    val (color, text) = when (complexity) {
        ModificationComplexity.LOW -> MaterialTheme.colorScheme.tertiary to "Low"
        ModificationComplexity.MEDIUM -> MaterialTheme.colorScheme.primary to "Medium"
        ModificationComplexity.HIGH -> MaterialTheme.colorScheme.error to "High"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * Change type chip
 */
@Composable
private fun ChangeTypeChip(type: ChangeType, count: Int) {
    val (color, text) = when (type) {
        ChangeType.CREATE -> MaterialTheme.colorScheme.tertiary to "Create"
        ChangeType.MODIFY -> MaterialTheme.colorScheme.primary to "Modify"
        ChangeType.DELETE -> MaterialTheme.colorScheme.error to "Delete"
    }

    AssistChip(
        onClick = {},
        label = {
            Text(
                text = "$text: $count",
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.15f),
            labelColor = color
        )
    )
}
