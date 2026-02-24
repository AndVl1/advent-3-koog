package ru.andvl.chatter.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.pause
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.start
import com.arkivanov.essenty.lifecycle.stop
import ru.andvl.chatter.app.feature.analysis.component.DefaultAnalysisComponent
import ru.andvl.chatter.app.feature.analysis.repository.AnalysisRepositoryImpl
import ru.andvl.chatter.app.feature.chat.component.DefaultChatComponent
import ru.andvl.chatter.app.feature.chat.repository.ChatRepositoryImpl
import ru.andvl.chatter.app.navigation.DefaultDesktopRootComponent
import ru.andvl.chatter.app.ui.RootContent

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))

    val lifecycle = LifecycleRegistry()
    val analysisRepository = AnalysisRepositoryImpl()
    val chatRepository = ChatRepositoryImpl()

    val rootComponent = DefaultDesktopRootComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle),
        analysisComponentFactory = { ctx -> DefaultAnalysisComponent(ctx, analysisRepository) },
        chatComponentFactory = { ctx -> DefaultChatComponent(ctx, chatRepository) }
    )

    lifecycle.create()
    lifecycle.start()
    lifecycle.resume()

    Window(
        onCloseRequest = {
            lifecycle.pause()
            lifecycle.stop()
            lifecycle.destroy()
            exitApplication()
        },
        title = "Chatter - AI Assistant",
        state = windowState
    ) {
        MaterialTheme {
            Surface {
                RootContent(rootComponent)
            }
        }
    }
}
