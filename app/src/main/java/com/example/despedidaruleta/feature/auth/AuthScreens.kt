package com.example.despedidaruleta.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.despedidaruleta.core.designsystem.component.MessageBanner
import com.example.despedidaruleta.core.designsystem.component.SectionTitle
import com.example.despedidaruleta.core.designsystem.component.VegasBackground
import com.example.despedidaruleta.core.designsystem.component.VegasCard
import com.example.despedidaruleta.core.designsystem.component.VegasPrimaryButton
import com.example.despedidaruleta.core.designsystem.component.VegasSecondaryButton
import com.example.despedidaruleta.core.designsystem.component.VegasTextField
import com.example.despedidaruleta.core.designsystem.theme.DespedidaRuletaTheme
import com.example.despedidaruleta.core.designsystem.theme.VegasColors

@Composable
fun WelcomeScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit
) {
    VegasBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = "Despedida Ruleta",
                    style = MaterialTheme.typography.displayLarge,
                    color = VegasColors.Gold
                )
                Text(
                    text = "Una sesion compartida, un codigo y todos los moviles viendo el mismo juego.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = VegasColors.TextSecondary
                )
            }
            VegasCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Acceso privado",
                        style = MaterialTheme.typography.titleLarge,
                        color = VegasColors.Gold
                    )
                    Text(
                        text = "Registrate con correo o inicia sesion para crear y compartir sesiones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VegasColors.TextSecondary
                    )
                    VegasPrimaryButton(text = "Iniciar sesion", onClick = onLogin)
                    VegasSecondaryButton(text = "Crear cuenta", onClick = onRegister)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onForgotPassword: () -> Unit,
    onBack: () -> Unit,
    onAuthenticated: () -> Unit
) {
    LaunchedEffect(uiState.completed) {
        if (uiState.completed) onAuthenticated()
    }
    AuthFormContainer(
        eyebrow = "Acceso",
        title = "Vuelve al casino",
        onBack = onBack
    ) {
        uiState.errorMessage?.let { MessageBanner(message = it) }
        VegasTextField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            label = "Correo",
            error = uiState.emailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        VegasTextField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            label = "Contrasena",
            error = uiState.passwordError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        )
        VegasPrimaryButton(
            text = "Entrar",
            onClick = onLogin,
            isLoading = uiState.isLoading
        )
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(text = "He olvidado la contrasena", color = VegasColors.Gold)
        }
    }
}

@Composable
fun RegisterScreen(
    uiState: RegisterUiState,
    onDisplayNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit,
    onAuthenticated: () -> Unit
) {
    LaunchedEffect(uiState.completed) {
        if (uiState.completed) onAuthenticated()
    }
    AuthFormContainer(
        eyebrow = "Registro",
        title = "Crea tu pase VIP",
        onBack = onBack
    ) {
        uiState.errorMessage?.let { MessageBanner(message = it) }
        VegasTextField(
            value = uiState.displayName,
            onValueChange = onDisplayNameChanged,
            label = "Nombre visible",
            error = uiState.displayNameError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        VegasTextField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            label = "Correo",
            error = uiState.emailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        VegasTextField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            label = "Contrasena",
            error = uiState.passwordError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        )
        VegasPrimaryButton(
            text = "Crear cuenta",
            onClick = onRegister,
            isLoading = uiState.isLoading
        )
    }
}

@Composable
fun ResetPasswordScreen(
    uiState: ResetPasswordUiState,
    onEmailChanged: (String) -> Unit,
    onSendReset: () -> Unit,
    onBack: () -> Unit
) {
    AuthFormContainer(
        eyebrow = "Recuperacion",
        title = "Restablece tu acceso",
        onBack = onBack
    ) {
        uiState.errorMessage?.let { MessageBanner(message = it) }
        uiState.successMessage?.let { MessageBanner(message = it, isError = false) }
        Text(
            text = "Introduce tu correo y Firebase enviara un enlace de recuperacion.",
            style = MaterialTheme.typography.bodyMedium,
            color = VegasColors.TextSecondary
        )
        VegasTextField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            label = "Correo",
            error = uiState.emailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)
        )
        VegasPrimaryButton(
            text = "Enviar correo",
            onClick = onSendReset,
            isLoading = uiState.isLoading
        )
    }
}

@Composable
private fun AuthFormContainer(
    eyebrow: String,
    title: String,
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
            SectionTitle(eyebrow = eyebrow, title = title, titleColor = VegasColors.Gold)
            VegasCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            }
            Text(
                text = "Correo y contrasena se gestionan con Firebase Authentication. La contrasena no se guarda en Firestore.",
                style = MaterialTheme.typography.bodyMedium,
                color = VegasColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomePreview() {
    DespedidaRuletaTheme { WelcomeScreen(onLogin = {}, onRegister = {}) }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    DespedidaRuletaTheme {
        LoginScreen(
            uiState = LoginUiState(),
            onEmailChanged = {},
            onPasswordChanged = {},
            onLogin = {},
            onForgotPassword = {},
            onBack = {},
            onAuthenticated = {}
        )
    }
}
