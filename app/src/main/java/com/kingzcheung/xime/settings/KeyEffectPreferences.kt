package com.kingzcheung.xime.settings

import android.content.SharedPreferences

/** Independent effects, with a read-only fallback for the former combined switch. */
object KeyEffectPreferences {
    const val GLOW = "key_glow_enabled"
    const val ANIMATION = "key_animation_enabled"

    fun glowEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(GLOW, false)

    fun animationEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(ANIMATION, glowEnabled(prefs))

    fun setGlowEnabled(prefs: SharedPreferences, enabled: Boolean) {
        // Freeze the old animation value before changing the legacy combined switch.
        prefs.edit().putBoolean(ANIMATION, animationEnabled(prefs))
            .putBoolean(GLOW, enabled).apply()
    }

    fun setAnimationEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(ANIMATION, enabled).apply()
    }
}
