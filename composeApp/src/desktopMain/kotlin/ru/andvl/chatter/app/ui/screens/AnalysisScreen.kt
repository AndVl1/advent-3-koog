package ru.andvl.chatter.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.andvl.chatter.app.feature.analysis.component.AnalysisComponent
import ru.andvl.chatter.app.ui.GithubAnalysisScreen

@Composable
fun AnalysisScreen(component: AnalysisComponent) {
    val state by component.state.subscribeAsState()
    GithubAnalysisScreen(state = state, onEvent = component::onEvent)
}
