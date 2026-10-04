package com.anti.theft.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Color4F7C,
    onPrimary = ColorFFFF,
    primaryContainer = Color1C24,
    onPrimaryContainer = ColorF5F7,
    secondary = Color22D3,
    onSecondary = Color0A0F,
    tertiary = Color34D3,
    onTertiary = Color0A0F,
    error = ColorFF4D,
    onError = ColorFFFF,
    background = Color0A0F,
    onBackground = ColorF5F7,
    surface = Color131A,
    onSurface = ColorF5F7,
    surfaceVariant = Color1C24,
    onSurfaceVariant = Color9AA4,
    surfaceContainerLowest = Color0A0F,
    surfaceContainerLow = Color131A,
    surfaceContainer = Color131A,
    surfaceContainerHigh = Color1C24,
    surfaceContainerHighest = Color1C24,
    outline = Color2A33,
    outlineVariant = Color2A33,
    inverseSurface = ColorF5F7,
    inverseOnSurface = Color0A0F,
    scrim = Color0A0F,
)

private val LightColors = lightColorScheme(
    primary = Color4F7C,
    onPrimary = ColorFFFF,
    primaryContainer = ColorF0F3,
    onPrimaryContainer = Color0A0F,
    secondary = Color22D3,
    onSecondary = Color0A0F,
    tertiary = Color34D3,
    onTertiary = Color0A0F,
    error = ColorFF4D,
    onError = ColorFFFF,
    background = ColorF0F3,
    onBackground = Color0A0F,
    surface = ColorFFFF,
    onSurface = Color0A0F,
    surfaceVariant = ColorF0F3,
    onSurfaceVariant = Color5B65,
    surfaceContainerLowest = ColorFFFF,
    surfaceContainerLow = ColorFFFF,
    surfaceContainer = ColorF0F3,
    surfaceContainerHigh = ColorF0F3,
    surfaceContainerHighest = ColorDFE5,
    outline = ColorDFE5,
    outlineVariant = ColorDFE5,
    inverseSurface = Color0A0F,
    inverseOnSurface = ColorF5F7,
    scrim = Color0A0F,
)

@Composable
fun AntiTheftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
