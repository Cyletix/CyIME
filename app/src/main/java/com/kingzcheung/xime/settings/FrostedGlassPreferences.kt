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

/** 独立保存磨砂参数，输入设置的保存不会覆盖效果选择。 */
object FrostedGlassPreferences {
    const val KEY_ENABLED = "frosted_glass_enabled"
    const val KEY_BLUR_RADIUS = "frosted_glass_blur_radius_dp"
    const val KEY_BACKGROUND_OPACITY = "frosted_glass_background_opacity"
    const val KEY_KEY_OPACITY = "frosted_glass_key_opacity"

    val keys: Set<String> = setOf(KEY_ENABLED, KEY_BLUR_RADIUS, KEY_BACKGROUND_OPACITY, KEY_KEY_OPACITY)

    fun read(context: Context): FrostedGlassConfig {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val defaults = FrostedGlassConfig()
        return FrostedGlassConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            blurRadiusDp = prefs.getFloat(KEY_BLUR_RADIUS, defaults.blurRadiusDp),
            backgroundOpacity = prefs.getFloat(KEY_BACKGROUND_OPACITY, defaults.backgroundOpacity),
            keyOpacity = prefs.getFloat(KEY_KEY_OPACITY, defaults.keyOpacity),
        ).normalized()
    }

    fun save(context: Context, config: FrostedGlassConfig) {
        val normalized = config.normalized()
        SettingsPreferences.getPrefsPublic(context).edit()
            .putBoolean(KEY_ENABLED, normalized.enabled)
            .putFloat(KEY_BLUR_RADIUS, normalized.blurRadiusDp)
            .putFloat(KEY_BACKGROUND_OPACITY, normalized.backgroundOpacity)
            .putFloat(KEY_KEY_OPACITY, normalized.keyOpacity)
            .apply()
    }
}
