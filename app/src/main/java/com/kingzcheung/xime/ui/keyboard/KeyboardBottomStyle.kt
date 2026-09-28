package com.kingzcheung.xime.ui.keyboard

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kingzcheung.xime.settings.SettingsPreferences

/** Shared persisted choice; independent of keyboard dimensions and insets. */
@Composable
internal fun rememberRoundedKeyboardBottom(): Boolean {
    val context = LocalContext.current
    var rounded by remember(context) { mutableStateOf(SettingsPreferences.roundedKeyboardBottom(context)) }
    DisposableEffect(context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == SettingsPreferences.KEY_ROUNDED_KEYBOARD_BOTTOM) {
                rounded = SettingsPreferences.roundedKeyboardBottom(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return rounded
}

internal fun useDarkNavigationIcons(rounded: Boolean, systemDark: Boolean, keyTextLuminance: Float): Boolean =
    if (rounded) !systemDark else keyTextLuminance < 0.5f