package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

data class FrostedGlassConfig(
    val enabled: Boolean = false,
    val blurRadiusDp: Float = 10f,
    val backgroundOpacity: Float = 0.8f,
    val keyOpacity: Float = 0.2f,
) {
    fun normalized(defaults: FrostedGlassConfig = FrostedGlassConfig.defaults(true)): FrostedGlassConfig = copy(
        blurRadiusDp = (blurRadiusDp.takeIf { it.isFinite() } ?: defaults.blurRadiusDp).coerceIn(0f, 40f),
        backgroundOpacity = (backgroundOpacity.takeIf { it.isFinite() } ?: defaults.backgroundOpacity).coerceIn(0f, 1f),
        keyOpacity = (keyOpacity.takeIf { it.isFinite() } ?: defaults.keyOpacity).coerceIn(0f, 1f),
    )

    companion object {
        fun defaults(isDark: Boolean, enabled: Boolean = false) = FrostedGlassConfig(
            enabled = enabled, blurRadiusDp = 10f,
            backgroundOpacity = if (isDark) 0.8f else 0.2f,
            keyOpacity = if (isDark) 0.2f else 0.8f,
        )
    }
}

data class FrostedGlassProfiles(val light: FrostedGlassConfig, val dark: FrostedGlassConfig) {
    fun forAppearance(isDark: Boolean): FrostedGlassConfig = if (isDark) dark else light
}

/** Per-appearance glass parameters; choosing a theme remains the only enable switch. */
object FrostedGlassPreferences {
    const val KEY_ENABLED = "frosted_glass_enabled"
    const val KEY_BLUR_RADIUS = "frosted_glass_blur_radius_dp"
    const val KEY_BACKGROUND_OPACITY = "frosted_glass_background_opacity"
    const val KEY_KEY_OPACITY = "frosted_glass_key_opacity"
    private const val KEY_APPEARANCE_MIGRATED = "frosted_glass_appearance_migrated"
    private val parameterKeys = listOf(KEY_BLUR_RADIUS, KEY_BACKGROUND_OPACITY, KEY_KEY_OPACITY)
    private fun appearanceKey(key: String, dark: Boolean) = key + if (dark) "_dark" else "_light"

    val keys: Set<String> = (parameterKeys + parameterKeys.map { appearanceKey(it, true) } +
        parameterKeys.map { appearanceKey(it, false) } +
        listOf(KEY_ENABLED, KEY_APPEARANCE_MIGRATED, "keyboard_theme", "dark_mode")).toSet()

    fun isDark(context: Context): Boolean = when (SettingsPreferences.getEffectiveDarkMode(context)) {
        0 -> false
        2 -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> true
    }

    fun read(context: Context, isDark: Boolean = FrostedGlassPreferences.isDark(context)): FrostedGlassConfig =
        readProfiles(context).forAppearance(isDark)

    fun readProfiles(context: Context): FrostedGlassProfiles {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        migrateLegacy(context, prefs)
        val enabled = SettingsPreferences.getKeyboardTheme(context) == com.kingzcheung.xime.ui.theme.TransparentGlassTheme.ID
        fun readAppearance(dark: Boolean): FrostedGlassConfig {
            val defaults = FrostedGlassConfig.defaults(dark, enabled)
            return FrostedGlassConfig(
                enabled,
                prefs.getFloat(appearanceKey(KEY_BLUR_RADIUS, dark), defaults.blurRadiusDp),
                prefs.getFloat(appearanceKey(KEY_BACKGROUND_OPACITY, dark), defaults.backgroundOpacity),
                prefs.getFloat(appearanceKey(KEY_KEY_OPACITY, dark), defaults.keyOpacity),
            ).normalized(defaults)
        }
        return FrostedGlassProfiles(readAppearance(false), readAppearance(true))
    }

    private fun migrateLegacy(context: Context, prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_APPEARANCE_MIGRATED, false) || parameterKeys.none(prefs::contains)) return
        // Keep the original keys as a recoverable record. A known dark preset must
        // never seed the light appearance, even when first read from a light screen.
        val legacy = FrostedGlassConfig(
            blurRadiusDp = prefs.getFloat(KEY_BLUR_RADIUS, 24f),
            backgroundOpacity = prefs.getFloat(KEY_BACKGROUND_OPACITY, 0.55f),
            keyOpacity = prefs.getFloat(KEY_KEY_OPACITY, 0.22f),
        )
        val targetDark = legacy == FrostedGlassConfig.defaults(true) || isDark(context)
        val editor = prefs.edit().putBoolean(KEY_APPEARANCE_MIGRATED, true)
        if (parameterKeys.none { prefs.contains(appearanceKey(it, targetDark)) }) {
            writeAppearance(editor, legacy.normalized(FrostedGlassConfig.defaults(targetDark)), targetDark)
        }
        editor.apply()
    }

    fun save(context: Context, config: FrostedGlassConfig, isDark: Boolean = FrostedGlassPreferences.isDark(context)) {
        val previous = readProfiles(context)
        val updated = if (isDark) previous.copy(dark = config, light = previous.light.copy(enabled = config.enabled))
            else previous.copy(light = config, dark = previous.dark.copy(enabled = config.enabled))
        saveProfiles(context, updated)
    }

    fun saveProfiles(context: Context, profiles: FrostedGlassProfiles) {
        val enabled = profiles.dark.enabled
        val currentTheme = SettingsPreferences.getKeyboardTheme(context)
        val theme = if (enabled) com.kingzcheung.xime.ui.theme.TransparentGlassTheme.ID
            else if (currentTheme == com.kingzcheung.xime.ui.theme.TransparentGlassTheme.ID)
                SettingsPreferences.defaultKeyboardTheme else currentTheme
        val editor = SettingsPreferences.getPrefsPublic(context).edit()
            .putString("keyboard_theme", theme)
            .putBoolean(KEY_ENABLED, enabled)
            .putBoolean(KEY_APPEARANCE_MIGRATED, true)
        writeAppearance(editor, profiles.dark.normalized(FrostedGlassConfig.defaults(true)), true)
        writeAppearance(editor, profiles.light.normalized(FrostedGlassConfig.defaults(false)), false)
        editor.apply()
    }

    private fun writeAppearance(editor: SharedPreferences.Editor, config: FrostedGlassConfig, dark: Boolean) {
        editor.putFloat(appearanceKey(KEY_BLUR_RADIUS, dark), config.blurRadiusDp)
            .putFloat(appearanceKey(KEY_BACKGROUND_OPACITY, dark), config.backgroundOpacity)
            .putFloat(appearanceKey(KEY_KEY_OPACITY, dark), config.keyOpacity)
    }
}
