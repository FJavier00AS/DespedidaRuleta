package com.example.despedidaruleta.feature.roulette

import android.graphics.Paint
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RouletteScreen(
    uiState: RouletteUiState,
    onSpinCategory: () -> Unit,
    onOpenLightningSection: () -> Unit,
    onSpinContent: () -> Unit,
    onResolveResult: (Boolean) -> Unit,
    onCloseLightningSummary: () -> Unit,
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
    LaunchedEffect(uiState.gameState.phase, uiState.gameState.activeSpinId) {
        val spinning = uiState.gameState.phase == GamePhase.CATEGORY_SPINNING || uiState.gameState.phase == GamePhase.CONTENT_SPINNING
        if (spinning) {
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

            GameStagePanel(
                uiState = uiState,
                onSpinCategory = onSpinCategory,
                onOpenLightningSection = onOpenLightningSection,
                onSpinContent = onSpinContent
            )

            UtilityControls(
                uiState = uiState,
                onOpenHistory = onOpenHistory,
                onOpenLocalSettings = onOpenLocalSettings,
                onOpenAdmin = onOpenAdmin,
                onResetGame = onResetGame
            )

            StatsPanel(uiState)
        }
    }

    ResultDialog(uiState = uiState, onResolveResult = onResolveResult)
    LightningSummaryDialog(uiState = uiState, onClose = onCloseLightningSummary)
}

@Composable
private fun GameStagePanel(
    uiState: RouletteUiState,
    onSpinCategory: () -> Unit,
    onOpenLightningSection: () -> Unit,
    onSpinContent: () -> Unit
) {
    val phase = uiState.gameState.phase
    val showContentWheel = uiState.gameState.selectedCategory != null &&
        (phase == GamePhase.CATEGORY_SELECTED || phase == GamePhase.CONTENT_SPINNING || phase == GamePhase.COMPLETED)

    if (phase == GamePhase.IDLE) {
        QuestionIntroPanel(uiState = uiState, onSpinCategory = onSpinCategory, onOpenLightningSection = onOpenLightningSection)
    } else if (showContentWheel) {
        ContentWheelPanel(uiState = uiState, onSpinContent = onSpinContent)
    } else {
        QuestionIntroPanel(uiState = uiState, onSpinCategory = onSpinCategory, onOpenLightningSection = onOpenLightningSection)
    }
}

