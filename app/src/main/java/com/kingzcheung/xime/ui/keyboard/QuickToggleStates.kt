package com.kingzcheung.xime.ui.keyboard

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kingzcheung.xime.settings.SettingsPreferences

/** 菜单/工具栏开关项的当前状态；设置页改动后立即反映到键盘上。 */
internal data class QuickToggleStates(val sound: Boolean, val vibration: Boolean, val incognito: Boolean)

@Composable
internal fun rememberQuickToggleStates(): QuickToggleStates {
    val context = LocalContext.current
    fun read() = QuickToggleStates(
        sound = SettingsPreferences.isSoundEnabled(context),
        vibration = SettingsPreferences.isVibrationEnabled(context),
        incognito = SettingsPreferences.isIncognito(context),
    )
    var states by remember(context) { mutableStateOf(read()) }
    DisposableEffect(context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val keys = setOf("sound_enabled", "haptic_mode", SettingsPreferences.KEY_INCOGNITO)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in keys) states = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return states
}
