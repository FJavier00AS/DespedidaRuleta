package com.example.despedidaruleta.feature.admin

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.example.despedidaruleta.domain.model.ImportPreview
import com.example.despedidaruleta.domain.model.RouletteCategory

@Composable
fun AdminScreen(
    uiState: AdminUiState,
    onParseFile: (android.net.Uri, RouletteCategory?) -> Unit,
    onConfirmImport: () -> Unit,
    onLoadDemoContent: () -> Unit,
    onClearPreview: () -> Unit,
    onSendTestBroadcast: () -> Unit,
    onBack: () -> Unit
) {
    var pendingCategory by remember { mutableStateOf<RouletteCategory?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onParseFile(uri, pendingCategory)
    }
    fun openFile(category: RouletteCategory?) {
        pendingCategory = category
        launcher.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/csv",
                "text/comma-separated-values",
                "application/octet-stream"
            )
        )
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
            SectionTitle(eyebrow = "Administracion", title = "Contenido de la ruleta")
            if (uiState.errorMessage != null) MessageBanner(message = uiState.errorMessage)
            if (uiState.infoMessage != null) MessageBanner(message = uiState.infoMessage, isError = false)
            uiState.result?.let { result ->
                MessageBanner(
                    message = "Importacion lista: ${result.inserted} insertadas, ${result.skipped} duplicadas.",
                    isError = false
                )
            }
            if (uiState.isLoading) LoadingState(message = "Cargando contenido")

            VegasCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Notificaciones", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Manda un aviso de prueba a todos los moviles del grupo, aunque tengan la app cerrada.",
                        color = VegasColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    VegasPrimaryButton(
                        text = "Avisar a todos",
                        onClick = onSendTestBroadcast,
                        isLoading = uiState.isSendingBroadcast
                    )
                }
            }

            VegasCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Prueba rapida", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Carga preguntas, retos y castigos inventados para probar la ruleta sin preparar archivos.",
                        color = VegasColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    VegasPrimaryButton(
                        text = "Cargar demo",
                        onClick = onLoadDemoContent,
                        isLoading = uiState.isImporting
                    )
                }
            }

            VegasCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Importar archivo", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Formato recomendado: XLSX o CSV. Para mixto usa columnas categoria, numero, texto. Para botones por categoria basta numero y texto.",
                        color = VegasColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    VegasPrimaryButton(
                        text = "Importar mixto",
                        onClick = { openFile(null) },
                        isLoading = uiState.isParsing
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        VegasSecondaryButton(
                            text = "Preguntas",
                            onClick = { openFile(RouletteCategory.QUESTION) },
                            modifier = Modifier.weight(1f)
                        )
                        VegasSecondaryButton(
                            text = "Retos",
                            onClick = { openFile(RouletteCategory.CHALLENGE) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    VegasSecondaryButton(text = "Castigos", onClick = { openFile(RouletteCategory.PUNISHMENT) })
                }
            }

            StatsCard(uiState)
            uiState.preview?.let { preview ->
                PreviewCard(
                    preview = preview,
                    isImporting = uiState.isImporting,
                    onConfirmImport = onConfirmImport,
                    onClearPreview = onClearPreview
                )
            }
            ContentSummaryCard(uiState)
        }
    }
}

@Composable
private fun StatsCard(uiState: AdminUiState) {
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Resumen", style = MaterialTheme.typography.titleLarge)
            RouletteCategory.entries.forEach { category ->
                val stats = uiState.stats.firstOrNull { it.category == category }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = category.label, color = VegasColors.Gold)
                    Text(
                        text = "${stats?.availableCount ?: 0} libres / ${stats?.totalCount ?: 0}",
                        color = VegasColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    preview: ImportPreview,
    isImporting: Boolean,
    onConfirmImport: () -> Unit,
    onClearPreview: () -> Unit
) {
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Vista previa: ${preview.fileName}", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Validas: ${preview.validRows.size}. Invalidas: ${preview.invalidRows.size}.",
                color = VegasColors.TextSecondary
            )
            preview.rows.take(12).forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Fila ${row.sourceRow}: ${row.category?.label ?: "?"} #${row.number ?: "?"}",
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.isValid) VegasColors.TextPrimary else VegasColors.Error
                    )
                    Text(text = row.text.ifBlank { row.error.orEmpty() }, color = VegasColors.TextSecondary)
                    row.error?.let { Text(text = it, color = VegasColors.Error, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                VegasSecondaryButton(text = "Cancelar", onClick = onClearPreview, modifier = Modifier.weight(1f))
                VegasPrimaryButton(
                    text = "Confirmar",
                    onClick = onConfirmImport,
                    enabled = preview.validRows.isNotEmpty(),
                    isLoading = isImporting,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ContentSummaryCard(uiState: AdminUiState) {
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Ultimos contenidos", style = MaterialTheme.typography.titleLarge)
            uiState.content.take(12).forEach { item ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "${item.category.label} #${item.number}", color = VegasColors.Gold)
                    Text(
                        text = if (item.used) "Usado" else "Libre",
                        color = if (item.used) VegasColors.TextSecondary else VegasColors.Success
                    )
                }
                Text(text = item.text, color = VegasColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (uiState.content.isEmpty()) {
                Text(text = "Todavia no hay contenido importado.", color = VegasColors.TextSecondary)
            }
        }
    }
}
