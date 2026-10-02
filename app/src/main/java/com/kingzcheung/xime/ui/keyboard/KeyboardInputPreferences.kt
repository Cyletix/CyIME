package com.kingzcheung.xime.ui.keyboard

import kotlin.math.roundToInt
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.KeyEffectPreferences

internal fun normalizeHandwritingPause(value: Float): Float =
    ((value.takeIf { it.isFinite() } ?: 0.5f).coerceIn(0.1f, 2.5f) * 10).roundToInt() / 10f

enum class SpaceHoldAction(val label: String) {
    CURSOR("移动光标"), REPEAT("连续空格"), VOICE_TOGGLE("语音开关")
}

enum class CursorGestureMode(val label: String) {
    SPACE("长按空格"), KEYBOARD("键盘滑动"), NONE("关闭")
}

data class KeyboardInputPreferences(
    val spaceHold: SpaceHoldAction = SpaceHoldAction.CURSOR,
    val cursorStepDp: Float = 10f,
    val keyTextScale: Float = 1.15f,
    val fixedSymbols: String = "",
    val keyGlowEnabled: Boolean = false,
    val keyAnimationEnabled: Boolean = false,
    val handwritingPauseSeconds: Float = 0.5f,
    val showPressBubble: Boolean = false,
    val splitKeyboardEnabled: Boolean = false,
    val cursorGesture: CursorGestureMode = if (spaceHold == SpaceHoldAction.CURSOR) CursorGestureMode.SPACE else CursorGestureMode.NONE,
    val neighborCorrection: Boolean = true,
    val frostedGlass: FrostedGlassConfig = FrostedGlassConfig(),
    val symbolInputMode: SymbolInputMode = SymbolInputMode.SWIPE_UP,
) {
    val effectiveSpaceHold: SpaceHoldAction get() = if (cursorGesture == CursorGestureMode.SPACE) SpaceHoldAction.CURSOR
        else spaceHold.takeUnless { it == SpaceHoldAction.CURSOR } ?: SpaceHoldAction.REPEAT
    val spaceHoldDelayMs: Long get() = if (effectiveSpaceHold == SpaceHoldAction.CURSOR) 100L else 300L
    companion object {
        fun read(context: Context): KeyboardInputPreferences {
            val prefs = SettingsPreferences.getPrefsPublic(context)
            val legacyPause = prefs.getFloat("handwriting_pause_seconds", 0.5f)
            // 本轮将旧的一秒默认值迁移为半秒；其他已设时长保留。
            val pause = prefs.getFloat("handwriting_pause_seconds_v2", if (legacyPause == 1f) 0.5f else legacyPause)
            val hold = SpaceHoldAction.entries.firstOrNull { it.name == prefs.getString("space_hold_action", "CURSOR") }
                ?: SpaceHoldAction.CURSOR
            return KeyboardInputPreferences(
                keyGlowEnabled = KeyEffectPreferences.glowEnabled(prefs),
                keyAnimationEnabled = KeyEffectPreferences.animationEnabled(prefs),
                frostedGlass = FrostedGlassPreferences.read(context),
                showPressBubble = SettingsPreferences.shouldShowPressBubble(context),
                splitKeyboardEnabled = SettingsPreferences.isSplitKeyboardEnabled(context),
                spaceHold = hold,
                cursorGesture = CursorGestureMode.entries.firstOrNull { it.name == prefs.getString("cursor_gesture_mode", null) }
                    ?: if (hold == SpaceHoldAction.CURSOR) CursorGestureMode.SPACE else CursorGestureMode.NONE,
                cursorStepDp = prefs.getFloat("cursor_step_dp", 10f).takeIf { it.isFinite() }?.coerceIn(6f, 24f) ?: 10f,
                neighborCorrection = prefs.getBoolean("neighbor_correction", true),
                symbolInputMode = SymbolInputMode.entries.firstOrNull {
                    it.name == prefs.getString("symbol_input_mode", null)
                } ?: SymbolInputMode.SWIPE_UP,
                keyTextScale = prefs.getFloat("key_text_scale", 1.15f).takeIf { it.isFinite() }?.coerceIn(0.8f, 1.6f) ?: 1.15f,
                handwritingPauseSeconds = normalizeHandwritingPause(pause),
                fixedSymbols = prefs.getString("fixed_symbols", "").orEmpty(),
            )
        }
    }

    fun save(context: Context) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString("space_hold_action", spaceHold.name)
            .putString("cursor_gesture_mode", cursorGesture.name)
            .putBoolean("neighbor_correction", neighborCorrection)
            .putString("symbol_input_mode", symbolInputMode.name)
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
    val onVoiceToggle: (() -> Unit)? = null,
    val isSttEnabled: Boolean = false,
    val schemas: List<SchemaInfo> = emptyList(),
    val currentInputModeId: String = "",
    val onSwitchSchema: ((String) -> Unit)? = null,
    val onCommitText: ((String) -> Unit)? = null,
    val isVoiceMode: Boolean = false,
    val voiceSticky: Boolean = false,
    val onCommitExactText: ((String) -> Unit)? = null,
)

val LocalKeyboardInputPreferences = staticCompositionLocalOf { KeyboardInputPreferences() }
val LocalKeyboardInputActions = staticCompositionLocalOf { KeyboardInputActions() }

@Composable
fun rememberKeyboardInputPreferences(): KeyboardInputPreferences {
    val context = LocalContext.current
    var settings by remember(context) { mutableStateOf(KeyboardInputPreferences.read(context)) }
    DisposableEffect(context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val relevantKeys = setOf(KeyEffectPreferences.GLOW, KeyEffectPreferences.ANIMATION, SettingsPreferences.KEY_SHOW_PRESS_BUBBLE,
            SettingsPreferences.KEY_SPLIT_KEYBOARD,
            "space_hold_action", "cursor_gesture_mode", "neighbor_correction", "symbol_input_mode", "cursor_step_dp", "key_text_scale", "fixed_symbols",
            "handwriting_pause_seconds", "handwriting_pause_seconds_v2") + FrostedGlassPreferences.keys
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in relevantKeys) settings = KeyboardInputPreferences.read(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}
