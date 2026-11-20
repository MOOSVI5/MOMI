package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemePreference {
    SYSTEM, LIGHT, DARK
}

enum class AppColorScheme(
    val displayName: String,
    val subtitle: String,
    val gradientColors: List<Color>
) {
    MIDNIGHT_SUNSET(
        displayName = "Midnight Sunset",
        subtitle = "OLED Black & Rose Amber",
        gradientColors = LumaGradientColors
    ),
    EMERALD_GOLD(
        displayName = "Emerald & Gold",
        subtitle = "Teal & Warm Gold",
        gradientColors = EmeraldGradientColors
    ),
    COBALT_INDIGO(
        displayName = "Electric Indigo",
        subtitle = "Cobalt Blue & Coral",
        gradientColors = IndigoGradientColors
    ),
    SILK_MAROON(
        displayName = "Silk & Maroon",
        subtitle = "Classic Momi Palette",
        gradientColors = ClassicGradientColors
    ),
    WHITE_GREY(
        displayName = "White & Grey",
        subtitle = "Crisp Minimal Studio",
        gradientColors = WhiteGreyGradientColors
    )
}

val LocalMomiBrandGradient = staticCompositionLocalOf { LumaGradientColors }

object AppearancePrefs {
    private const val PREFS_NAME = "momi_appearance_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_COLOR_SCHEME = "color_scheme"
    private const val KEY_ADMIN_VIEW_MODE = "owner_admin_view_mode"
    private const val KEY_AUTO_SIGN_IN = "stay_signed_in_auto"
    private const val KEY_NOTIFICATION_SOUNDS = "notification_sounds_enabled"
    private const val KEY_NOTIF_PERM_PROMPTED = "notification_permission_prompted"

    fun loadThemePreference(context: Context): ThemePreference {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, ThemePreference.SYSTEM.name)
        return runCatching { ThemePreference.valueOf(raw ?: ThemePreference.SYSTEM.name) }
            .getOrDefault(ThemePreference.SYSTEM)
    }

    fun saveThemePreference(context: Context, preference: ThemePreference) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, preference.name)
            .apply()
    }

    fun loadColorScheme(context: Context): AppColorScheme {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COLOR_SCHEME, AppColorScheme.MIDNIGHT_SUNSET.name)
        return runCatching { AppColorScheme.valueOf(raw ?: AppColorScheme.MIDNIGHT_SUNSET.name) }
            .getOrDefault(AppColorScheme.MIDNIGHT_SUNSET)
    }

    fun saveColorScheme(context: Context, scheme: AppColorScheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COLOR_SCHEME, scheme.name)
            .apply()
    }

    fun loadAdminViewMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ADMIN_VIEW_MODE, true)
    }

    fun saveAdminViewMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ADMIN_VIEW_MODE, enabled)
            .apply()
    }

    fun isAutoSignInEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SIGN_IN, true)
    }

    fun setAutoSignInEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SIGN_IN, enabled)
            .apply()
    }

    fun isNotificationSoundsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATION_SOUNDS, true)
    }

    fun setNotificationSoundsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_SOUNDS, enabled)
            .apply()
    }

    fun hasPromptedNotificationPermission(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIF_PERM_PROMPTED, false)
    }

    fun markNotificationPermissionPrompted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIF_PERM_PROMPTED, true)
            .apply()
    }
}

private val SunsetDarkColorScheme = darkColorScheme(
    primary = SunsetRoseBright,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C0D22),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = SunsetAmber,
    onSecondary = Color(0xFF281100),
    secondaryContainer = Color(0xFF1A1A20),
    onSecondaryContainer = Color(0xFFF5F5F7),
    tertiary = SunsetRoseBright,
    onTertiary = Color.White,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF0D0D11),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF1A1A22),
    onSurfaceVariant = Color(0xFFA8A8B8),
    outline = Color(0xFF2B2B36),
    outlineVariant = Color(0xFF262630)
)

private val SunsetLightColorScheme = lightColorScheme(
    primary = SunsetRose,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4EC),
    onPrimaryContainer = Color(0xFF6B0B26),
    secondary = SunsetAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3F4F6),
    onSecondaryContainer = Color(0xFF111827),
    tertiary = SunsetRose,
    onTertiary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFE5E7EB)
)

