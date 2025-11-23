package ru.andvl.chatter.codeagent.ui.codemod

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * File scope selector for limiting modification scope
 *
 * Allows user to select specific files or use all files in repository.
 *
 * @param fileScope Selected files for scope (null = all files)
 * @param onFileScopeChange Callback when file scope changes
 * @param enabled Whether the selector is enabled
 */
@Composable
fun ScopeSelector(
    fileScope: List<String>?,
    onFileScopeChange: (List<String>?) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label
        Text(
            text = "File Scope",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Scope display
        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Column {
                        Text(
                            text = if (fileScope == null) "All Files" else "${fileScope.size} files selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (fileScope != null && fileScope.isNotEmpty()) {
                            Text(
                                text = fileScope.take(3).joinToString(", ") { it.substringAfterLast('/') } +
                                        if (fileScope.size > 3) ", ..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Clear button
                if (fileScope != null) {
                    IconButton(
                        onClick = { onFileScopeChange(null) },
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear file scope",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Info text
        Text(
            text = if (fileScope == null)
                "All files in the repository will be considered for modifications"
            else
                "Only selected files will be modified",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
