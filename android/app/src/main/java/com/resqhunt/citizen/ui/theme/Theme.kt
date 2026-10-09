package com.resqhunt.citizen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = CardSurface,
    secondary = TealAccent,
    onSecondary = NavyDark,
    tertiary = EmergencyRed,
    background = CanvasBg,
    surface = CardSurface,
    onSurface = InkText
)

@Composable
fun ResQhunTTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
