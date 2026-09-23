package com.kingzcheung.xime.ui.keyboard

import kotlin.math.roundToInt
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.settings.SettingsPreferences

internal fun normalizeHandwritingPause(value: Float): Float =
    ((value.takeIf { it.isFinite() } ?: 0.5f).coerceIn(0.1f, 2.5f) * 10).roundToInt() / 10f

enum class SpaceHoldAction(val label: String) {
    CURSOR("移动光标"), REPEAT("连续空格")
}

data class KeyboardInputPreferences(
    val spaceHold: SpaceHoldAction = SpaceHoldAction.CURSOR,
    val cursorStepDp: Float = 10f,
    val keyTextScale: Float = 1.15f,
    val fixedSymbols: String = "",
    val keyGlowEnabled: Boolean = false,
    val handwritingPauseSeconds: Float = 0.5f,
    val showPressBubble: Boolean = false,
    val splitKeyboardEnabled: Boolean = false,
) {
    companion object {
        fun read(context: Context): KeyboardInputPreferences {
            val prefs = SettingsPreferences.getPrefsPublic(context)
            val legacyPause = prefs.getFloat("handwriting_pause_seconds", 0.5f)
            // 本轮将旧的一秒默认值迁移为半秒；其他已设时长保留。
            val pause = prefs.getFloat("handwriting_pause_seconds_v2", if (legacyPause == 1f) 0.5f else legacyPause)
            return KeyboardInputPreferences(
                keyGlowEnabled = prefs.getBoolean("key_glow_enabled", false),
                showPressBubble = SettingsPreferences.shouldShowPressBubble(context),
                splitKeyboardEnabled = SettingsPreferences.isSplitKeyboardEnabled(context),
                spaceHold = SpaceHoldAction.entries.firstOrNull { it.name == prefs.getString("space_hold_action", "CURSOR") }
                    ?: SpaceHoldAction.CURSOR,
                cursorStepDp = prefs.getFloat("cursor_step_dp", 10f).takeIf { it.isFinite() }?.coerceIn(6f, 24f) ?: 10f,
                keyTextScale = prefs.getFloat("key_text_scale", 1.15f).takeIf { it.isFinite() }?.coerceIn(0.8f, 1.6f) ?: 1.15f,
                handwritingPauseSeconds = normalizeHandwritingPause(pause),
                fixedSymbols = prefs.getString("fixed_symbols", "").orEmpty(),
            )
        }
    }

    fun save(context: Context) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString("space_hold_action", spaceHold.name)
            .putFloat("cursor_step_dp", cursorStepDp.coerceIn(6f, 24f))
            .putFloat("key_text_scale", keyTextScale.coerceIn(0.8f, 1.6f))
            .putFloat("handwriting_pause_seconds_v2", normalizeHandwritingPause(handwritingPauseSeconds))
            .putFloat("handwriting_pause_seconds", normalizeHandwritingPause(handwritingPauseSeconds))
            .putString("fixed_symbols", fixedSymbols).apply()
    }

    fun symbols(): List<String>? = fixedSymbols.lines().map { it.trim() }
        .filter { it.isNotEmpty() }.distinct().takeIf { it.isNotEmpty() }
}

data class KeyboardInputActions(
    val onCursorMove: ((Int) -> Unit)? = null,
    val onCursorMoveVertical: ((Int) -> Unit)? = null,
    val onCursorModeChange: ((Boolean) -> Unit)? = null,
    val onVoiceModeChange: ((Boolean) -> Unit)? = null,
    val isSttEnabled: Boolean = false,
    val schemas: List<SchemaInfo> = emptyList(),
    val currentInputModeId: String = "",
    val onSwitchSchema: ((String) -> Unit)? = null,
    val onCommitText: ((String) -> Unit)? = null,
    val isVoiceMode: Boolean = false,
    val voiceSticky: Boolean = false,
)

val LocalKeyboardInputPreferences = staticCompositionLocalOf { KeyboardInputPreferences() }
val LocalKeyboardInputActions = staticCompositionLocalOf { KeyboardInputActions() }

@Composable
fun rememberKeyboardInputPreferences(): KeyboardInputPreferences {
    val context = LocalContext.current
    var settings by remember(context) { mutableStateOf(KeyboardInputPreferences.read(context)) }
    DisposableEffect(context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val relevantKeys = setOf("key_glow_enabled", SettingsPreferences.KEY_SHOW_PRESS_BUBBLE,
            SettingsPreferences.KEY_SPLIT_KEYBOARD,
            "space_hold_action", "cursor_step_dp", "key_text_scale", "fixed_symbols",
            "handwriting_pause_seconds", "handwriting_pause_seconds_v2")
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in relevantKeys) settings = KeyboardInputPreferences.read(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}
