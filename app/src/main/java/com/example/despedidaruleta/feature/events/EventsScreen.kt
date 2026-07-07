package com.example.despedidaruleta.feature.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
                            when {
                                uiState.isPaused -> "Pausado por la ruleta. Seguirá esperando a que vuelva la partida."
                                uiState.awaitingCompletion -> "Marca el evento actual como completado para que el siguiente pueda sorprenderos."
                                else -> "Activo: el próximo evento puede llegar en cualquier momento. Sorpresa."
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
                                    when {
                                        uiState.awaitingCompletion -> "Marca el evento como completado para seguir."
                                        uiState.isPaused -> "En pausa por la ruleta. El siguiente evento es sorpresa."
                                        else -> "El siguiente evento es sorpresa: puede llegar en cualquier momento."
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
