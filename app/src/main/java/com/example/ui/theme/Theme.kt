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
    WHITE_GREY(
        displayName = "White + Grey",
        subtitle = "Crisp Minimal Studio",
        gradientColors = WhiteGreyGradientColors
    ),
    EMERALD_MAROON(
        displayName = "Emerald Green + Maroon",
        subtitle = "Deep Emerald & Rich Maroon",
        gradientColors = EmeraldMaroonGradientColors
    ),
    GOLD_EMERALD(
        displayName = "Gold + Emerald Green",
        subtitle = "Warm Gold & Deep Emerald",
        gradientColors = GoldEmeraldGradientColors
    )
}

val LocalMomiBrandGradient = staticCompositionLocalOf { EmeraldMaroonGradientColors }

object AppearancePrefs {
    private const val PREFS_NAME = "momi_appearance_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_COLOR_SCHEME = "color_scheme"
    private const val KEY_ADMIN_VIEW_MODE = "owner_admin_view_mode"
    private const val KEY_AUTO_SIGN_IN = "stay_signed_in_auto"
    private const val KEY_NOTIFICATION_SOUNDS = "notification_sounds_enabled"
    private const val KEY_NOTIF_PERM_PROMPTED = "notification_permission_prompted"
    private const val KEY_SUPA_ACCESS_TOKEN = "supabase_access_token"
    private const val KEY_SUPA_REFRESH_TOKEN = "supabase_refresh_token"
    private const val KEY_SUPA_USER_ID = "supabase_user_id"
    private const val KEY_SUPA_EMAIL = "supabase_email"
    private const val KEY_SUPA_DISPLAY_NAME = "supabase_display_name"
    private const val KEY_SUPA_AVATAR_URL = "supabase_avatar_url"
    private const val KEY_USER_GEMINI_API_KEY = "user_gemini_api_key"
    private const val KEY_CACHED_PROFILE_PREFIX = "cached_profile_json_"
    private const val KEY_CACHED_FEED_POSTS = "cached_feed_posts_json"

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
            .getString(KEY_COLOR_SCHEME, AppColorScheme.EMERALD_MAROON.name)
            ?.trim()
            .orEmpty()
        return when (raw) {
            "WHITE_GREY" -> AppColorScheme.WHITE_GREY
            "EMERALD_MAROON", "SILK_MAROON" -> AppColorScheme.EMERALD_MAROON
            "GOLD_EMERALD", "EMERALD_GOLD" -> AppColorScheme.GOLD_EMERALD
            else -> AppColorScheme.EMERALD_MAROON
        }
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

    fun loadUserGeminiApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_GEMINI_API_KEY, "")
            .orEmpty()
            .trim()
    }

    fun saveUserGeminiApiKey(context: Context, apiKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_GEMINI_API_KEY, apiKey.trim())
            .apply()
    }

    fun saveCachedProfileJson(context: Context, userId: String, profileJson: String) {
        if (userId.isBlank() || profileJson.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_CACHED_PROFILE_PREFIX$userId", profileJson)
            .apply()
    }

    fun loadCachedProfileJson(context: Context, userId: String): String? {
        if (userId.isBlank()) return null
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_CACHED_PROFILE_PREFIX$userId", null)
            ?.takeIf { it.isNotBlank() }
    }

    fun saveCachedFeedPostsJson(context: Context, postsJson: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CACHED_FEED_POSTS, postsJson)
            .apply()
    }

    fun loadCachedFeedPostsJson(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_FEED_POSTS, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun saveSupabaseSession(
        context: Context,
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String?,
        displayName: String? = null,
        avatarUrl: String? = null
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUPA_ACCESS_TOKEN, accessToken)
            .putString(KEY_SUPA_REFRESH_TOKEN, refreshToken)
            .putString(KEY_SUPA_USER_ID, userId)
            .putString(KEY_SUPA_EMAIL, email)
            .putString(KEY_SUPA_DISPLAY_NAME, displayName)
            .putString(KEY_SUPA_AVATAR_URL, avatarUrl)
            .apply()
    }

    fun loadSupabaseSessionRaw(context: Context): List<String?>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val access = prefs.getString(KEY_SUPA_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val userId = prefs.getString(KEY_SUPA_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val refresh = prefs.getString(KEY_SUPA_REFRESH_TOKEN, null).orEmpty()
        val email = prefs.getString(KEY_SUPA_EMAIL, null)
        val displayName = prefs.getString(KEY_SUPA_DISPLAY_NAME, null)
        val avatarUrl = prefs.getString(KEY_SUPA_AVATAR_URL, null)
        return listOf(access, refresh, userId, email, displayName, avatarUrl)
    }

    fun loadSavedSupabaseSession(context: Context): com.example.data.supabase.SupabaseAuthSession? {
        val parts = loadSupabaseSessionRaw(context) ?: return null
        return com.example.data.supabase.SupabaseAuthSession(
            accessToken = parts[0].orEmpty(),
            refreshToken = parts[1].orEmpty(),
            userId = parts[2].orEmpty(),
            email = parts[3],
            displayName = parts.getOrNull(4),
            avatarUrl = parts.getOrNull(5)
        )
    }

    fun restoreSupabaseSession(context: Context): com.example.data.supabase.SupabaseAuthSession? {
        val session = loadSavedSupabaseSession(context) ?: return null
        com.example.data.supabase.MomiSupabaseClient.activeSession = session
        return session
    }

    fun clearSupabaseSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SUPA_ACCESS_TOKEN)
            .remove(KEY_SUPA_REFRESH_TOKEN)
            .remove(KEY_SUPA_USER_ID)
            .remove(KEY_SUPA_EMAIL)
            .remove(KEY_SUPA_DISPLAY_NAME)
            .remove(KEY_SUPA_AVATAR_URL)
            .apply()
    }
}

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

