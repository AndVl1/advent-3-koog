package ru.andvl.chatter.codeagent.ui.codemod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.codeagent.viewmodel.ModificationPlanUi

/**
 * Modification plan section
 *
 * Displays the generated modification plan with checkboxes for selection.
 *
 * @param plan Modification plan to display
 * @param selectedChanges Set of selected change IDs
 * @param onToggleChange Callback when change selection is toggled
 * @param onSelectAll Callback when select all is clicked
 * @param onDeselectAll Callback when deselect all is clicked
 * @param onViewDetails Callback when view details is clicked
 * @param onApplyChanges Callback when apply changes is clicked
 * @param canApplyChanges Whether apply button is enabled
 * @param isApplyingChanges Whether changes are being applied
 */
@Composable
fun ModificationPlanSection(
    plan: ModificationPlanUi,
    selectedChanges: Set<String>,
    onToggleChange: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onViewDetails: (String) -> Unit,
    onApplyChanges: () -> Unit,
    canApplyChanges: Boolean,
    isApplyingChanges: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section title
            Text(
                text = "Modification Plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Plan summary
            PlanSummaryCard(
                plan = plan,
                selectedCount = plan.selectedCount(selectedChanges)
            )

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select All")
                }

                OutlinedButton(
                    onClick = onDeselectAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Deselect All")
                }
            }

            // Divider
            HorizontalDivider()

            // File changes list
            Text(
                text = "Proposed Changes (${plan.changes.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = plan.changes,
                    key = { it.id }
                ) { change ->
                    FileChangeCard(
                        change = change,
                        isSelected = change.id in selectedChanges,
                        isApplied = change.id in plan.appliedChanges,
                        onToggleSelection = { onToggleChange(change.id) },
                        onViewDetails = { onViewDetails(change.id) }
                    )
                }
            }

            // Apply changes button
            Button(
                onClick = onApplyChanges,
                modifier = Modifier.fillMaxWidth(),
                enabled = canApplyChanges
            ) {
                Icon(
                    imageVector = if (plan.appliedChanges.isEmpty()) Icons.Default.PlayArrow else Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isApplyingChanges -> "Applying Changes..."
                        plan.appliedChanges.isEmpty() -> "Apply Selected Changes"
                        else -> "Reapply Selected Changes"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
