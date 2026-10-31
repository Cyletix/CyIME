package com.kingzcheung.xime.ui.keyboard

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kingzcheung.xime.settings.SettingsPreferences

/** Shared persisted choice; independent of keyboard dimensions and insets. */
@Composable
internal fun rememberRoundedKeyboardBottom(): Boolean =
    rememberCornerChoice(SettingsPreferences.KEY_ROUNDED_KEYBOARD_BOTTOM, SettingsPreferences::roundedKeyboardBottom)

/** 固定键盘顶部两角是否圆角，和底部圆角同一种读法。 */
@Composable
internal fun rememberRoundedKeyboardTop(): Boolean =
    rememberCornerChoice(SettingsPreferences.KEY_ROUNDED_KEYBOARD_TOP, SettingsPreferences::roundedKeyboardTop)

/** 玻璃「柔化顶部边缘」开关，同一种读法（设置页改了键盘立刻跟）。 */
@Composable
internal fun rememberGlassSoftTopEdge(): Boolean =
    rememberCornerChoice(SettingsPreferences.KEY_GLASS_SOFT_TOP_EDGE, SettingsPreferences::glassSoftTopEdge)

@Composable
private fun rememberCornerChoice(prefKey: String, read: (android.content.Context) -> Boolean): Boolean {
    val context = LocalContext.current
    var rounded by remember(context) { mutableStateOf(read(context)) }
    DisposableEffect(context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == prefKey) {
                rounded = read(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return rounded
}

internal fun useDarkNavigationIcons(rounded: Boolean, systemDark: Boolean, keyTextLuminance: Float): Boolean =
    if (rounded) !systemDark else keyTextLuminance < 0.5f