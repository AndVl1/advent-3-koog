package ru.andvl.chatter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.andvl.chatter.app.navigation.DesktopRootComponent
import ru.andvl.chatter.app.ui.screens.AnalysisScreen
import ru.andvl.chatter.app.ui.screens.ChatScreenWrapper

@Composable
fun RootContent(component: DesktopRootComponent) {
    val childStack by component.childStack.subscribeAsState()
    val activeConfig = childStack.active.configuration

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeConfig is DesktopRootComponent.Config.Analysis,
                onClick = { component.navigateTo(DesktopRootComponent.Config.Analysis) },
                label = { Text("GitHub Analysis") },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            FilterChip(
                selected = activeConfig is DesktopRootComponent.Config.Chat,
                onClick = { component.navigateTo(DesktopRootComponent.Config.Chat) },
                label = { Text("Chat") },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        when (val instance = childStack.active.instance) {
            is DesktopRootComponent.Child.Analysis -> AnalysisScreen(instance.component)
            is DesktopRootComponent.Child.Chat -> ChatScreenWrapper(instance.component)
        }
    }
}
