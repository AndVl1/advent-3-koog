package ru.andvl.chatter.app.navigation

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import ru.andvl.chatter.app.feature.chat.component.ChatComponent

interface RootComponent {
    val childStack: Value<ChildStack<Config, Child>>

    sealed class Child {
        data class Chat(val component: ChatComponent) : Child()
    }

    @Serializable
    sealed class Config {
        @Serializable data object Chat : Config()
    }

    fun navigateTo(config: Config)
}
