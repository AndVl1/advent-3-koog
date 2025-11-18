package ru.andvl.chatter.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.desktop.models.AppScreen
import ru.andvl.chatter.desktop.models.AppState
import ru.andvl.chatter.desktop.models.GithubAnalysisAction

/**
 * Main screen with segmented control for navigation
 */
@Composable
fun MainScreen(
    state: AppState,
    onAction: (GithubAnalysisAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Segmented Control для переключения экранов
        SegmentedControl(
            currentScreen = state.currentScreen,
            onScreenSelected = { screen ->
                onAction(GithubAnalysisAction.NavigateToScreen(screen))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        )

        // Контент выбранного экрана
        when (state.currentScreen) {
            AppScreen.GITHUB_ANALYSIS -> {
                GithubAnalysisScreen(
                    state = state,
                    onAction = onAction
                )
            }
            AppScreen.CHAT -> {
                ChatScreen(
                    chatState = state.chatState,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun SegmentedControl(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppScreen.entries.forEach { screen ->
            val isSelected = screen == currentScreen

            FilterChip(
                selected = isSelected,
                onClick = { onScreenSelected(screen) },
                label = {
                    Text(
                        text = when (screen) {
                            AppScreen.GITHUB_ANALYSIS -> "GitHub Analysis"
                            AppScreen.CHAT -> "Chat"
                        }
                    )
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
