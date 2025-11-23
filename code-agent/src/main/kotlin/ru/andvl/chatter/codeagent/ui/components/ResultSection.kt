package ru.andvl.chatter.codeagent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisResult

/**
 * Result section component for displaying analysis results
 *
 * This is a stateless composable that displays the complete analysis result.
 *
 * @param result Repository analysis result
 * @param fileTreeExpansionState Map of file paths to their expansion state
 * @param onToggleTreeNode Callback when tree node is toggled
 * @param onClearClick Callback when clear button is clicked
 */
@Composable
internal fun ResultSection(
    result: RepositoryAnalysisResult,
    fileTreeExpansionState: Map<String, Boolean>,
    onToggleTreeNode: (String) -> Unit,
    onClearClick: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header with repository name and clear button
            ResultHeader(
                repositoryName = result.repositoryName,
                onClearClick = onClearClick
            )

            HorizontalDivider()

            // Metadata chips
            MetadataChips(
                fileCount = result.fileCount,
                mainLanguages = result.mainLanguages,
                buildTool = result.buildTool
            )

            HorizontalDivider()

            // Summary section
            SummarySection(summary = result.summary)

            HorizontalDivider()

            // Dependencies section (if available)
            if (result.dependencies.isNotEmpty()) {
                DependenciesSection(dependencies = result.dependencies)
                HorizontalDivider()
            }

            // File tree section
            FileTreeView(
                structureTree = result.structureTree,
                expansionState = fileTreeExpansionState,
                onToggleNode = onToggleTreeNode
            )
        }
    }
}

/**
 * Result header with repository name and clear button
 */
@Composable
private fun ResultHeader(
    repositoryName: String,
    onClearClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Text(
                text = repositoryName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        OutlinedButton(onClick = onClearClick) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear")
        }
    }
}

/**
 * Metadata chips showing file count, languages, and build tool
 */
@Composable
private fun MetadataChips(
    fileCount: Int,
    mainLanguages: List<String>,
    buildTool: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File count chip
        AssistChip(
            onClick = { },
            label = { Text("$fileCount files") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        // Languages chips
        mainLanguages.take(3).forEach { language ->
            AssistChip(
                onClick = { },
                label = { Text(language) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        // Build tool chip (if available)
        buildTool?.let {
            AssistChip(
                onClick = { },
                label = { Text(it) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

/**
 * Summary section displaying the repository summary
 */
@Composable
private fun SummarySection(summary: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Summary",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * Dependencies section displaying the list of dependencies
 */
@Composable
private fun DependenciesSection(dependencies: List<String>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Dependencies",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dependencies.forEach { dependency ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = dependency,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
