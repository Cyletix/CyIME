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

/** Use the candidate viewport, not the device width: a tablet can host a narrow floating keyboard. */
internal fun candidateCommentsVisible(
    widthDp: Float,
    fontScale: Float,
    textSizeSp: Float,
    allowed: Boolean,
): Boolean {
    val textScale = (fontScale * textSizeSp / 19f).coerceAtLeast(1f)
    return allowed && widthDp >= 480f * textScale
}

/** Keep the existing explicit hide preference, including changes while the IME is visible. */
@Composable
internal fun rememberCandidateCommentsAllowed(): Boolean {
    val context = LocalContext.current
    val preferences = remember(context) { SettingsPreferences.getPrefsPublic(context) }
    var allowed by remember(preferences) { mutableStateOf(SettingsPreferences.showCandidateComments(context)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == SettingsPreferences.KEY_SHOW_CANDIDATE_COMMENTS) {
                allowed = SettingsPreferences.showCandidateComments(context)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return allowed
}
