package ru.andvl.chatter.codeagent.ui.codemod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.andvl.chatter.codeagent.ui.components.ErrorCard
import ru.andvl.chatter.codeagent.viewmodel.CodeModificationAction
import ru.andvl.chatter.codeagent.viewmodel.CodeModificationViewModel

/**
 * Main screen for Code Modification feature
 *
 * This is a stateless composable that displays the complete code modification UI.
 * It follows the pattern: Screen(state, onEvent)
 *
 * @param viewModel ViewModel managing state and actions
 */
@Composable
fun CodeModificationScreen(
    viewModel: CodeModificationViewModel
) {
    val state by viewModel.state.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            CodeModificationHeader(
                repositoryName = state.repositoryName,
                hasSession = state.hasSession
            )

            // Error display
            if (state.hasError) {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Text(
                            text = state.error!!,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.dispatch(CodeModificationAction.DismissError) }
                        ) {
                            androidx.compose.material3.Text(
                                "✕",
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }

            // Content
            if (!state.hasSession) {
                SessionEmptyState()
            } else {
                CodeModificationContent(
                    state = state,
                    onAction = { action -> viewModel.dispatch(action) }
                )
            }
        }
    }
}

/**
 * Content section of Code Modification screen
 *
 * Displays the main content when a session is active.
 */
@Composable
private fun CodeModificationContent(
    state: ru.andvl.chatter.codeagent.viewmodel.CodeModificationUiState,
    onAction: (CodeModificationAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // Left panel - Input and Plan
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Request input section
            RequestInputSection(
                modificationRequest = state.modificationRequest,
                fileScope = state.fileScope,
                canGeneratePlan = state.canGeneratePlan,
                isLoadingPlan = state.isLoadingPlan,
                onRequestChange = { onAction(CodeModificationAction.UpdateRequest(it)) },
                onFileScopeChange = { onAction(CodeModificationAction.UpdateFileScope(it)) },
                onGeneratePlan = { onAction(CodeModificationAction.GeneratePlan) }
            )

            // Loading indicator or plan section
            if (state.isLoadingPlan) {
                ProgressSection(message = "Generating modification plan...")
            } else if (state.modificationPlan != null) {
                ModificationPlanSection(
                    plan = state.modificationPlan,
                    selectedChanges = state.selectedChanges,
                    onToggleChange = { onAction(CodeModificationAction.ToggleChangeSelection(it)) },
                    onSelectAll = { onAction(CodeModificationAction.SelectAllChanges) },
                    onDeselectAll = { onAction(CodeModificationAction.DeselectAllChanges) },
                    onViewDetails = { onAction(CodeModificationAction.ViewChangeDetails(it)) },
                    onApplyChanges = { onAction(CodeModificationAction.ApplySelectedChanges) },
                    canApplyChanges = state.canApplyChanges,
                    isApplyingChanges = state.isApplyingChanges
                )
            }

            // Git commit section
            if (state.modificationPlan?.appliedChanges?.isNotEmpty() == true) {
                GitCommitSection(
                    commitMessage = state.commitMessage,
                    createBranch = state.createBranch,
                    branchName = state.branchName,
                    canCommit = state.canCommit,
                    isCommitting = state.isCommitting,
                    commitSha = state.modificationPlan.commitSha,
                    onCommitMessageChange = { onAction(CodeModificationAction.UpdateCommitMessage(it)) },
                    onToggleCreateBranch = { onAction(CodeModificationAction.ToggleCreateBranch) },
                    onBranchNameChange = { onAction(CodeModificationAction.UpdateBranchName(it)) },
                    onCommit = { onAction(CodeModificationAction.CommitChanges) }
                )
            }
        }

        // Right panel - Change Details
        if (state.selectedChangeForDetails != null) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                ChangeDetailsPanel(
                    change = state.selectedChange!!,
                    diffViewMode = state.diffViewMode,
                    onClose = { onAction(CodeModificationAction.ViewChangeDetails(null)) },
                    onSwitchMode = { onAction(CodeModificationAction.SwitchDiffViewMode(it)) }
                )
            }
        }
    }
}
