package ru.andvl.chatter.codeagent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.codeagent.ui.RepositoryAnalysisScreen
import ru.andvl.chatter.codeagent.ui.codeqa.CodeQaScreen
import ru.andvl.chatter.codeagent.ui.codemod.CodeModificationScreen
import ru.andvl.chatter.codeagent.viewmodel.CodeQaAction
import ru.andvl.chatter.codeagent.viewmodel.CodeQaViewModel
import ru.andvl.chatter.codeagent.viewmodel.CodeModificationAction
import ru.andvl.chatter.codeagent.viewmodel.CodeModificationViewModel
import ru.andvl.chatter.codeagent.viewmodel.RepositoryAnalysisViewModel

/**
 * Main entry point for Code Agent desktop application
 *
 * This application provides:
 * - Repository Analysis: AI-powered analysis of GitHub repositories
 * - Code QA: Interactive Q&A about analyzed code
 * - Code Modifications: AI-assisted code modification with diff viewer
 *
 * It follows Clean Architecture and MVVM principles with Jetpack Compose for Desktop.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Code Agent",
        state = rememberWindowState(width = 1400.dp, height = 900.dp)
    ) {
        MaterialTheme {
            CodeAgentApp()
        }
    }
}

/**
 * Main application composable with tab navigation
 */
@Composable
private fun CodeAgentApp() {
    // Create ViewModels and ensure proper cleanup
    val repositoryAnalysisViewModel = remember { RepositoryAnalysisViewModel() }
    val codeQaViewModel = remember { CodeQaViewModel() }
    val codeModificationViewModel = remember { CodeModificationViewModel() }

    // Track current tab
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Observe repository analysis state for session initialization
    val repoState by repositoryAnalysisViewModel.state.collectAsState()

    // When analysis completes, initialize Code QA and Code Modification sessions
    LaunchedEffect(repoState.analysisResult) {
        repoState.analysisResult?.let { result ->
            codeQaViewModel.dispatch(
                CodeQaAction.InitializeSession(
                    sessionId = result.repositoryPath,
                    repositoryName = result.repositoryName
                )
            )
            codeModificationViewModel.dispatch(
                CodeModificationAction.InitializeSession(
                    sessionId = result.repositoryPath,
                    repositoryName = result.repositoryName
                )
            )
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            repositoryAnalysisViewModel.onCleared()
            codeQaViewModel.onCleared()
            codeModificationViewModel.onCleared()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab navigation
        androidx.compose.material3.PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Repository Analysis") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Code QA") }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                text = { Text("Code Modifications") }
            )
        }

        // Display selected screen
        when (selectedTabIndex) {
            0 -> RepositoryAnalysisScreen(viewModel = repositoryAnalysisViewModel)
            1 -> CodeQaScreen(viewModel = codeQaViewModel)
            2 -> CodeModificationScreen(viewModel = codeModificationViewModel)
        }
    }
}