@Composable
private fun QuestionIntroPanel(
    uiState: RouletteUiState,
    onSpinCategory: () -> Unit,
    onOpenLightningSection: () -> Unit
) {
    VegasCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "Ruleta de preguntas", style = MaterialTheme.typography.titleLarge, color = VegasColors.TextPrimary)
            Text(
                text = "La partida gira alrededor de preguntas. Si aciertas, recibirás un mensaje de premio; si fallas, se abrirá la ruleta de castigos. También puedes entrar a la sección relámpago para responder varias preguntas rápidas seguidas.",
                style = MaterialTheme.typography.bodyMedium,
                color = VegasColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            WheelCanvas(
                segments = listOf(
                    WheelSegment(
                        label = "Preguntas",
                        weight = 1,
                        color = VegasColors.NeonCyan,
                        labelColor = VegasColors.TextPrimary
                    )
                ),
                rotation = uiState.gameState.categoryRotation,
                centerText = "Preguntas",
                effectsEnabled = uiState.settings.visualEffectsEnabled
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                VegasPrimaryButton(
                    text = if (uiState.gameState.phase == GamePhase.CATEGORY_SPINNING) "Girando pregunta" else "Girar pregunta",
                    onClick = onSpinCategory,
                    enabled = uiState.canSpinCategory,
                    isLoading = uiState.actionLoading,
                    modifier = Modifier.weight(1f)
                )
                VegasSecondaryButton(
                    text = "Relámpago",
                    onClick = onOpenLightningSection,
                    enabled = uiState.canSpinCategory,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CategoryWheelPanel(
    uiState: RouletteUiState,
    onSpinCategory: () -> Unit
) {
    val totalAvailable = uiState.totalAvailable.coerceAtLeast(0)
    val segments = RouletteCategory.entries.map { category ->
        val stats = uiState.stats.firstOrNull { it.category == category }
        val available = stats?.availableCount ?: 0
        val percent = if (totalAvailable > 0) ((available * 100f) / totalAvailable).roundToInt() else 0
        WheelSegment(
            label = "${category.shortLabel()}\n$percent%",
            weight = available.coerceAtLeast(0),
            color = category.segmentColor(),
            labelColor = category.segmentTextColor()
        )
    }

    VegasCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "Ruleta principal", style = MaterialTheme.typography.titleLarge, color = VegasColors.TextPrimary)
            Text(
                text = "La probabilidad depende del contenido disponible: más preguntas, sección relámpago o castigos equivalen a más espacio en la ruleta.",
                style = MaterialTheme.typography.bodyMedium,
                color = VegasColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            WheelCanvas(
                segments = segments,
                rotation = uiState.gameState.categoryRotation,
                centerText = when (uiState.gameState.phase) {
                    GamePhase.CATEGORY_SPINNING -> "Girando"
                    else -> "Principal"
                },
                effectsEnabled = uiState.settings.visualEffectsEnabled
            )
            VegasPrimaryButton(
                text = if (uiState.gameState.phase == GamePhase.CATEGORY_SPINNING) "Girando categoria" else "Girar categoria",
                onClick = onSpinCategory,
                enabled = uiState.canSpinCategory,
                isLoading = uiState.actionLoading
            )
        }
    }
}

@Composable
private fun ContentWheelPanel(
    uiState: RouletteUiState,
    onSpinContent: () -> Unit
) {
    val category = uiState.gameState.selectedCategory ?: return
    val selectedContentId = uiState.gameState.selectedContentId
    val availableContent = uiState.content
        .filter { it.active && !it.used && it.category == category }
        .sortedBy { it.number }
    val selectedContent = uiState.content.firstOrNull { it.id == selectedContentId }
    val wheelContent = (listOfNotNull(selectedContent) + availableContent)
        .distinctBy { it.id }
        .ifEmpty { availableContent }
    val segments = wheelContent.mapIndexed { index, item ->
        WheelSegment(
            label = "#${item.number}",
            weight = 1,
            color = if (index % 2 == 0) category.segmentColor() else category.segmentColor().copy(alpha = 0.72f),
            labelColor = category.segmentTextColor()
        )
    }

    VegasCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = categoryWheelTitle(category), style = MaterialTheme.typography.titleLarge, color = VegasColors.TextPrimary)
            Text(
                text = contentWheelMessage(uiState, category, availableContent.size),
                style = MaterialTheme.typography.bodyMedium,
                color = VegasColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            WheelCanvas(
                segments = segments,
                rotation = uiState.gameState.contentRotation,
                centerText = when (uiState.gameState.phase) {
                    GamePhase.CONTENT_SPINNING -> "Girando"
                    GamePhase.COMPLETED -> "#${uiState.gameState.selectedContentNumber ?: "?"}"
                    else -> category.shortLabel()
                },
                effectsEnabled = uiState.settings.visualEffectsEnabled
            )
            if (uiState.gameState.phase != GamePhase.COMPLETED) {
                VegasPrimaryButton(
                    text = if (uiState.gameState.phase == GamePhase.CONTENT_SPINNING) "Girando ${category.label}" else "Girar ${category.label}",
                    onClick = onSpinContent,
                    enabled = uiState.canSpinContent,
                    isLoading = uiState.actionLoading
                )
            }
        }
    }
}

