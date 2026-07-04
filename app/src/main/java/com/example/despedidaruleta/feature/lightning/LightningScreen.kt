package com.example.despedidaruleta.feature.lightning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.despedidaruleta.core.designsystem.component.LoadingState
import com.example.despedidaruleta.core.designsystem.component.MessageBanner
import com.example.despedidaruleta.core.designsystem.component.SectionTitle
import com.example.despedidaruleta.core.designsystem.component.SyncStatusPill
import com.example.despedidaruleta.core.designsystem.component.VegasBackground
import com.example.despedidaruleta.core.designsystem.component.VegasCard
import com.example.despedidaruleta.core.designsystem.component.VegasPrimaryButton
import com.example.despedidaruleta.core.designsystem.component.VegasSecondaryButton
import com.example.despedidaruleta.core.designsystem.theme.VegasColors

@Composable
fun LightningScreen(
    uiState: LightningUiState,
    onStartRound: () -> Unit,
    onAnswer: (Boolean) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                VegasSecondaryButton(text = "Volver", onClick = onBack, modifier = Modifier.fillMaxWidth(0.34f))
                SyncStatusPill(networkStatus = uiState.networkStatus, fromCache = uiState.fromCache)
            }
            SectionTitle(eyebrow = "Juego unico", title = "Ronda relampago")
            uiState.errorMessage?.let { MessageBanner(message = it) }

            if (uiState.isLoading) {
                LoadingState(message = "Cargando ronda relampago")
            } else {
                LightningCard(uiState = uiState, onStartRound = onStartRound, onAnswer = onAnswer, onBack = onBack)
            }
        }
    }
}

@Composable
private fun LightningCard(
    uiState: LightningUiState,
    onStartRound: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val round = uiState.round
    VegasCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                round == null && uiState.totalCount == 0 -> {
                    Text(
                        text = "Sin preguntas relampago",
                        style = MaterialTheme.typography.titleLarge,
                        color = VegasColors.TextPrimary
                    )
                    Text(
                        text = "El propietario debe importarlas desde la pantalla de contenido antes de jugar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VegasColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                round == null && uiState.alreadyPlayed -> {
                    Text(
                        text = "La ronda relampago ya se ha jugado",
                        style = MaterialTheme.typography.titleLarge,
                        color = VegasColors.NeonPurple,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Este juego se juega una sola vez por despedida y sus ${uiState.totalCount} preguntas ya estan gastadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VegasColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    VegasSecondaryButton(text = "Volver", onClick = onBack)
                }
                round == null -> {
                    Text(
                        text = "Preparados para el relampago",
                        style = MaterialTheme.typography.titleLarge,
                        color = VegasColors.NeonPurple,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Juego unico y continuo: se encadenan las ${uiState.availableCount} preguntas del tiron, " +
                            "con ${LightningViewModel.SECONDS_PER_QUESTION} segundos para responder cada una. " +
                            "Si se agota el tiempo cuenta como fallo. Quien pulse empezar dirige el juego desde su movil.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VegasColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    VegasPrimaryButton(
                        text = "Empezar ronda relampago",
                        onClick = onStartRound,
                        enabled = uiState.canStart,
                        isLoading = uiState.actionLoading
                    )
                }
                round.finished -> {
                    Text(text = "Resultado final", style = MaterialTheme.typography.labelLarge, color = VegasColors.Gold)
                    Text(
                        text = "${round.hits} de ${round.totalQuestions} aciertos",
                        style = MaterialTheme.typography.headlineMedium,
                        color = VegasColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    VegasPrimaryButton(text = "Volver", onClick = onBack)
                }
                else -> {
                    Text(
                        text = "Pregunta ${round.currentIndex + 1} de ${round.totalQuestions}",
                        style = MaterialTheme.typography.labelLarge,
                        color = VegasColors.Gold
                    )
                    Text(
                        text = "${round.secondsLeft}",
                        style = MaterialTheme.typography.displayLarge,
                        color = if (round.secondsLeft <= 3) VegasColors.Error else VegasColors.NeonPurple
                    )
                    Text(
                        text = round.currentQuestion?.text.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = VegasColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        VegasSecondaryButton(
                            text = "Fallo",
                            onClick = { onAnswer(false) },
                            modifier = Modifier.weight(1f)
                        )
                        VegasPrimaryButton(
                            text = "Acierto",
                            onClick = { onAnswer(true) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "Aciertos: ${round.hits}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VegasColors.TextSecondary
                    )
                }
            }
        }
    }
}
