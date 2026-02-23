package ru.andvl.chatter.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import ru.andvl.chatter.app.feature.chat.component.ChatComponent

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val chatComponentFactory: (ComponentContext) -> ChatComponent
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<RootComponent.Config>()

    override val childStack: Value<ChildStack<RootComponent.Config, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = RootComponent.Config.serializer(),
            initialConfiguration = RootComponent.Config.Chat,
            handleBackButton = false,
            childFactory = ::createChild
        )

    private fun createChild(
        config: RootComponent.Config,
        context: ComponentContext
    ): RootComponent.Child = when (config) {
        is RootComponent.Config.Chat -> RootComponent.Child.Chat(chatComponentFactory(context))
    }

    override fun navigateTo(config: RootComponent.Config) {
        navigation.bringToFront(config)
    }
}
