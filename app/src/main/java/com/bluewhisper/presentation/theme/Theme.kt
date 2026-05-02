package com.bluewhisper.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── BlueWhisper Design System Colors ─────────────────────────────
object BWColors {
    // Primary palette
    val Blue = Color(0xFF2E75FF)
    val BlueVariant = Color(0xFF1A5FE0)
    val Teal = Color(0xFF00D4C8)
    val Purple = Color(0xFFA371F7)
    val PurpleVariant = Color(0xFF7B3FF5)

    // Semantic
    val Red = Color(0xFFF85149)
    val Green = Color(0xFF3FB950)
    val Orange = Color(0xFFE86B00)
    val Amber = Color(0xFFD29922)

    // Surfaces — Dark theme (default)
    val Background = Color(0xFF0D1117)
    val Surface = Color(0xFF161B22)
    val SurfaceVariant = Color(0xFF1F2937)
    val Border = Color(0xFF21262D)

    // Text
    val OnBackground = Color(0xFFF0F6FC)
    val OnSurface = Color(0xFFF0F6FC)
    val TextSecondary = Color(0xFF8B949E)
    val TextTertiary = Color(0xFF4D5566)

    // Message bubbles
    val BubbleSent1 = Color(0xFF2E75FF)
    val BubbleSent2 = Color(0xFF7B3FF5)
    val BubbleReceived = Color(0xFF1F2937)

    // Progress bar colors
    val ProgressGreen  = Color(0xFF3FB950)
    val ProgressYellow = Color(0xFFD29922)
    val ProgressOrange = Color(0xFFE86B00)
    val ProgressRed    = Color(0xFFF85149)
}

// ── Dark Color Scheme ─────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary = BWColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0D2B5E),
    onPrimaryContainer = BWColors.OnBackground,
    secondary = BWColors.Teal,
    onSecondary = Color.White,
    tertiary = BWColors.Purple,
    onTertiary = Color.White,
    background = BWColors.Background,
    onBackground = BWColors.OnBackground,
    surface = BWColors.Surface,
    onSurface = BWColors.OnSurface,
    surfaceVariant = BWColors.SurfaceVariant,
    onSurfaceVariant = BWColors.TextSecondary,
    outline = BWColors.Border,
    error = BWColors.Red,
    onError = Color.White,
)

// ── Typography (Inter-like using system fonts) ────────────────────
val BWTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 34.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp
    ),
)

// ── Shapes ────────────────────────────────────────────────────────
val BWShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

// ── Theme Composable ──────────────────────────────────────────────
@Composable
fun BlueWhisperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // BlueWhisper is always dark — privacy-first design
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = BWTypography,
        shapes = BWShapes,
        content = content
    )
}

// ── Spacing constants ─────────────────────────────────────────────
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}