@Composable
private fun UtilityControls(
    uiState: RouletteUiState,
    onOpenHistory: () -> Unit,
    onOpenLocalSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
    onResetGame: () -> Unit
) {
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Control", style = MaterialTheme.typography.titleLarge, color = VegasColors.TextPrimary)
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
                    enabled = !uiState.categorySpinInProgress && !uiState.contentSpinInProgress,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WheelCanvas(
    segments: List<WheelSegment>,
    rotation: Float,
    centerText: String,
    effectsEnabled: Boolean
) {
    val visibleSegments = segments.filter { it.weight > 0 }.ifEmpty {
        listOf(WheelSegment("Sin\ndatos", 1, VegasColors.TextSecondary, VegasColors.TextPrimary))
    }
    val totalWeight = visibleSegments.sumOf { it.weight }.coerceAtLeast(1)
    val textSize = with(LocalDensity.current) { 14.sp.toPx() }
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = tween(durationMillis = if (effectsEnabled) 2_200 else 0, easing = FastOutSlowInEasing),
        label = "wheelRotation"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(292.dp)
                .graphicsLayer(rotationZ = animatedRotation)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                visibleSegments.forEach { segment ->
                    val sweep = 360f * segment.weight / totalWeight
                    drawArc(
                        color = segment.color.copy(alpha = 0.92f),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true
                    )
                    drawArc(
                        color = VegasColors.Charcoal.copy(alpha = 0.24f),
                        startAngle = startAngle,
                        sweepAngle = 0.9f,
                        useCenter = true
                    )
                    drawSegmentLabel(
                        label = segment.label,
                        startAngle = startAngle,
                        sweep = sweep,
                        textSize = textSize,
                        color = segment.labelColor
                    )
                    startAngle += sweep
                }
                drawCircle(color = VegasColors.Charcoal.copy(alpha = 0.38f), radius = size.minDimension * 0.19f)
            }
        }
        Box(
            modifier = Modifier
                .size(96.dp)
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
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSegmentLabel(
    label: String,
    startAngle: Float,
    sweep: Float,
    textSize: Float,
    color: Color
) {
    if (sweep < 12f) return
    val middleAngle = startAngle + sweep / 2f
    val radians = middleAngle * PI.toFloat() / 180f
    val radius = size.minDimension * 0.32f
    val textX = center.x + cos(radians) * radius
    val textY = center.y + sin(radians) * radius
    val lines = label.split('\n').take(2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    drawIntoCanvas { canvas ->
        lines.forEachIndexed { index, line ->
            val offset = (index - (lines.size - 1) / 2f) * textSize * 1.1f
            canvas.nativeCanvas.drawText(line, textX, textY + offset, paint)
        }
    }
}

@Composable
private fun StatsPanel(uiState: RouletteUiState) {
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Disponibilidad", style = MaterialTheme.typography.titleLarge, color = VegasColors.TextPrimary)
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
private fun ResultDialog(uiState: RouletteUiState, onResolveResult: (Boolean) -> Unit) {
    val state = uiState.gameState
    val text = state.selectedContentText
    val category = state.selectedCategory
    if (state.phase != GamePhase.COMPLETED || text.isNullOrBlank() || category == null) return

    val isQuestion = category == RouletteCategory.QUESTION
    val isLightning = category == RouletteCategory.CHALLENGE
    val tracksResult = isQuestion || isLightning
    AlertDialog(
        onDismissRequest = {},
        containerColor = VegasColors.Card,
        titleContentColor = VegasColors.TextPrimary,
        textContentColor = VegasColors.TextPrimary,
        title = {
            Text(text = resultTitle(category), style = MaterialTheme.typography.headlineSmall, color = VegasColors.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${category.label} #${state.selectedContentNumber ?: ""}",
                    color = category.segmentColor(),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(text = text, style = MaterialTheme.typography.titleLarge, color = VegasColors.TextPrimary)
                if (isQuestion) {
                    Text(
                        text = "Marca premio para recibir el premio y volver a empezar. Marca fallo para abrir una ruleta de castigos.",
                        color = VegasColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (isLightning) {
                    Text(
                        text = "Pregunta ${(state.lightningAnswered + 1).coerceAtMost(state.lightningTotal)} de ${state.lightningTotal}. Marca acierto o fallo para seguir con la ronda.",
                        color = VegasColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onResolveResult(true) }, enabled = !uiState.actionLoading) {
                Text(text = if (isQuestion) "Premio" else if (isLightning) "Acierto" else "Volver", color = VegasColors.Gold)
            }
        },
        dismissButton = {
            if (tracksResult) {
                TextButton(onClick = { onResolveResult(false) }, enabled = !uiState.actionLoading) {
                    Text(text = "Fallo", color = VegasColors.Red)
                }
            }
        }
    )
}

@Composable
private fun LightningSummaryDialog(uiState: RouletteUiState, onClose: () -> Unit) {
    val state = uiState.gameState
    if (state.phase != GamePhase.LIGHTNING_SUMMARY) return

    AlertDialog(
        onDismissRequest = {},
        containerColor = VegasColors.Card,
        titleContentColor = VegasColors.TextPrimary,
        textContentColor = VegasColors.TextPrimary,
        title = {
            Text(text = "Resumen ronda relámpago", style = MaterialTheme.typography.headlineSmall, color = VegasColors.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${state.lightningCorrect} de ${state.lightningAnswered} correctas (${state.lightningPercent}%)",
                    style = MaterialTheme.typography.titleLarge,
                    color = VegasColors.Gold
                )
                Text(text = "Correctas: ${state.lightningCorrect}", color = VegasColors.TextSecondary)
                Text(text = "Incorrectas: ${state.lightningIncorrect}", color = VegasColors.TextSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, enabled = !uiState.actionLoading) {
                Text(text = "Cerrar", color = VegasColors.Gold)
            }
        }
    )
}

private data class WheelSegment(
    val label: String,
    val weight: Int,
    val color: Color,
    val labelColor: Color
)

private fun RouletteCategory.segmentColor(): Color = when (this) {
    RouletteCategory.QUESTION -> VegasColors.NeonCyan
    RouletteCategory.CHALLENGE -> VegasColors.Gold
    RouletteCategory.PUNISHMENT -> VegasColors.Red
    RouletteCategory.EVENT -> VegasColors.Warning
}

private fun RouletteCategory.segmentTextColor(): Color = when (this) {
    RouletteCategory.CHALLENGE -> VegasColors.Charcoal
    RouletteCategory.EVENT -> VegasColors.Charcoal
    else -> VegasColors.TextPrimary
}

private fun RouletteCategory.shortLabel(): String = when (this) {
    RouletteCategory.QUESTION -> "Preguntas"
    RouletteCategory.CHALLENGE -> "Relámpago"
    RouletteCategory.PUNISHMENT -> "Castigos"
    RouletteCategory.EVENT -> "Eventos"
}

private fun categoryWheelTitle(category: RouletteCategory): String = when (category) {
    RouletteCategory.QUESTION -> "Ruleta de preguntas"
    RouletteCategory.CHALLENGE -> "Ruleta relámpago"
    RouletteCategory.PUNISHMENT -> "Ruleta de castigos"
    RouletteCategory.EVENT -> "Ruleta de eventos"
}

private fun contentWheelMessage(uiState: RouletteUiState, category: RouletteCategory, availableCount: Int): String {
    val state = uiState.gameState
    val lightningProgress = "Pregunta ${(state.lightningAnswered + 1).coerceAtMost(state.lightningTotal)} de ${state.lightningTotal}."
    return when (state.phase) {
        GamePhase.CATEGORY_SELECTED -> if (category == RouletteCategory.CHALLENGE) {
            "Ronda relámpago activa. $lightningProgress"
        } else {
            "Ha salido ${category.label.lowercase()}. Ahora gira esta ruleta. Quedan $availableCount disponibles."
        }
        GamePhase.CONTENT_SPINNING -> if (category == RouletteCategory.CHALLENGE) {
            "La ruleta relámpago está girando. $lightningProgress"
        } else {
            "La ruleta de ${category.label.lowercase()} esta girando."
        }
        GamePhase.COMPLETED -> "Resultado preparado. Resuelve el modal para continuar."
        else -> "Contenido disponible: $availableCount."
    }
}

private fun resultTitle(category: RouletteCategory): String = when (category) {
    RouletteCategory.QUESTION -> "Pregunta"
    RouletteCategory.CHALLENGE -> "Relámpago"
    RouletteCategory.PUNISHMENT -> "Castigo"
    RouletteCategory.EVENT -> "Evento"
}
