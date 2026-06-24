package com.example.despedidaruleta.feature.roulette

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.despedidaruleta.core.designsystem.component.EmptyState
import com.example.despedidaruleta.core.designsystem.component.LoadingState
import com.example.despedidaruleta.core.designsystem.component.MessageBanner
import com.example.despedidaruleta.core.designsystem.component.SectionTitle
import com.example.despedidaruleta.core.designsystem.component.SyncStatusPill
import com.example.despedidaruleta.core.designsystem.component.VegasBackground
import com.example.despedidaruleta.core.designsystem.component.VegasCard
import com.example.despedidaruleta.core.designsystem.component.VegasPrimaryButton
import com.example.despedidaruleta.core.designsystem.component.VegasSecondaryButton
import com.example.despedidaruleta.core.designsystem.theme.VegasColors
import com.example.despedidaruleta.domain.model.ContentItem
import com.example.despedidaruleta.domain.model.GamePhase
import com.example.despedidaruleta.domain.model.RouletteCategory

@Composable
fun RouletteScreen(
    uiState: RouletteUiState,
    onSpin: () -> Unit,
    onResetGame: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLocalSettings: () -> Unit,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }
    DisposableEffect(Unit) {
        onDispose { toneGenerator.release() }
    }
    LaunchedEffect(uiState.gameState.activeSpinId, uiState.gameState.phase) {
        if (uiState.gameState.activeSpinId != null && uiState.gameState.phase == GamePhase.CONTENT_SPINNING) {
            if (uiState.settings.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (uiState.settings.soundEnabled) toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        }
    }

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
            SectionTitle(eyebrow = "Juego sincronizado", title = "Ruleta de la despedida")
            when {
                uiState.isLoading -> LoadingState(message = "Cargando ruleta")
                uiState.errorMessage != null -> MessageBanner(message = uiState.errorMessage)
                uiState.content.isEmpty() -> EmptyState(
                    title = "Sin contenido",
                    message = "El propietario debe importar preguntas, retos o castigos antes de jugar."
                )
            }
            uiState.infoMessage?.let { MessageBanner(message = it, isError = false) }

            WheelsPanel(uiState = uiState)

            VegasCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Control", style = MaterialTheme.typography.titleLarge)
                    VegasPrimaryButton(
                        text = if (uiState.gameState.phase == GamePhase.CONTENT_SPINNING) "Girando" else "Girar ruleta",
                        onClick = onSpin,
                        enabled = uiState.canSpin,
                        isLoading = uiState.actionLoading
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        VegasSecondaryButton(
                            text = "Historial",
                            onClick = onOpenHistory,
                            modifier = Modifier.weight(1f)
                        )
                        VegasSecondaryButton(
                            text = "Ajustes",
                            onClick = onOpenLocalSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        VegasSecondaryButton(
                            text = "Contenido",
                            onClick = onOpenAdmin,
                            modifier = Modifier.weight(1f)
                        )
                        VegasSecondaryButton(
                            text = "Reset",
                            onClick = onResetGame,
                            enabled = uiState.gameState.phase != GamePhase.CONTENT_SPINNING,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            StatsPanel(uiState)
            ResultPanel(uiState)
        }
    }
}

@Composable
private fun WheelsPanel(uiState: RouletteUiState) {
    val categoryLabels = RouletteCategory.entries.map { category ->
        val count = uiState.stats.firstOrNull { it.category == category }?.availableCount ?: 0
        "${category.label}\n$count"
    }
    val availableContent = uiState.content
        .filter { it.active && !it.used && (uiState.gameState.selectedCategory == null || it.category == uiState.gameState.selectedCategory) }
        .sortedWith(compareBy<ContentItem> { it.category.ordinal }.thenBy { it.number })
        .take(18)
    val contentLabels = availableContent.ifEmpty { uiState.content.filter { it.active }.take(18) }
        .map { "${it.number}" }
        .ifEmpty { listOf("?") }

    VegasCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "Categoria", style = MaterialTheme.typography.titleMedium, color = VegasColors.Gold)
            WheelCanvas(
                labels = categoryLabels,
                rotation = uiState.gameState.categoryRotation,
                colors = RouletteCategory.entries.map { it.segmentColor() },
                centerText = uiState.gameState.selectedCategory?.label ?: "Lista",
                effectsEnabled = uiState.settings.visualEffectsEnabled
            )
            Text(text = "Contenido", style = MaterialTheme.typography.titleMedium, color = VegasColors.Gold)
            WheelCanvas(
                labels = contentLabels,
                rotation = uiState.gameState.contentRotation,
                colors = contentLabels.indices.map { if (it % 2 == 0) VegasColors.Red else VegasColors.Gold },
                centerText = uiState.gameState.selectedContentNumber?.toString() ?: "?",
                effectsEnabled = uiState.settings.visualEffectsEnabled
            )
        }
    }
}

@Composable
private fun WheelCanvas(
    labels: List<String>,
    rotation: Float,
    colors: List<Color>,
    centerText: String,
    effectsEnabled: Boolean
) {
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = tween(durationMillis = if (effectsEnabled) 2_200 else 0, easing = FastOutSlowInEasing),
        label = "wheelRotation"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .graphicsLayer(rotationZ = animatedRotation)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val segmentCount = labels.size.coerceAtLeast(1)
                val sweep = 360f / segmentCount
                repeat(segmentCount) { index ->
                    drawArc(
                        color = colors[index % colors.size].copy(alpha = if (index % 2 == 0) 0.92f else 0.78f),
                        startAngle = -90f + index * sweep,
                        sweepAngle = sweep,
                        useCenter = true
                    )
                }
                drawCircle(color = VegasColors.Charcoal.copy(alpha = 0.35f), radius = size.minDimension * 0.18f)
            }
        }
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(VegasColors.Charcoal)
                .border(2.dp, VegasColors.Gold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = centerText,
                color = VegasColors.Gold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
        }
        Text(
            text = "V",
            color = VegasColors.Gold,
            modifier = Modifier.align(Alignment.TopCenter),
            style = MaterialTheme.typography.headlineMedium
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 10.dp)) {
        labels.take(8).forEach { label ->
            Text(text = label.replace('\n', ' '), style = MaterialTheme.typography.labelMedium, color = VegasColors.TextSecondary)
        }
    }
}

@Composable
private fun StatsPanel(uiState: RouletteUiState) {
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Disponibilidad", style = MaterialTheme.typography.titleLarge)
            RouletteCategory.entries.forEach { category ->
                val stats = uiState.stats.firstOrNull { it.category == category }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = category.label, color = category.segmentColor(), fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${stats?.availableCount ?: 0} libres / ${stats?.totalCount ?: 0} total",
                        color = VegasColors.TextSecondary
                    )
                }
            }
            Text(text = "Usadas: ${uiState.totalUsed}", color = VegasColors.TextSecondary)
        }
    }
}

@Composable
private fun ResultPanel(uiState: RouletteUiState) {
    val text = uiState.gameState.selectedContentText
    if (text.isNullOrBlank()) return
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Resultado", style = MaterialTheme.typography.titleLarge, color = VegasColors.Gold)
            Text(
                text = "${uiState.gameState.selectedCategory?.label.orEmpty()} #${uiState.gameState.selectedContentNumber ?: ""}",
                color = VegasColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(text = text, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun RouletteCategory.segmentColor(): Color = when (this) {
    RouletteCategory.QUESTION -> VegasColors.NeonCyan
    RouletteCategory.CHALLENGE -> VegasColors.Gold
    RouletteCategory.PUNISHMENT -> VegasColors.Red
}
