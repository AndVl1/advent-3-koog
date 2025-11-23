package ru.andvl.chatter.codeagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.codeagent.ui.components.*
import ru.andvl.chatter.codeagent.viewmodel.RepositoryAnalysisAction
import ru.andvl.chatter.codeagent.viewmodel.RepositoryAnalysisViewModel

/**
 * Main screen for Repository Analysis feature
 *
 * This screen follows Clean Architecture and MVVM principles:
 * - Observes state from ViewModel via StateFlow
 * - Dispatches actions to ViewModel
 * - Stateless composables for all UI components
 * - Unidirectional Data Flow (UDF)
 *
 * Component hierarchy:
 * ```
 * RepositoryAnalysisScreen
 * ├── Header (title, description)
 * ├── InputSection (URL field + Analyze button)
 * ├── ProgressSection (conditional, when analyzing)
 * ├── ErrorSection (conditional, when error)
 * └── ResultSection (conditional, when result available)
 *     ├── ResultHeader
 *     ├── MetadataChips
 *     ├── SummarySection
 *     ├── DependenciesSection
 *     └── FileTreeView
 * ```
 *
 * @param viewModel ViewModel for state management and business logic
 */
@Composable
fun RepositoryAnalysisScreen(
    viewModel: RepositoryAnalysisViewModel = RepositoryAnalysisViewModel(),
    modifier: Modifier = Modifier
) {
    // Observe state from ViewModel
    val state by viewModel.state.collectAsState()

    // Main layout
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Header()

            // Input section (always visible)
            InputSection(
                githubUrl = state.githubUrl,
                isAnalyzing = state.isAnalyzing,
                canAnalyze = state.canAnalyze,
                onUrlChange = { url ->
                    viewModel.dispatch(RepositoryAnalysisAction.UpdateGitHubUrl(url))
                },
                onAnalyzeClick = {
                    viewModel.dispatch(RepositoryAnalysisAction.StartAnalysis)
                }
            )

            // Progress section (conditional)
            if (state.isAnalyzing) {
                ProgressSection(progressMessage = state.progressMessage)
            }

            // Error section (conditional)
            if (state.hasError) {
                ErrorCard(errorMessage = state.error ?: "Unknown error")
            }

            // Result section (conditional)
            if (state.hasResult) {
                ResultSection(
                    result = state.analysisResult!!,
                    fileTreeExpansionState = state.fileTreeExpansionState,
                    onToggleTreeNode = { nodePath ->
                        viewModel.dispatch(RepositoryAnalysisAction.ToggleTreeNode(nodePath))
                    },
                    onClearClick = {
                        viewModel.dispatch(RepositoryAnalysisAction.ClearResult)
                    }
                )
            }
        }
    }
}

/**
 * Header component with app title and description
 */
@Composable
private fun Header() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Code Agent",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "AI-powered repository analyzer for understanding codebases",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
