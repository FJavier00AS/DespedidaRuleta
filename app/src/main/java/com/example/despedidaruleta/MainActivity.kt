package com.example.despedidaruleta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.despedidaruleta.core.designsystem.theme.DespedidaRuletaTheme
import com.example.despedidaruleta.core.di.AppContainer
import com.example.despedidaruleta.core.navigation.DespedidaRuletaApp
import com.example.despedidaruleta.core.notification.SessionReminderWorker
import com.example.despedidaruleta.feature.events.EventsViewModel

class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer by lazy { AppContainer(applicationContext) }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* si se deniega, simplemente no se mostraran notificaciones */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
