package com.example.despedidaruleta.feature.events

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.despedidaruleta.core.designsystem.component.LoadingState
import com.example.despedidaruleta.core.designsystem.component.MessageBanner
import com.example.despedidaruleta.core.designsystem.component.SectionTitle
import com.example.despedidaruleta.core.designsystem.component.VegasBackground
import com.example.despedidaruleta.core.designsystem.component.VegasCard
import com.example.despedidaruleta.core.designsystem.component.VegasPrimaryButton
import com.example.despedidaruleta.core.designsystem.component.VegasSecondaryButton
import com.example.despedidaruleta.core.designsystem.theme.VegasColors

@Composable
fun EventsScreen(
    uiState: EventsUiState,
    onToggleActive: () -> Unit,
    onMarkCompleted: () -> Unit,
    onBack: () -> Unit
) {
    VegasBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VegasSecondaryButton(text = "Volver", onClick = onBack, modifier = Modifier.fillMaxWidth(0.34f))
            SectionTitle(eyebrow = "Eventos aleatorios", title = "Modo activo")

            if (uiState.errorMessage != null) {
                MessageBanner(message = uiState.errorMessage)
            }

            VegasCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Estado", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (uiState.isActive) {
                            if (uiState.awaitingCompletion) {
                                "Marca el evento actual como completado para que el siguiente pueda sorprenderos."
                            } else {
                                "Activo: el próximo evento puede llegar en cualquier momento. Sorpresa."
                            }
                        } else {
                            "Inactivo: activa este modo para empezar a lanzar eventos aleatorios."
                        },
                        color = VegasColors.TextSecondary
                    )
                    VegasPrimaryButton(
                        text = if (uiState.isActive) "Detener eventos" else "Activar eventos",
                        onClick = onToggleActive,
                        isLoading = uiState.isLoading
                    )
                }
            }

            if (uiState.isLoading) {
                LoadingState(message = "Cargando eventos")
            } else {
                VegasCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Evento actual", style = MaterialTheme.typography.titleLarge)
                        val event = uiState.currentEvent
                        if (event == null) {
                            Text("Todavia no hay eventos disponibles.", color = VegasColors.TextSecondary)
                        } else {
                            Text(text = "#${event.number}", color = VegasColors.Gold, fontWeight = FontWeight.Bold)
                            Text(text = event.text, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (uiState.isActive) {
                                    if (uiState.awaitingCompletion) {
                                        "Marca el evento como completado para seguir."
                                    } else {
                                        "El siguiente evento es sorpresa: puede llegar en cualquier momento."
                                    }
                                } else {
                                    "Activa el modo para empezar a lanzar eventos."
                                },
                                color = VegasColors.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (uiState.isActive && uiState.awaitingCompletion) {
                                VegasPrimaryButton(
                                    text = "Completado",
                                    onClick = onMarkCompleted,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                if (uiState.history.isNotEmpty()) {
                    VegasCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Historial reciente", style = MaterialTheme.typography.titleLarge)
                            uiState.history.forEach { event ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(text = "#${event.number}", color = VegasColors.Gold, fontWeight = FontWeight.Bold)
                                    Text(text = event.text, color = VegasColors.TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * App-wide popup shown when a new event arrives, regardless of which screen is on top
 * (the caller is responsible for not composing this while on the lightning round screen).
 * "Aceptar" only dismisses the popup; the event stays awaiting completion until the
 * user marks it done from the Eventos screen.
 */
@Composable
fun EventArrivalPopup(uiState: EventsUiState, onAccept: () -> Unit) {
    val event = uiState.currentEvent
    if (!uiState.isActive || !uiState.awaitingCompletion || event == null) return

    val haptic = LocalHapticFeedback.current
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }
    DisposableEffect(Unit) {
        onDispose { toneGenerator.release() }
    }
    LaunchedEffect(event.id) {
        if (uiState.settings.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (uiState.settings.soundEnabled) toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
    }

    AlertDialog(
        onDismissRequest = {},
        containerColor = VegasColors.Card,
        titleContentColor = VegasColors.TextPrimary,
        textContentColor = VegasColors.TextPrimary,
        title = {
            Text(text = "Evento sorpresa", style = MaterialTheme.typography.headlineSmall, color = VegasColors.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "#${event.number}", color = VegasColors.Gold, fontWeight = FontWeight.Bold)
                Text(text = event.text, style = MaterialTheme.typography.titleLarge, color = VegasColors.TextPrimary)
                Text(
                    text = "Marca el evento como completado desde la pestaña de Eventos cuando lo terminéis.",
                    color = VegasColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(text = "Aceptar", color = VegasColors.Gold)
            }
        }
    )
}
