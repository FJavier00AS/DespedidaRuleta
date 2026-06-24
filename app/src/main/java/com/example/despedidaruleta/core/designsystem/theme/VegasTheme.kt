package com.example.despedidaruleta.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object VegasColors {
    val Charcoal = Color(0xFF09090B)
    val CharcoalSoft = Color(0xFF141216)
    val Card = Color(0xFF1D1719)
    val CardElevated = Color(0xFF281D20)
    val Gold = Color(0xFFFFC857)
    val GoldDeep = Color(0xFFC58A1C)
    val Red = Color(0xFFB9162F)
    val RedDeep = Color(0xFF6F0D1A)
    val NeonCyan = Color(0xFF40E0D0)
    val Question = Color(0xFF1FB8FF)
    val Challenge = Color(0xFFFFA928)
    val Punishment = Color(0xFFFF355D)
    val TextPrimary = Color(0xFFFFF7E1)
    val TextSecondary = Color(0xFFCDBFA7)
    val Success = Color(0xFF4DDC8A)
    val Warning = Color(0xFFFFB84D)
    val Error = Color(0xFFFF6B6B)
}

private val VegasColorScheme = darkColorScheme(
    primary = VegasColors.Gold,
    onPrimary = Color(0xFF211400),
    secondary = VegasColors.Red,
    onSecondary = Color.White,
    tertiary = VegasColors.NeonCyan,
    onTertiary = Color(0xFF001D1C),
    background = VegasColors.Charcoal,
    onBackground = VegasColors.TextPrimary,
    surface = VegasColors.Card,
    onSurface = VegasColors.TextPrimary,
    surfaceVariant = VegasColors.CardElevated,
    onSurfaceVariant = VegasColors.TextSecondary,
    error = VegasColors.Error,
    onError = Color(0xFF220000),
    outline = Color(0xFF7B6845)
)

private val VegasTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Black,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.2.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    )
)

private val VegasShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp)
)

@Composable
fun DespedidaRuletaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VegasColorScheme,
        typography = VegasTypography,
        shapes = VegasShapes,
        content = content
    )
}
