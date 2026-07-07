package com.example.despedidaruleta.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.despedidaruleta.core.designsystem.component.EmptyState
import com.example.despedidaruleta.core.designsystem.component.LoadingState
import com.example.despedidaruleta.core.designsystem.component.MessageBanner
import com.example.despedidaruleta.core.designsystem.component.SectionTitle
import com.example.despedidaruleta.core.designsystem.component.SyncStatusPill
import com.example.despedidaruleta.core.designsystem.component.VegasBackground
import com.example.despedidaruleta.core.designsystem.component.VegasCard
import com.example.despedidaruleta.core.designsystem.component.VegasPrimaryButton
import com.example.despedidaruleta.core.designsystem.component.VegasSecondaryButton
import com.example.despedidaruleta.core.designsystem.theme.DespedidaRuletaTheme
import com.example.despedidaruleta.core.designsystem.theme.VegasColors
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.SessionDetail
import com.example.despedidaruleta.domain.model.SessionMember
import com.example.despedidaruleta.domain.model.SessionRole
import com.example.despedidaruleta.domain.model.SessionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionHomeScreen(
    uiState: SessionHomeUiState,
    onOpenWheel: () -> Unit,
    onOpenEvents: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLocalSettings: () -> Unit,
    onOpenSettings: () -> Unit,
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
                VegasSecondaryButton(text = "Volver", onClick = onBack, modifier = Modifier.fillMaxWidth(0.38f))
                SyncStatusPill(networkStatus = uiState.networkStatus, fromCache = uiState.fromCache)
            }
            when {
                uiState.isLoading -> LoadingState(message = "Abriendo sesion")
                uiState.errorMessage != null -> MessageBanner(message = uiState.errorMessage)
                uiState.session == null -> EmptyState(
                    title = "Sesion no disponible",
                    message = "No existe, no eres miembro o todavia no se ha sincronizado."
                )
                else -> SessionHomeContent(
                    session = uiState.session,
                    onOpenWheel = onOpenWheel,
                    onOpenEvents = onOpenEvents,
                    onOpenAdmin = onOpenAdmin,
                    onOpenHistory = onOpenHistory,
                    onOpenLocalSettings = onOpenLocalSettings,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun SessionHomeContent(
    session: SessionDetail,
    onOpenWheel: () -> Unit,
    onOpenEvents: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLocalSettings: () -> Unit,
    onOpenSettings: () -> Unit
) {
    SectionTitle(eyebrow = session.role.label, title = session.eventName)
    GroomPhotoCard(photoUrl = session.groomPhotoUrl, groomName = session.groomName)
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Novio", color = VegasColors.Gold, style = MaterialTheme.typography.labelLarge)
            Text(text = session.groomName, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Codigo de acceso",
                color = VegasColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = session.joinCode,
                style = MaterialTheme.typography.displayLarge,
                color = VegasColors.Gold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow(label = "Propietario", value = session.ownerDisplayName.ifBlank { session.ownerUid })
            InfoRow(label = "Miembros", value = session.memberCount.toString())
            InfoRow(label = "Estado", value = session.status.label)
            InfoRow(label = "Zona horaria", value = session.timeZone.ifBlank { "No configurada" })
            InfoRow(label = "Inicio", value = formatMillis(session.startsAtMillis))
            InfoRow(label = "Fin", value = formatMillis(session.endsAtMillis))
            InfoRow(label = "Creada", value = formatMillis(session.createdAtMillis))
        }
    }
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Acciones", style = MaterialTheme.typography.titleLarge)
            VegasPrimaryButton(
                text = "Abrir ruleta",
                onClick = onOpenWheel
            )
            VegasSecondaryButton(
                text = "Eventos aleatorios",
                onClick = onOpenEvents
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
            if (session.isOwner) {
                VegasSecondaryButton(
                    text = "Contenido",
                    onClick = onOpenAdmin
                )
                VegasSecondaryButton(
                    text = "Configuracion",
                    onClick = onOpenSettings
                )
            }
        }
    }
    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Miembros", style = MaterialTheme.typography.titleLarge)
            session.members.forEach { member ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = member.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(text = member.email, style = MaterialTheme.typography.bodyMedium, color = VegasColors.TextSecondary)
                    }
                    Text(text = member.role.label, color = VegasColors.Gold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun GroomPhotoCard(photoUrl: String?, groomName: String) {
    var failed by remember(photoUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.large)
            .background(VegasColors.Card)
            .border(1.dp, VegasColors.Gold.copy(alpha = 0.42f), MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank() && !failed) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Foto del novio $groomName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        } else {
            Text(
                text = "Foto de $groomName pendiente",
                color = VegasColors.TextSecondary,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = VegasColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, color = VegasColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatMillis(value: Long?): String {
    if (value == null) return "Pendiente de sincronizar"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(value))
}

@Preview(showBackground = true)
@Composable
private fun SessionHomePreview() {
    DespedidaRuletaTheme {
        SessionHomeScreen(
            uiState = SessionHomeUiState(
                isLoading = false,
                session = SessionDetail(
                    id = "session",
                    eventName = "Despedida en Madrid",
                    groomName = "Carlos",
                    groomPhotoUrl = null,
                    joinCode = "123456",
                    ownerUid = "owner",
                    ownerDisplayName = "Javi",
                    role = SessionRole.OWNER,
                    status = SessionStatus.ACTIVE,
                    timeZone = "Europe/Madrid",
                    memberCount = 2,
                    startsAtMillis = System.currentTimeMillis(),
                    endsAtMillis = System.currentTimeMillis() + 86_400_000,
                    createdAtMillis = System.currentTimeMillis(),
                    updatedAtMillis = System.currentTimeMillis(),
                    members = listOf(
                        SessionMember("owner", "Javi", "javi@example.com", SessionRole.OWNER, true),
                        SessionMember("member", "Luis", "luis@example.com", SessionRole.MEMBER, true)
                    )
                ),
                networkStatus = NetworkStatus.ONLINE
            ),
            onOpenSettings = {},
            onOpenWheel = {},
            onOpenEvents = {},
            onOpenAdmin = {},
            onOpenHistory = {},
            onOpenLocalSettings = {},
            onBack = {}
        )
    }
}
