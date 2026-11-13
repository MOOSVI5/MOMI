package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemePreference {
    SYSTEM, LIGHT, DARK
}

private val LumaDarkColorScheme = darkColorScheme(
    primary = SilkBlueLight,
    onPrimary = Color(0xFF081F36),
    primaryContainer = Color(0xFF1A3A59),
    onPrimaryContainer = SilkBlueSoft,
    secondary = MaroonBright,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = MaroonBright,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline
)

private val LumaLightColorScheme = lightColorScheme(
    primary = SilkBlue,
    onPrimary = Color.White,
    primaryContainer = SilkBlueSoft,
    onPrimaryContainer = SilkBlueDeep,
    secondary = MaroonDeep,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightTextPrimary,
    tertiary = MaroonVibrant,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightOutline
)

@Composable
fun LumaTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (darkTheme) LumaDarkColorScheme else LumaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LumaTypography,
        content = content
    )
}
