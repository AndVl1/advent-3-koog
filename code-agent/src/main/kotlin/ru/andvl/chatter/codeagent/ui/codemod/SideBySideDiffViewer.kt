package ru.andvl.chatter.codeagent.ui.codemod

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.codeagent.viewmodel.DiffType
import ru.andvl.chatter.codeagent.viewmodel.FileChangeUi

/**
 * Side-by-side diff viewer
 *
 * Displays diff in side-by-side format (original and modified columns).
 *
 * @param change File change to display
 * @param modifier Modifier for the component
 */
@Composable
fun SideBySideDiffViewer(
    change: FileChangeUi,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Original",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Modified",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // Diff content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(
                    items = change.diffLines,
                    key = { "${it.lineNumber}_${it.type}" }
                ) { diffLine ->
                    SideBySideDiffLine(diffLine = diffLine)
                }
            }
        }
    }
}

/**
 * Single line in side-by-side diff view
 */
@Composable
private fun SideBySideDiffLine(
    diffLine: ru.andvl.chatter.codeagent.viewmodel.DiffLine
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Original side
        DiffSide(
            lineNumber = diffLine.lineNumber,
            content = diffLine.originalContent,
            backgroundColor = when (diffLine.type) {
                DiffType.REMOVED, DiffType.MODIFIED -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                else -> Color.Transparent
            },
            modifier = Modifier.weight(1f)
        )

        // Modified side
        DiffSide(
            lineNumber = diffLine.lineNumber,
            content = diffLine.modifiedContent,
            backgroundColor = when (diffLine.type) {
                DiffType.ADDED, DiffType.MODIFIED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                else -> Color.Transparent
            },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * One side of the side-by-side diff
 */
@Composable
private fun DiffSide(
    lineNumber: Int,
    content: String?,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(backgroundColor)
            .padding(vertical = 2.dp, horizontal = 8.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        if (content != null) {
            // Line number
            Text(
                text = String.format("%4d", lineNumber),
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            // Content
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
