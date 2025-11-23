package ru.andvl.chatter.codeagent.ui.codemod

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.codeagent.viewmodel.DiffType
import ru.andvl.chatter.codeagent.viewmodel.FileChangeUi

/**
 * Unified diff viewer
 *
 * Displays diff in unified format (single column with +/- indicators).
 *
 * @param change File change to display
 * @param modifier Modifier for the component
 */
@Composable
fun UnifiedDiffViewer(
    change: FileChangeUi,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
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
                UnifiedDiffLine(diffLine = diffLine)
            }
        }
    }
}

/**
 * Single line in unified diff view
 */
@Composable
private fun UnifiedDiffLine(
    diffLine: ru.andvl.chatter.codeagent.viewmodel.DiffLine
) {
    val (backgroundColor, prefix, content) = when (diffLine.type) {
        DiffType.UNCHANGED -> Triple(
            Color.Transparent,
            " ",
            diffLine.originalContent ?: ""
        )
        DiffType.ADDED -> Triple(
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
            "+",
            diffLine.modifiedContent ?: ""
        )
        DiffType.REMOVED -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
            "-",
            diffLine.originalContent ?: ""
        )
        DiffType.MODIFIED -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            "~",
            diffLine.modifiedContent ?: ""
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 2.dp, horizontal = 8.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        // Line number
        Text(
            text = String.format("%4d", diffLine.lineNumber),
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        // Prefix
        Text(
            text = prefix,
            modifier = Modifier.width(20.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = when (diffLine.type) {
                DiffType.ADDED -> MaterialTheme.colorScheme.tertiary
                DiffType.REMOVED -> MaterialTheme.colorScheme.error
                DiffType.MODIFIED -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            }
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
