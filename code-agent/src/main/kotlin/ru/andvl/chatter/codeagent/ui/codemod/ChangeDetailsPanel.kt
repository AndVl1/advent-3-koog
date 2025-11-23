package ru.andvl.chatter.codeagent.ui.codemod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.codeagent.viewmodel.DiffViewMode
import ru.andvl.chatter.codeagent.viewmodel.FileChangeUi

/**
 * Change details panel
 *
 * Displays detailed view of a file change with diff viewer.
 *
 * @param change File change to display
 * @param diffViewMode Current diff view mode
 * @param onClose Callback when close button is clicked
 * @param onSwitchMode Callback when diff view mode is switched
 */
@Composable
fun ChangeDetailsPanel(
    change: FileChangeUi,
    diffViewMode: DiffViewMode,
    onClose: () -> Unit,
    onSwitchMode: (DiffViewMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Change Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = change.displayPath,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // AI Reasoning Card
        AiReasoningCard(
            reasoning = change.reasoning,
            confidence = change.confidencePercentage
        )

        // Diff view mode selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Diff View",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = diffViewMode == DiffViewMode.UNIFIED,
                    onClick = { onSwitchMode(DiffViewMode.UNIFIED) },
                    label = { Text("Unified") }
                )

                FilterChip(
                    selected = diffViewMode == DiffViewMode.SIDE_BY_SIDE,
                    onClick = { onSwitchMode(DiffViewMode.SIDE_BY_SIDE) },
                    label = { Text("Side by Side") }
                )
            }
        }

        HorizontalDivider()

        // Diff viewer
        when (diffViewMode) {
            DiffViewMode.UNIFIED -> UnifiedDiffViewer(
                change = change,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            DiffViewMode.SIDE_BY_SIDE -> SideBySideDiffViewer(
                change = change,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
