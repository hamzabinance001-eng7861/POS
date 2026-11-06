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

// Soft violet on light grey, like the Dribbble POS designs. The accent colours keep their old
// names so every screen picks up the palette: Indigo = main violet, Violet = lighter violet.
val Indigo = Color(0xFF7B6BDF)
val Violet = Color(0xFF9A8CEB)
val Green = Color(0xFF2FB174)
val Amber = Color(0xFFE59A2B)
val Red = Color(0xFFEF5A6F)

/** The violet gradient used on the home summary card. */
fun heroBrush(): Brush = Brush.linearGradient(listOf(Color(0xFF6A59D6), Indigo, Color(0xFF9585EE)))

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE8FB),
    onPrimaryContainer = Color(0xFF2B2460),
    secondary = Red,
    background = Color(0xFFF4F4F8),
    onBackground = Color(0xFF16142E),
    surface = Color.White,
    onSurface = Color(0xFF16142E),
    surfaceVariant = Color(0xFFEFEFF6),
    onSurfaceVariant = Color(0xFF8A8AA3),
    outline = Color(0xFFE6E4F2),
    error = Red,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA99DF5),
    onPrimary = Color(0xFF1E1850),
    primaryContainer = Color(0xFF352F6B),
    onPrimaryContainer = Color(0xFFECE8FB),
    secondary = Color(0xFFFF8A9A),
    background = Color(0xFF121120),
    onBackground = Color(0xFFEDEBFA),
    surface = Color(0xFF1C1A31),
    onSurface = Color(0xFFEDEBFA),
    surfaceVariant = Color(0xFF272446),
    onSurfaceVariant = Color(0xFFA6A4C2),
    outline = Color(0xFF34315A),
    error = Color(0xFFFF8A9A),
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

/** The plain light-grey page behind every screen. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}
