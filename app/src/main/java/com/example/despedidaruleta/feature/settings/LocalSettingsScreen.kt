package com.example.despedidaruleta.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.despedidaruleta.core.designsystem.component.MessageBanner
import com.example.despedidaruleta.core.designsystem.component.SectionTitle
import com.example.despedidaruleta.core.designsystem.component.VegasBackground
import com.example.despedidaruleta.core.designsystem.component.VegasCard
import com.example.despedidaruleta.core.designsystem.component.VegasSecondaryButton
import com.example.despedidaruleta.core.designsystem.theme.VegasColors

@Composable
fun LocalSettingsScreen(
    uiState: LocalSettingsUiState,
    sessionId: String,
    onSetThisSessionActive: (Boolean) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onHapticChanged: (Boolean) -> Unit,
    onVisualEffectsChanged: (Boolean) -> Unit,
    onQuietStartChanged: (Int) -> Unit,
    onQuietEndChanged: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onNotificationsChanged(granted)
    }
    fun requestOrToggleNotifications(enabled: Boolean) {
        if (!enabled) {
            onNotificationsChanged(false)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onNotificationsChanged(true)
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) onNotificationsChanged(true) else notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val settings = uiState.settings
    VegasBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VegasSecondaryButton(text = "Volver", onClick = onBack, modifier = Modifier.fillMaxWidth(0.36f))
            SectionTitle(eyebrow = "Local", title = "Ajustes del dispositivo")
            uiState.infoMessage?.let { MessageBanner(message = it, isError = false) }
            VegasCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsSwitchRow(
                        title = "Usar esta sesion para avisos",
                        subtitle = if (settings.activeSessionId == sessionId) "Activa" else "No activa",
                        checked = settings.activeSessionId == sessionId,
                        onCheckedChange = onSetThisSessionActive
                    )
                    SettingsSwitchRow(
                        title = "Notificaciones locales",
                        subtitle = "WorkManager usa el minimo Android: 15 minutos.",
                        checked = settings.notificationsEnabled,
                        onCheckedChange = ::requestOrToggleNotifications
                    )
                    SettingsSwitchRow(
                        title = "Sonido de ruleta",
                        subtitle = "Tono corto al iniciar un giro.",
                        checked = settings.soundEnabled,
                        onCheckedChange = onSoundChanged
                    )
                    SettingsSwitchRow(
                        title = "Haptica",
                        subtitle = "Vibracion corta al girar.",
                        checked = settings.hapticEnabled,
                        onCheckedChange = onHapticChanged
                    )
                    SettingsSwitchRow(
                        title = "Efectos visuales",
                        subtitle = "Reserva para reducir animaciones en pruebas.",
                        checked = settings.visualEffectsEnabled,
                        onCheckedChange = onVisualEffectsChanged
                    )
                }
            }
            VegasCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Horas silenciosas", style = MaterialTheme.typography.titleLarge)
                    HourStepper(label = "Inicio", value = settings.quietHoursStart, onChange = onQuietStartChanged)
                    HourStepper(label = "Fin", value = settings.quietHoursEnd, onChange = onQuietEndChanged)
                    Text(
                        text = "Durante este tramo no se muestran recordatorios locales.",
                        color = VegasColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, color = VegasColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HourStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$label: ${value.toString().padStart(2, '0')}:00", modifier = Modifier.weight(1f))
        VegasSecondaryButton(text = "-", onClick = { onChange((value + 23) % 24) }, modifier = Modifier.weight(0.5f))
        VegasSecondaryButton(text = "+", onClick = { onChange((value + 1) % 24) }, modifier = Modifier.weight(0.5f))
    }
}
