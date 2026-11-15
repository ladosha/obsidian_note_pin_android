package com.vladmas.notepinobsidian.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Obsidian's default theme: its purple accent on near-black, or on white in light mode. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8A6CEF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2D6E),
    onPrimaryContainer = Color(0xFFE7DFFF),
    secondary = Color(0xFFB39DFF),
    onSecondary = Color(0xFF1E1E1E),
    secondaryContainer = Color(0xFF332A55),
    onSecondaryContainer = Color(0xFFE7DFFF),
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFDADADA),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFDADADA),
    surfaceVariant = Color(0xFF2B2B2B),
    onSurfaceVariant = Color(0xFFA6A6A6),
    surfaceContainerLowest = Color(0xFF161616),
    surfaceContainerLow = Color(0xFF222222),
    surfaceContainer = Color(0xFF262626),
    surfaceContainerHigh = Color(0xFF2E2E2E),
    surfaceContainerHighest = Color(0xFF363636),
    outline = Color(0xFF4D4D4D),
    outlineVariant = Color(0xFF363636),
    error = Color(0xFFFB464C),
    onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7552EC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE3FF),
    onPrimaryContainer = Color(0xFF2B1A6B),
    secondary = Color(0xFF8A6CEF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7FF),
    onSecondaryContainer = Color(0xFF2B1A6B),
    background = Color.White,
    onBackground = Color(0xFF222222),
    surface = Color.White,
    onSurface = Color(0xFF222222),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF5C5C5C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF6F6F6),
    surfaceContainerHigh = Color(0xFFEFEFEF),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    outline = Color(0xFFCCCCCC),
    outlineVariant = Color(0xFFE3E3E3),
    error = Color(0xFFE93147),
    onError = Color.White,
)

val OkGreen = Color(0xFF4CAF6E)
val AttentionOrange = Color(0xFFF2832B)
val KofiBlue = Color(0xFF29ABE0)

@Composable
fun NotePinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
