package com.inventry.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

// Soft violet on light grey. The accent colours keep their old names so every screen picks up the palette.
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
    background = Color(0xFFF5F5F9),
    onBackground = Color(0xFF16142E),
    surface = Color.White,
    onSurface = Color(0xFF16142E),
    surfaceVariant = Color(0xFFEFEFF6),
    onSurfaceVariant = Color(0xFF7C7C96),
    outline = Color(0xFFE8E6F3),
    error = Red,
    // Dialogs, menus and sheets use these. Plain white instead of the default purple tint.
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF1F0FA),
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
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF1C1A31),
    surfaceContainerLow = Color(0xFF1C1A31),
    surfaceContainer = Color(0xFF1C1A31),
    surfaceContainerHigh = Color(0xFF1C1A31),
    surfaceContainerHighest = Color(0xFF272446),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun PosTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    // A phone set to very large text would make every screen overflow, so text grows at most a little.
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = density.fontScale.coerceAtMost(1.08f))) {
        val scheme = if (isSystemInDarkTheme()) DarkColors else LightColors
        MaterialTheme(colorScheme = scheme, shapes = AppShapes) {
            // A steady medium weight makes thin phone fonts easy to read.
            ProvideTextStyle(TextStyle(fontWeight = FontWeight.Medium, color = scheme.onSurface)) { content() }
        }
    }
}

/** The plain light-grey page behind every screen. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}
