package com.aivigil.unitconverter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = NeonBackground,
    surface = NeonSurface,
    surfaceContainer = NeonSurface,
    primary = NeonPrimary,
    secondary = NeonSecondary,
    onBackground = NeonTextPrimary,
    onSurface = NeonTextPrimary
)

@Composable
fun UnitConverterTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}