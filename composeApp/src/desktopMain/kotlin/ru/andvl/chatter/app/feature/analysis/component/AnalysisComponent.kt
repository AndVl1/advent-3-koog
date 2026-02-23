package ru.andvl.chatter.app.feature.analysis.component

import com.arkivanov.decompose.value.Value
import ru.andvl.chatter.app.models.AnalysisEvent
import ru.andvl.chatter.app.models.AnalysisUiState

interface AnalysisComponent {
    val state: Value<AnalysisUiState>
    fun onEvent(event: AnalysisEvent)
}
