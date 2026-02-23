package ru.andvl.chatter.app.feature.chat.component

import com.arkivanov.decompose.value.Value
import ru.andvl.chatter.app.models.ChatEvent
import ru.andvl.chatter.app.models.ChatUiState

interface ChatComponent {
    val state: Value<ChatUiState>
    fun onEvent(event: ChatEvent)
}
