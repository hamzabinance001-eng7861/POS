package com.inventry.pos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Indigo = Color(0xFF4F46E5)
val Violet = Color(0xFF7C3AED)
val Green = Color(0xFF16A34A)
val Amber = Color(0xFFD97706)
val Red = Color(0xFFDC2626)

/** The gradient used on the top bar, the login screen and the home summary. */
fun heroBrush(): Brush = Brush.linearGradient(listOf(Color(0xFF4338CA), Indigo, Violet))

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Violet,
    background = Color(0xFFF5F6FB),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF0F9),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFD5DAEA),
    error = Red,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AA2FF),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE4E7FF),
    secondary = Color(0xFFB69CFF),
    background = Color(0xFF0E1120),
    onBackground = Color(0xFFE8EAF6),
    surface = Color(0xFF171A2D),
    onSurface = Color(0xFFE8EAF6),
    surfaceVariant = Color(0xFF232745),
    onSurfaceVariant = Color(0xFF9CA6C6),
    outline = Color(0xFF343A5E),
    error = Color(0xFFF87171),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun PosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
