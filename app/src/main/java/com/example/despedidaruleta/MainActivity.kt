package com.example.despedidaruleta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.despedidaruleta.core.designsystem.theme.DespedidaRuletaTheme
import com.example.despedidaruleta.core.di.AppContainer
import com.example.despedidaruleta.core.navigation.DespedidaRuletaApp
import com.example.despedidaruleta.core.notification.SessionReminderWorker
import com.example.despedidaruleta.feature.events.EventsViewModel

class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialSessionId = intent.getStringExtra(SessionReminderWorker.EXTRA_SESSION_ID)
        val initialRoute = intent.getStringExtra(EventsViewModel.EXTRA_EVENT_ROUTE)
        setContent {
            DespedidaRuletaTheme {
                DespedidaRuletaApp(
                    container = appContainer,
                    initialSessionId = initialSessionId,
                    initialRoute = initialRoute
                )
            }
        }
    }
}
