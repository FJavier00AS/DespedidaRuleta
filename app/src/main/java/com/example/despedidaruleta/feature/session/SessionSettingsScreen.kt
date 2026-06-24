package com.example.despedidaruleta.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.despedidaruleta.core.designsystem.component.VegasTextField
import com.example.despedidaruleta.core.designsystem.theme.DespedidaRuletaTheme
import com.example.despedidaruleta.core.designsystem.theme.VegasColors
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.SessionDetail
import com.example.despedidaruleta.domain.model.SessionRole
import com.example.despedidaruleta.domain.model.SessionStatus

@Composable
fun SessionSettingsScreen(
    uiState: SessionSettingsUiState,
    onEventNameChanged: (String) -> Unit,
    onGroomNameChanged: (String) -> Unit,
    onPhotoUrlChanged: (String) -> Unit,
    onPhotoLoaded: () -> Unit,
    onPhotoLoadFailed: () -> Unit,
    onStartsAtChanged: (String) -> Unit,
    onEndsAtChanged: (String) -> Unit,
    onTimeZoneChanged: (String) -> Unit,
    onSave: () -> Unit,
    onRegenerateCode: () -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigationConsumed: () -> Unit
) {
    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            onNavigationConsumed()
            onSaved()
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
                VegasSecondaryButton(text = "Volver", onClick = onBack, modifier = Modifier.fillMaxWidth(0.38f))
                SyncStatusPill(networkStatus = uiState.networkStatus, fromCache = uiState.fromCache)
            }

            when {
                uiState.isLoading -> LoadingState(message = "Cargando configuracion")
                uiState.errorMessage != null && uiState.session == null -> MessageBanner(message = uiState.errorMessage)
                uiState.session == null -> EmptyState(
                    title = "Sesion no disponible",
                    message = "No existe, no eres miembro o todavia no se ha sincronizado."
                )
                !uiState.canEdit -> EmptyState(
                    title = "Solo propietario",
                    message = "Los miembros pueden consultar la sesion, pero no modificar la configuracion compartida."
                )
                else -> SettingsContent(
                    uiState = uiState,
                    onEventNameChanged = onEventNameChanged,
                    onGroomNameChanged = onGroomNameChanged,
                    onPhotoUrlChanged = onPhotoUrlChanged,
                    onPhotoLoaded = onPhotoLoaded,
                    onPhotoLoadFailed = onPhotoLoadFailed,
                    onStartsAtChanged = onStartsAtChanged,
                    onEndsAtChanged = onEndsAtChanged,
                    onTimeZoneChanged = onTimeZoneChanged,
                    onSave = onSave,
                    onRegenerateCode = onRegenerateCode
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    uiState: SessionSettingsUiState,
    onEventNameChanged: (String) -> Unit,
    onGroomNameChanged: (String) -> Unit,
    onPhotoUrlChanged: (String) -> Unit,
    onPhotoLoaded: () -> Unit,
    onPhotoLoadFailed: () -> Unit,
    onStartsAtChanged: (String) -> Unit,
    onEndsAtChanged: (String) -> Unit,
    onTimeZoneChanged: (String) -> Unit,
    onSave: () -> Unit,
    onRegenerateCode: () -> Unit
) {
    SectionTitle(eyebrow = "Propietario", title = "Configuracion")
    uiState.errorMessage?.let { MessageBanner(message = it) }
    uiState.successMessage?.let { MessageBanner(message = it, isError = false) }
    if (uiState.networkStatus == NetworkStatus.OFFLINE) {
        MessageBanner(message = "Sin conexion puedes revisar datos cacheados, pero no guardar cambios.")
    }

    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = "Datos del evento", style = MaterialTheme.typography.titleLarge)
            VegasTextField(
                value = uiState.eventName,
                onValueChange = onEventNameChanged,
                label = "Nombre del evento",
                error = uiState.eventNameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            VegasTextField(
                value = uiState.groomName,
                onValueChange = onGroomNameChanged,
                label = "Nombre del novio",
                error = uiState.groomNameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            VegasTextField(
                value = uiState.startsAt,
                onValueChange = onStartsAtChanged,
                label = "Inicio (dd/MM/yyyy HH:mm)",
                error = uiState.startsAtError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            VegasTextField(
                value = uiState.endsAt,
                onValueChange = onEndsAtChanged,
                label = "Fin (dd/MM/yyyy HH:mm)",
                error = uiState.endsAtError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            VegasTextField(
                value = uiState.timeZone,
                onValueChange = onTimeZoneChanged,
                label = "Zona horaria",
                error = uiState.timeZoneError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }
    }

    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = "Foto publica del novio", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Acepta URLs HTTPS y enlaces publicos habituales de Google Drive. No se guarda ninguna imagen en Firestore, solo la URL.",
                color = VegasColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            VegasTextField(
                value = uiState.photoUrl,
                onValueChange = onPhotoUrlChanged,
                label = "URL HTTPS de imagen",
                error = uiState.photoUrlError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
            )
            PhotoPreview(
                url = uiState.normalizedPhotoUrl,
                state = uiState.photoPreviewState,
                onPhotoLoaded = onPhotoLoaded,
                onPhotoLoadFailed = onPhotoLoadFailed
            )
        }
    }

    VegasCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = "Codigo de acceso", style = MaterialTheme.typography.titleLarge)
            Text(
                text = uiState.session?.joinCode.orEmpty(),
                color = VegasColors.Gold,
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Al regenerarlo, los miembros actuales conservan acceso. El codigo anterior deja de permitir nuevas incorporaciones.",
                color = VegasColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            VegasSecondaryButton(
                text = "Regenerar codigo",
                onClick = onRegenerateCode,
                enabled = uiState.canRegenerateCode,
                isLoading = uiState.isRegeneratingCode
            )
        }
    }

    VegasPrimaryButton(
        text = "Guardar configuracion",
        onClick = onSave,
        enabled = uiState.canSubmit,
        isLoading = uiState.isSaving
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun PhotoPreview(
    url: String?,
    state: PhotoPreviewState,
    onPhotoLoaded: () -> Unit,
    onPhotoLoadFailed: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(MaterialTheme.shapes.large)
            .background(VegasColors.CharcoalSoft)
            .border(1.dp, VegasColors.Gold.copy(alpha = 0.35f), MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = "Sin foto configurada",
                color = VegasColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = "Previsualizacion de la foto del novio",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { onPhotoLoaded() },
                onError = { onPhotoLoadFailed() }
            )
            when (state) {
                PhotoPreviewState.LOADING -> CircularProgressIndicator(color = VegasColors.Gold)
                PhotoPreviewState.ERROR -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VegasColors.Charcoal.copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se pudo cargar la imagen",
                        color = VegasColors.Error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                PhotoPreviewState.EMPTY,
                PhotoPreviewState.READY -> Unit
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionSettingsPreview() {
    DespedidaRuletaTheme {
        SessionSettingsScreen(
            uiState = SessionSettingsUiState(
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
                    memberCount = 1,
                    startsAtMillis = null,
                    endsAtMillis = null,
                    createdAtMillis = null,
                    updatedAtMillis = null,
                    members = emptyList()
                ),
                isLoading = false,
                eventName = "Despedida en Madrid",
                groomName = "Carlos",
                timeZone = "Europe/Madrid",
                networkStatus = NetworkStatus.ONLINE
            ),
            onEventNameChanged = {},
            onGroomNameChanged = {},
            onPhotoUrlChanged = {},
            onPhotoLoaded = {},
            onPhotoLoadFailed = {},
            onStartsAtChanged = {},
            onEndsAtChanged = {},
            onTimeZoneChanged = {},
            onSave = {},
            onRegenerateCode = {},
            onBack = {},
            onSaved = {},
            onNavigationConsumed = {}
        )
    }
}
