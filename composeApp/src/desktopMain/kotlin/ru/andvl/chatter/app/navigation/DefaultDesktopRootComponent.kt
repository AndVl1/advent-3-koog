package ru.andvl.chatter.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import ru.andvl.chatter.app.feature.analysis.component.AnalysisComponent
import ru.andvl.chatter.app.feature.chat.component.ChatComponent

class DefaultDesktopRootComponent(
    componentContext: ComponentContext,
    private val analysisComponentFactory: (ComponentContext) -> AnalysisComponent,
    private val chatComponentFactory: (ComponentContext) -> ChatComponent
) : DesktopRootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<DesktopRootComponent.Config>()

    override val childStack: Value<ChildStack<DesktopRootComponent.Config, DesktopRootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = DesktopRootComponent.Config.serializer(),
            initialConfiguration = DesktopRootComponent.Config.Analysis,
            handleBackButton = false,
            childFactory = ::createChild
        )

    private fun createChild(
        config: DesktopRootComponent.Config,
        context: ComponentContext
    ): DesktopRootComponent.Child = when (config) {
        is DesktopRootComponent.Config.Analysis -> DesktopRootComponent.Child.Analysis(analysisComponentFactory(context))
        is DesktopRootComponent.Config.Chat -> DesktopRootComponent.Child.Chat(chatComponentFactory(context))
    }

    override fun navigateTo(config: DesktopRootComponent.Config) {
        navigation.bringToFront(config)
    }
}
