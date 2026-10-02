package com.kingzcheung.xime.settings

import android.content.Context

data class FrostedGlassConfig(
    val enabled: Boolean = false,
    val blurRadiusDp: Float = 24f,
    val backgroundOpacity: Float = 0.55f,
    val keyOpacity: Float = 0.22f,
) {
    fun normalized(): FrostedGlassConfig = copy(
        blurRadiusDp = (blurRadiusDp.takeIf { it.isFinite() } ?: 24f).coerceIn(0f, 40f),
        backgroundOpacity = (backgroundOpacity.takeIf { it.isFinite() } ?: 0.55f).coerceIn(0f, 1f),
        keyOpacity = (keyOpacity.takeIf { it.isFinite() } ?: 0.22f).coerceIn(0f, 1f),
    )
}

/** 透明玻璃主题参数；旧开关仅用于迁移与兼容，主题选择决定是否启用。 */
object FrostedGlassPreferences {
    const val KEY_ENABLED = "frosted_glass_enabled"
    const val KEY_BLUR_RADIUS = "frosted_glass_blur_radius_dp"
    const val KEY_BACKGROUND_OPACITY = "frosted_glass_background_opacity"
    const val KEY_KEY_OPACITY = "frosted_glass_key_opacity"

    val keys: Set<String> = setOf(KEY_ENABLED, KEY_BLUR_RADIUS, KEY_BACKGROUND_OPACITY, KEY_KEY_OPACITY, "keyboard_theme")

    fun read(context: Context): FrostedGlassConfig {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val defaults = FrostedGlassConfig()
        return FrostedGlassConfig(
            enabled = SettingsPreferences.getKeyboardTheme(context) == com.kingzcheung.xime.ui.theme.TransparentGlassTheme.ID,
            blurRadiusDp = prefs.getFloat(KEY_BLUR_RADIUS, defaults.blurRadiusDp),
            backgroundOpacity = prefs.getFloat(KEY_BACKGROUND_OPACITY, defaults.backgroundOpacity),
            keyOpacity = prefs.getFloat(KEY_KEY_OPACITY, defaults.keyOpacity),
        ).normalized()
    }

    fun save(context: Context, config: FrostedGlassConfig) {
        val normalized = config.normalized()
        val currentTheme = SettingsPreferences.getKeyboardTheme(context)
        val theme = if (normalized.enabled) com.kingzcheung.xime.ui.theme.TransparentGlassTheme.ID
            else if (currentTheme == com.kingzcheung.xime.ui.theme.TransparentGlassTheme.ID)
                SettingsPreferences.defaultKeyboardTheme else currentTheme
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString("keyboard_theme", theme)
            .putBoolean(KEY_ENABLED, normalized.enabled)
            .putFloat(KEY_BLUR_RADIUS, normalized.blurRadiusDp)
            .putFloat(KEY_BACKGROUND_OPACITY, normalized.backgroundOpacity)
            .putFloat(KEY_KEY_OPACITY, normalized.keyOpacity)
            .apply()
    }
}
