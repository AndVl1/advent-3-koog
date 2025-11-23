package ru.andvl.chatter.codeagent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Input section component for GitHub URL entry and analysis trigger
 *
 * This is a stateless composable that follows Material Design 3 guidelines.
 *
 * @param githubUrl Current GitHub URL value
 * @param isAnalyzing Whether analysis is in progress
 * @param canAnalyze Whether the analyze button should be enabled
 * @param onUrlChange Callback when URL changes
 * @param onAnalyzeClick Callback when analyze button is clicked
 */
@Composable
internal fun InputSection(
    githubUrl: String,
    isAnalyzing: Boolean,
    canAnalyze: Boolean,
    onUrlChange: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "GitHub Repository Analysis",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Enter a GitHub repository URL to analyze its structure, dependencies, and build tools.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = githubUrl,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub Repository URL") },
                placeholder = { Text("https://github.com/owner/repository") },
                singleLine = true,
                enabled = !isAnalyzing,
                supportingText = {
                    Text("Example: https://github.com/JetBrains/compose-multiplatform")
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAnalyzeClick,
                    enabled = canAnalyze,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyze Repository")
                }
            }
        }
    }
}