private val EmeraldMaroonDarkColorScheme = darkColorScheme(
    primary = EmeraldGreenBright,
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = MaroonAccentBright,
    onSecondary = Color(0xFF2B0410),
    secondaryContainer = Color(0xFF3B0D1D),
    onSecondaryContainer = Color(0xFFFFE4E6),
    tertiary = MaroonAccentBright,
    onTertiary = Color.White,
    background = Color(0xFF06110E),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF0C1A16),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF142822),
    onSurfaceVariant = Color(0xFFA7C4BA),
    outline = Color(0xFF223F36),
    outlineVariant = Color(0xFF1D352E)
)

private val EmeraldMaroonLightColorScheme = lightColorScheme(
    primary = EmeraldGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = MaroonAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4E6),
    onSecondaryContainer = Color(0xFF4C0519),
    tertiary = MaroonAccent,
    onTertiary = Color.White,
    background = Color(0xFFF8FBF9),
    onBackground = Color(0xFF0E211B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E211B),
    surfaceVariant = Color(0xFFECF3EF),
    onSurfaceVariant = Color(0xFF446158),
    outline = Color(0xFFCFE0D8),
    outlineVariant = Color(0xFFD9E6DF)
)

private val GoldEmeraldDarkColorScheme = darkColorScheme(
    primary = GoldBright,
    onPrimary = Color(0xFF261900),
    primaryContainer = Color(0xFF4B3200),
    onPrimaryContainer = Color(0xFFFEF3C7),
    secondary = EmeraldBright,
    onSecondary = Color(0xFF032623),
    secondaryContainer = Color(0xFF0F3F3A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = EmeraldBright,
    onTertiary = Color(0xFF032623),
    background = Color(0xFF081210),
    onBackground = Color(0xFFF4F7F2),
    surface = Color(0xFF0F1C19),
    onSurface = Color(0xFFF4F7F2),
    surfaceVariant = Color(0xFF182B27),
    onSurfaceVariant = Color(0xFFA8C2BA),
    outline = Color(0xFF2A423B),
    outlineVariant = Color(0xFF223832)
)

private val GoldEmeraldLightColorScheme = lightColorScheme(
    primary = GoldAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = EmeraldPrimary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF0B4641),
    tertiary = EmeraldPrimary,
    onTertiary = Color.White,
    background = Color(0xFFFAF9F4),
    onBackground = Color(0xFF14211D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14211D),
    surfaceVariant = Color(0xFFF2EFE5),
    onSurfaceVariant = Color(0xFF4F5E58),
    outline = Color(0xFFDCD8CA),
    outlineVariant = Color(0xFFE5E1D4)
)

private fun resolveColorScheme(
    appColorScheme: AppColorScheme,
    darkTheme: Boolean
): ColorScheme = when (appColorScheme) {
    AppColorScheme.WHITE_GREY -> if (darkTheme) WhiteGreyDarkColorScheme else WhiteGreyLightColorScheme
    AppColorScheme.EMERALD_MAROON -> if (darkTheme) EmeraldMaroonDarkColorScheme else EmeraldMaroonLightColorScheme
    AppColorScheme.GOLD_EMERALD -> if (darkTheme) GoldEmeraldDarkColorScheme else GoldEmeraldLightColorScheme
}

@Composable
fun LumaTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    appColorScheme: AppColorScheme = AppColorScheme.EMERALD_MAROON,
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
