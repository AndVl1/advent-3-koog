package ru.andvl.chatter.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ru.andvl.chatter.desktop.ui.MainScreen
import ru.andvl.chatter.desktop.viewmodel.GithubAnalysisViewModel

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1200.dp, 800.dp)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "Chatter - AI Assistant",
        state = windowState
    ) {
        ChatterApp()
    }
}

@Composable
fun ChatterApp() {
    MaterialTheme {
        Surface {
            val viewModel = remember { GithubAnalysisViewModel() }
            val state by viewModel.state.collectAsState()

            MainScreen(
                state = state,
                onAction = viewModel::dispatch
            )
        }
    }
}