private val EmeraldDarkColorScheme = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = Color(0xFF032623),
    primaryContainer = Color(0xFF0F3F3A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = GoldBright,
    onSecondary = Color(0xFF261900),
    secondaryContainer = Color(0xFF162422),
    onSecondaryContainer = Color(0xFFECFEFF),
    tertiary = GoldBright,
    onTertiary = Color(0xFF261900),
    background = Color(0xFF071110),
    onBackground = Color(0xFFEEF7F6),
    surface = Color(0xFF0E1B1A),
    onSurface = Color(0xFFEEF7F6),
    surfaceVariant = Color(0xFF172B29),
    onSurfaceVariant = Color(0xFF9BC2BE),
    outline = Color(0xFF26423F),
    outlineVariant = Color(0xFF213A37)
)

private val EmeraldLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF0B4641),
    secondary = GoldAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F3),
    onSecondaryContainer = Color(0xFF11211F),
    tertiary = GoldAccent,
    onTertiary = Color.White,
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF11211F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF11211F),
    surfaceVariant = Color(0xFFEEF3F0),
    onSurfaceVariant = Color(0xFF48615D),
    outline = Color(0xFFD5E1DD),
    outlineVariant = Color(0xFFD5E1DD)
)

private val IndigoDarkColorScheme = darkColorScheme(
    primary = IndigoBright,
    onPrimary = Color(0xFF061B3E),
    primaryContainer = Color(0xFF173166),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = CoralBright,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF161E31),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = CoralBright,
    onTertiary = Color.White,
    background = Color(0xFF080D1A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF101729),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1A243D),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF283554),
    outlineVariant = Color(0xFF232E4A)
)

private val IndigoLightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = CoralAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = CoralAccent,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFD6DFEB),
    outlineVariant = Color(0xFFD6DFEB)
)

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

private val WhiteGreyDarkColorScheme = darkColorScheme(
    primary = WhiteGreyPrimaryBright,
    onPrimary = Color(0xFF111827),
    primaryContainer = Color(0xFF374151),
    onPrimaryContainer = Color(0xFFF9FAFB),
    secondary = WhiteGreySecondaryBright,
    onSecondary = Color(0xFF111827),
    secondaryContainer = Color(0xFF21242B),
    onSecondaryContainer = Color(0xFFE5E7EB),
    tertiary = Color(0xFFD1D5DB),
    onTertiary = Color(0xFF111827),
    background = Color(0xFF101114),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF17191D),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF252830),
    onSurfaceVariant = Color(0xFFB3B8C4),
    outline = Color(0xFF3A3F4B),
    outlineVariant = Color(0xFF2D313B)
)

private val WhiteGreyLightColorScheme = lightColorScheme(
    primary = WhiteGreyPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E7EB),
    onPrimaryContainer = Color(0xFF111827),
    secondary = WhiteGreySecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3F4F6),
    onSecondaryContainer = Color(0xFF1F2937),
    tertiary = Color(0xFF374151),
    onTertiary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB)
)

private fun resolveColorScheme(
    appColorScheme: AppColorScheme,
    darkTheme: Boolean
): ColorScheme = when (appColorScheme) {
    AppColorScheme.MIDNIGHT_SUNSET -> if (darkTheme) SunsetDarkColorScheme else SunsetLightColorScheme
    AppColorScheme.EMERALD_GOLD -> if (darkTheme) EmeraldDarkColorScheme else EmeraldLightColorScheme
    AppColorScheme.COBALT_INDIGO -> if (darkTheme) IndigoDarkColorScheme else IndigoLightColorScheme
    AppColorScheme.SILK_MAROON -> if (darkTheme) LumaDarkColorScheme else LumaLightColorScheme
    AppColorScheme.WHITE_GREY -> if (darkTheme) WhiteGreyDarkColorScheme else WhiteGreyLightColorScheme
}

@Composable
fun LumaTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    appColorScheme: AppColorScheme = AppColorScheme.MIDNIGHT_SUNSET,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = resolveColorScheme(appColorScheme, darkTheme)

    CompositionLocalProvider(
        LocalMomiBrandGradient provides appColorScheme.gradientColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LumaTypography,
            content = content
        )
    }
}
