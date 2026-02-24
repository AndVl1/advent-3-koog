package ru.andvl.chatter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.arkivanov.decompose.defaultComponentContext
import ru.andvl.chatter.app.feature.chat.component.DefaultChatComponent
import ru.andvl.chatter.app.feature.chat.repository.ChatRepositoryImpl
import ru.andvl.chatter.app.navigation.DefaultRootComponent
import ru.andvl.chatter.app.platform.FileSaver
import ru.andvl.chatter.app.ui.RootContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)

        FileSaver.init(this)

        val chatRepository = ChatRepositoryImpl()

        val rootComponent = DefaultRootComponent(
            componentContext = defaultComponentContext(),
            chatComponentFactory = { ctx -> DefaultChatComponent(ctx, chatRepository) }
        )

        setContent {
            MaterialTheme {
                Surface {
                    RootContent(rootComponent)
                }
            }
        }
    }
}
