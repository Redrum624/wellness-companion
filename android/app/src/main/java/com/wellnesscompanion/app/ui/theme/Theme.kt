package com.wellnesscompanion.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = WaterText,
    onPrimary = WaterCardBg,
    primaryContainer = WaterCardBg,
    secondary = AccentTeal,
    background = WaterScreenBg,
    surface = WaterCardBg,
    onBackground = WaterText,
    onSurface = WaterText
)

@Composable
fun WellnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = WellnessTypography,
        content = content
    )
}
