package com.bestie.lectoraao3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFD94F8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E8),
    onPrimaryContainer = Color(0xFF4A1030),
    secondary = Color(0xFF9C6ADE),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDDFF),
    onSecondaryContainer = Color(0xFF2E1658),
    tertiary = Color(0xFFE8869E),
    background = Color(0xFFFFF8FB),
    onBackground = Color(0xFF261A20),
    surface = Color(0xFFFFF8FB),
    onSurface = Color(0xFF261A20),
    surfaceVariant = Color(0xFFF6E3EC),
    onSurfaceVariant = Color(0xFF6E5562),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1F7),
    surfaceContainer = Color(0xFFFCEAF2),
    surfaceContainerHigh = Color(0xFFF8E3EC),
    surfaceContainerHighest = Color(0xFFF3DCE6),
    outline = Color(0xFFC9A9B8),
    outlineVariant = Color(0xFFEBD3DE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF2A7D0),
    onPrimary = Color(0xFF4A1030),
    primaryContainer = Color(0xFF6B2148),
    onPrimaryContainer = Color(0xFFFFD9E8),
    secondary = Color(0xFFCFB5FF),
    onSecondary = Color(0xFF2E1658),
    secondaryContainer = Color(0xFF4A3270),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFFF5B4C4),
    background = Color(0xFF17121A),
    onBackground = Color(0xFFEDE0E8),
    surface = Color(0xFF17121A),
    onSurface = Color(0xFFEDE0E8),
    surfaceVariant = Color(0xFF3A2D35),
    onSurfaceVariant = Color(0xFFD7C0CC),
    surfaceContainerLowest = Color(0xFF120E14),
    surfaceContainerLow = Color(0xFF201922),
    surfaceContainer = Color(0xFF261E28),
    surfaceContainerHigh = Color(0xFF30272F),
    surfaceContainerHighest = Color(0xFF3A3039),
    outline = Color(0xFF9E8794),
    outlineVariant = Color(0xFF4D3E47),
)

@Composable
fun LectoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

data class ReaderColors(val bg: Color, val fg: Color, val accent: Color, val note: Color, val dark: Boolean)

val READER_THEMES = listOf(
    "rosa" to "Rosita",
    "claro" to "Claro",
    "sepia" to "Sepia",
    "noche" to "Noche",
    "negro" to "Negro",
)

fun readerColors(theme: String) = when (theme) {
    "claro" -> ReaderColors(Color(0xFFFFFFFF), Color(0xFF1E1E1E), Color(0xFFD94F8C), Color(0xFFF4F0F2), false)
    "sepia" -> ReaderColors(Color(0xFFF4ECD8), Color(0xFF4B3B2A), Color(0xFFA0522D), Color(0xFFEADFC6), false)
    "noche" -> ReaderColors(Color(0xFF17121A), Color(0xFFE9DFEC), Color(0xFFF2A7D0), Color(0xFF261E2A), true)
    "negro" -> ReaderColors(Color(0xFF000000), Color(0xFFD8D8D8), Color(0xFFF2A7D0), Color(0xFF141414), true)
    else -> ReaderColors(Color(0xFFFFF0F6), Color(0xFF3D1530), Color(0xFFD94F8C), Color(0xFFFFE1EE), false)
}
