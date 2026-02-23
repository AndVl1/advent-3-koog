package ru.andvl.chatter.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.andvl.chatter.app.feature.chat.component.ChatComponent
import ru.andvl.chatter.app.ui.ChatScreen

@Composable
fun ChatScreenWrapper(component: ChatComponent) {
    val state by component.state.subscribeAsState()
    ChatScreen(state = state, onEvent = component::onEvent)
}
