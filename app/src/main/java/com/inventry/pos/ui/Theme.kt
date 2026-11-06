package com.inventry.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The accent colours keep their old names so every screen picks up the new palette:
// "Indigo" is now the deep teal, "Violet" a lighter teal, and "Red" the coral used for Purchase.
val Indigo = Color(0xFF0B5D75)
val Violet = Color(0xFF1A8CA8)
val Green = Color(0xFF2E9E5B)
val Amber = Color(0xFFD98A12)
val Red = Color(0xFFE5484D)

/** The deep teal gradient used on the home summary card. */
fun heroBrush(): Brush = Brush.linearGradient(listOf(Color(0xFF084A5E), Indigo, Color(0xFF127893)))

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9F1),
    onPrimaryContainer = Color(0xFF063A4A),
    secondary = Red,
    background = Color(0xFFE9F3F8),
    onBackground = Color(0xFF0F2A35),
    surface = Color.White,
    onSurface = Color(0xFF0F2A35),
    surfaceVariant = Color(0xFFE1EDF3),
    onSurfaceVariant = Color(0xFF5B7280),
    outline = Color(0xFFC9DCE6),
    error = Red,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF55B7D3),
    onPrimary = Color(0xFF04303D),
    primaryContainer = Color(0xFF0F4558),
    onPrimaryContainer = Color(0xFFD6E9F1),
    secondary = Color(0xFFFF7A7F),
    background = Color(0xFF0B1A22),
    onBackground = Color(0xFFE3EFF5),
    surface = Color(0xFF13262F),
    onSurface = Color(0xFFE3EFF5),
    surfaceVariant = Color(0xFF1B3340),
    onSurfaceVariant = Color(0xFF9DB4C0),
    outline = Color(0xFF2B4757),
    error = Color(0xFFFF7A7F),
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

/** The soft blue-to-white backdrop behind every screen. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val brush = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF0F2530), Color(0xFF0A161D)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFD5E9F3), Color(0xFFF1F8FB)))
    }
    Box(modifier.fillMaxSize().background(brush)) { content() }
}
