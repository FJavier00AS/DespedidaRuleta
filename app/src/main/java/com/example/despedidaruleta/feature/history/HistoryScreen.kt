package com.example.despedidaruleta.feature.history

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.despedidaruleta.core.designsystem.component.EmptyState
import com.example.despedidaruleta.core.designsystem.component.LoadingState
import com.example.despedidaruleta.core.designsystem.component.MessageBanner
import com.example.despedidaruleta.core.designsystem.component.SectionTitle
import com.example.despedidaruleta.core.designsystem.component.SyncStatusPill
import com.example.despedidaruleta.core.designsystem.component.VegasBackground
import com.example.despedidaruleta.core.designsystem.component.VegasCard
import com.example.despedidaruleta.core.designsystem.component.VegasSecondaryButton
import com.example.despedidaruleta.core.designsystem.theme.VegasColors
import com.example.despedidaruleta.domain.model.SpinRecord
import com.example.despedidaruleta.domain.model.SpinStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onRestore: (String) -> Unit,
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
            SectionTitle(eyebrow = "Trazabilidad", title = "Historial de giros")
            uiState.errorMessage?.let { MessageBanner(message = it) }
            uiState.infoMessage?.let { MessageBanner(message = it, isError = false) }
            when {
                uiState.isLoading -> LoadingState(message = "Cargando historial")
                uiState.spins.isEmpty() -> EmptyState(title = "Sin giros", message = "Cuando alguien gire la ruleta aparecera aqui.")
                else -> uiState.spins.forEach { spin ->
                    SpinCard(
                        spin = spin,
                        isOwner = uiState.isOwner,
                        actionLoading = uiState.actionLoading,
                        onRestore = onRestore
                    )
                }
            }
        }
    }
}

@Composable
private fun SpinCard(
    spin: SpinRecord,
    isOwner: Boolean,
    actionLoading: Boolean,
    onRestore: (String) -> Unit
) {
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "${spin.category.label} #${spin.contentNumber}", color = VegasColors.Gold, fontWeight = FontWeight.SemiBold)
                Text(text = spin.status.label, color = statusColor(spin.status), style = MaterialTheme.typography.labelLarge)
            }
            Text(text = spin.contentText, style = MaterialTheme.typography.titleMedium)
            Text(text = "Por ${spin.spunByName.ifBlank { spin.spunByUid }}", color = VegasColors.TextSecondary)
            Text(text = "Inicio: ${formatMillis(spin.startedAtMillis)}", color = VegasColors.TextSecondary)
            spin.restoredAtMillis?.let { restoredAt ->
                Text(text = "Restaurado: ${formatMillis(restoredAt)}", color = VegasColors.Warning)
            }
            if (isOwner && spin.status != SpinStatus.RESTORED) {
                VegasSecondaryButton(
                    text = "Restaurar contenido",
                    onClick = { onRestore(spin.id) },
                    enabled = !actionLoading
                )
            }
        }
    }
}

private fun statusColor(status: SpinStatus) = when (status) {
    SpinStatus.SPINNING -> VegasColors.Warning
    SpinStatus.COMPLETED -> VegasColors.Success
    SpinStatus.RESTORED -> VegasColors.TextSecondary
}

private fun formatMillis(value: Long?): String = value?.let {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))
} ?: "Pendiente"
