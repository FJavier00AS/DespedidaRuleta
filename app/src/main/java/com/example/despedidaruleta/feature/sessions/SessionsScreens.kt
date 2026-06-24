package com.example.despedidaruleta.feature.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
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
import com.example.despedidaruleta.core.designsystem.component.VegasTextField
import com.example.despedidaruleta.core.designsystem.theme.DespedidaRuletaTheme
import com.example.despedidaruleta.core.designsystem.theme.VegasColors
import com.example.despedidaruleta.domain.model.AuthUser
import com.example.despedidaruleta.domain.model.NetworkStatus
import com.example.despedidaruleta.domain.model.SessionRole
import com.example.despedidaruleta.domain.model.SessionStatus
import com.example.despedidaruleta.domain.model.SessionSummary

@Composable
fun SessionsListScreen(
    uiState: SessionsListUiState,
    onCreateSession: () -> Unit,
    onJoinSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onSignOut: () -> Unit
) {
    VegasBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SessionsHeader(
                    userName = uiState.user?.displayName.orEmpty(),
                    networkStatus = uiState.networkStatus,
                    fromCache = uiState.fromCache,
                    onSignOut = onSignOut
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val online = uiState.networkStatus == NetworkStatus.ONLINE
                    VegasPrimaryButton(
                        text = "Crear sesion",
                        onClick = onCreateSession,
                        enabled = online
                    )
                    VegasSecondaryButton(
                        text = "Unirme con codigo",
                        onClick = onJoinSession,
                        enabled = online
                    )
                    if (!online) {
                        MessageBanner(
                            message = "Sin conexion puedes consultar datos en cache, pero crear o unirte requiere sincronizacion.",
                            isError = true
                        )
                    }
                }
            }
            uiState.errorMessage?.let { error ->
                item { MessageBanner(message = error) }
            }
            when {
                uiState.isLoading -> item { LoadingState(message = "Cargando sesiones") }
                uiState.sessions.isEmpty() -> item {
                    EmptyState(
                        title = "Todavia no hay sesiones",
                        message = "Crea una despedida o pide a otro usuario el codigo de seis digitos."
                    )
                }
                else -> items(uiState.sessions, key = { it.id }) { session ->
                    SessionSummaryCard(session = session, onClick = { onOpenSession(session.id) })
                }
            }
        }
    }
}

@Composable
private fun SessionsHeader(
    userName: String,
    networkStatus: NetworkStatus,
    fromCache: Boolean,
    onSignOut: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Mesa privada", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = if (userName.isBlank()) "Sesion iniciada" else "Hola, $userName",
                    style = MaterialTheme.typography.bodyLarge,
                    color = VegasColors.TextSecondary
                )
            }
            SyncStatusPill(networkStatus = networkStatus, fromCache = fromCache)
        }
        VegasSecondaryButton(text = "Cerrar sesion", onClick = onSignOut, modifier = Modifier.fillMaxWidth(0.55f))
    }
}

@Composable
private fun SessionSummaryCard(
    session: SessionSummary,
    onClick: () -> Unit
) {
    VegasCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = session.eventName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(text = session.role.label, color = VegasColors.Gold, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = "Novio: ${session.groomName}",
                style = MaterialTheme.typography.bodyLarge,
                color = VegasColors.TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Codigo ${session.joinCode}", color = VegasColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(text = session.status.label, color = VegasColors.TextSecondary)
            }
        }
    }
}

@Composable
fun CreateSessionScreen(
    uiState: CreateSessionUiState,
    onEventNameChanged: (String) -> Unit,
    onGroomNameChanged: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    onNavigationConsumed: () -> Unit
) {
    LaunchedEffect(uiState.createdSessionId) {
        val sessionId = uiState.createdSessionId
        if (sessionId != null) {
            onNavigationConsumed()
            onNavigateToSession(sessionId)
        }
    }
    SessionFormContainer(
        eyebrow = "Nueva mesa",
        title = "Crear despedida",
        networkStatus = uiState.networkStatus,
        onBack = onBack
    ) {
        uiState.errorMessage?.let { MessageBanner(message = it) }
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        VegasPrimaryButton(
            text = "Crear y reservar codigo",
            onClick = onCreate,
            enabled = uiState.networkStatus == NetworkStatus.ONLINE,
            isLoading = uiState.isLoading
        )
    }
}

@Composable
fun JoinSessionScreen(
    uiState: JoinSessionUiState,
    onCodeChanged: (String) -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    onNavigationConsumed: () -> Unit
) {
    LaunchedEffect(uiState.joinedSessionId) {
        val sessionId = uiState.joinedSessionId
        if (sessionId != null) {
            onNavigationConsumed()
            onNavigateToSession(sessionId)
        }
    }
    SessionFormContainer(
        eyebrow = "Invitacion",
        title = "Unirse con codigo",
        networkStatus = uiState.networkStatus,
        onBack = onBack
    ) {
        uiState.errorMessage?.let { MessageBanner(message = it) }
        Text(
            text = "Introduce los seis digitos de la despedida. No se pueden listar codigos disponibles.",
            style = MaterialTheme.typography.bodyMedium,
            color = VegasColors.TextSecondary
        )
        VegasTextField(
            value = uiState.code,
            onValueChange = onCodeChanged,
            label = "Codigo de seis digitos",
            error = uiState.codeError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
        )
        VegasPrimaryButton(
            text = "Entrar en la sesion",
            onClick = onJoin,
            enabled = uiState.networkStatus == NetworkStatus.ONLINE,
            isLoading = uiState.isLoading
        )
    }
}

@Composable
private fun SessionFormContainer(
    eyebrow: String,
    title: String,
    networkStatus: NetworkStatus,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    VegasBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            VegasSecondaryButton(text = "Volver", onClick = onBack, modifier = Modifier.fillMaxWidth(0.45f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                SectionTitle(eyebrow = eyebrow, title = title, modifier = Modifier.weight(1f))
                SyncStatusPill(networkStatus = networkStatus, fromCache = false)
            }
            if (networkStatus == NetworkStatus.OFFLINE) {
                MessageBanner(message = "Esta operacion necesita conexion para evitar divergencias entre moviles.")
            }
            VegasCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionsListPreview() {
    DespedidaRuletaTheme {
        SessionsListScreen(
            uiState = SessionsListUiState(
                user = AuthUser(uid = "1", displayName = "Javi", email = "javi@example.com"),
                sessions = listOf(
                    SessionSummary(
                        id = "session",
                        eventName = "Despedida en Madrid",
                        groomName = "Carlos",
                        role = SessionRole.OWNER,
                        status = SessionStatus.ACTIVE,
                        joinCode = "123456",
                        updatedAtMillis = null
                    )
                ),
                isLoading = false
            ),
            onCreateSession = {},
            onJoinSession = {},
            onOpenSession = {},
            onSignOut = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateSessionPreview() {
    DespedidaRuletaTheme {
        CreateSessionScreen(
            uiState = CreateSessionUiState(),
            onEventNameChanged = {},
            onGroomNameChanged = {},
            onCreate = {},
            onBack = {},
            onNavigateToSession = {},
            onNavigationConsumed = {}
        )
    }
}
