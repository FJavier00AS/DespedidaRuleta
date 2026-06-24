package com.example.despedidaruleta.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.despedidaruleta.core.designsystem.theme.DespedidaRuletaTheme
import com.example.despedidaruleta.core.designsystem.theme.VegasColors
import com.example.despedidaruleta.domain.model.NetworkStatus

@Composable
fun VegasBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VegasColors.Charcoal)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = VegasColors.Gold.copy(alpha = 0.12f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.05f, size.height * 0.05f)
            )
            drawCircle(
                color = VegasColors.Red.copy(alpha = 0.15f),
                radius = size.minDimension * 0.45f,
                center = Offset(size.width * 0.95f, size.height * 0.24f)
            )
            drawCircle(
                color = VegasColors.NeonCyan.copy(alpha = 0.08f),
                radius = size.minDimension * 0.50f,
                center = Offset(size.width * 0.50f, size.height * 1.05f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            VegasColors.Charcoal.copy(alpha = 0.72f),
                            VegasColors.Charcoal
                        )
                    )
                )
        )
        CompositionLocalProvider(LocalContentColor provides VegasColors.TextPrimary) {
            content()
        }
    }
}

@Composable
fun VegasCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(VegasColors.Gold.copy(alpha = 0.65f), Color.Transparent)),
                shape = RoundedCornerShape(28.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = VegasColors.Card.copy(alpha = 0.92f),
            contentColor = VegasColors.TextPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        Box(modifier = Modifier.padding(20.dp)) {
            CompositionLocalProvider(LocalContentColor provides VegasColors.TextPrimary) {
                content()
            }
        }
    }
}

@Composable
fun VegasPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VegasColors.Gold,
            contentColor = Color(0xFF211400),
            disabledContainerColor = VegasColors.Gold.copy(alpha = 0.32f),
            disabledContentColor = VegasColors.TextSecondary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFF211400),
                strokeWidth = 2.dp
            )
        } else {
            Text(text = text.uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun VegasSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, VegasColors.Gold.copy(alpha = 0.62f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = VegasColors.TextPrimary,
            disabledContentColor = VegasColors.TextSecondary.copy(alpha = 0.7f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = VegasColors.Gold, strokeWidth = 2.dp)
        } else if (content != null) {
            content()
        } else {
            Text(text = text.uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun VegasTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VegasColors.Gold,
            unfocusedBorderColor = VegasColors.TextSecondary.copy(alpha = 0.55f),
            focusedLabelColor = VegasColors.Gold,
            unfocusedLabelColor = VegasColors.TextSecondary,
            cursorColor = VegasColors.Gold,
            focusedTextColor = VegasColors.TextPrimary,
            unfocusedTextColor = VegasColors.TextPrimary,
            focusedContainerColor = VegasColors.CharcoalSoft.copy(alpha = 0.85f),
            unfocusedContainerColor = VegasColors.CharcoalSoft.copy(alpha = 0.85f),
            errorBorderColor = VegasColors.Error,
            errorLabelColor = VegasColors.Error,
            errorTextColor = VegasColors.TextPrimary
        ),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun MessageBanner(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = true
) {
    val accent = if (isError) VegasColors.Error else VegasColors.Success
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            text = message,
            color = VegasColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun SyncStatusPill(
    networkStatus: NetworkStatus,
    fromCache: Boolean,
    modifier: Modifier = Modifier
) {
    val (text, color) = when {
        networkStatus == NetworkStatus.OFFLINE -> "Sin conexion" to VegasColors.Error
        fromCache -> "Datos de cache" to VegasColors.Warning
        else -> "Conectado" to VegasColors.Success
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    VegasCard(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = VegasColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LoadingState(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(color = VegasColors.Gold)
        Text(text = message, color = VegasColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SectionTitle(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = VegasColors.TextPrimary
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = eyebrow.uppercase(),
            color = VegasColors.Gold,
            style = MaterialTheme.typography.labelLarge
        )
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = titleColor)
    }
}

@Preview(showBackground = true)
@Composable
private fun VegasComponentsPreview() {
    DespedidaRuletaTheme {
        VegasBackground {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SectionTitle(eyebrow = "Despedida", title = "Las Vegas privada")
                VegasTextField(value = "", onValueChange = {}, label = "Correo")
                MessageBanner(message = "Mensaje de error controlado")
                SyncStatusPill(networkStatus = NetworkStatus.ONLINE, fromCache = false)
                VegasPrimaryButton(text = "Continuar", onClick = {})
                EmptyState(title = "Sin sesiones", message = "Crea una sesion o unete con un codigo.")
            }
        }
    }
}
