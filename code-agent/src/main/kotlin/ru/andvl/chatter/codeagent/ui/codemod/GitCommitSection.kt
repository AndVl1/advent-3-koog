package ru.andvl.chatter.codeagent.ui.codemod

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Git commit section
 *
 * Allows user to configure and commit applied changes.
 *
 * @param commitMessage Current commit message
 * @param createBranch Whether to create a new branch
 * @param branchName Custom branch name (null = auto-generate)
 * @param canCommit Whether commit button is enabled
 * @param isCommitting Whether commit is in progress
 * @param commitSha Git commit SHA if committed (null if not yet committed)
 * @param onCommitMessageChange Callback when commit message changes
 * @param onToggleCreateBranch Callback when create branch is toggled
 * @param onBranchNameChange Callback when branch name changes
 * @param onCommit Callback when commit button is clicked
 */
@Composable
fun GitCommitSection(
    commitMessage: String,
    createBranch: Boolean,
    branchName: String?,
    canCommit: Boolean,
    isCommitting: Boolean,
    commitSha: String?,
    onCommitMessageChange: (String) -> Unit,
    onToggleCreateBranch: () -> Unit,
    onBranchNameChange: (String?) -> Unit,
    onCommit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (commitSha != null)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section title
            Text(
                text = "Git Commit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Success message if already committed
            if (commitSha != null) {
                CommitSuccessCard(
                    commitSha = commitSha,
                    branchName = branchName
                )
            } else {
                // Commit message input
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = onCommitMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Commit Message") },
                    placeholder = { Text("Describe the changes made") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isCommitting
                )

                // Create branch checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = createBranch,
                        onCheckedChange = { onToggleCreateBranch() },
                        enabled = !isCommitting
                    )

                    Text(
                        text = "Create new branch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Branch name input (if creating branch)
                if (createBranch) {
                    OutlinedTextField(
                        value = branchName ?: "",
                        onValueChange = { onBranchNameChange(it.ifBlank { null }) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Branch Name (optional)") },
                        placeholder = { Text("Auto-generated if empty") },
                        supportingText = { Text("Leave empty to auto-generate based on timestamp") },
                        singleLine = true,
                        enabled = !isCommitting
                    )
                }

                // Commit button
                Button(
                    onClick = onCommit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canCommit
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCommitting) "Committing..." else "Commit Changes",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * Commit success card
 *
 * Displays success message with commit SHA and branch name.
 */
@Composable
private fun CommitSuccessCard(
    commitSha: String,
    branchName: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Changes Committed Successfully",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Commit:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )

                    Text(
                        text = commitSha,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (branchName != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Branch:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )

                        Text(
                            text = branchName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
