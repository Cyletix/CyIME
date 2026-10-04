package com.kingzcheung.xime.ui.keyboard

import kotlin.math.roundToInt
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.KeyEffectPreferences

internal fun normalizeHandwritingPause(value: Float): Float =
    ((value.takeIf { it.isFinite() } ?: 0.5f).coerceIn(0.1f, 2.5f) * 10).roundToInt() / 10f

internal fun normalizeCursorHoldSeconds(value: Float): Float =
    ((value.takeIf { it.isFinite() } ?: 0.2f).coerceIn(0.1f, 1f) * 10).roundToInt() / 10f

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
    val reverseSymbolSwipe: Boolean = false,
    val cursorHoldSeconds: Float = 0.2f,
    val fourteenLetterSwipe: Boolean = true,
    val fourteenLetterSwipeDp: Float = 24f,
) {
    val effectiveSpaceHold: SpaceHoldAction get() = if (cursorGesture == CursorGestureMode.SPACE) SpaceHoldAction.CURSOR
        else spaceHold.takeUnless { it == SpaceHoldAction.CURSOR } ?: SpaceHoldAction.REPEAT
    val spaceHoldDelayMs: Long get() = if (effectiveSpaceHold == SpaceHoldAction.CURSOR)
        (normalizeCursorHoldSeconds(cursorHoldSeconds) * 1000).roundToInt().toLong() else 300L
    companion object {
        fun read(context: Context, isDark: Boolean = FrostedGlassPreferences.isDark(context)): KeyboardInputPreferences {
            val prefs = SettingsPreferences.getPrefsPublic(context)
            val legacyPause = prefs.getFloat("handwriting_pause_seconds", 0.5f)
            // 本轮将旧的一秒默认值迁移为半秒；其他已设时长保留。
            val pause = prefs.getFloat("handwriting_pause_seconds_v2", if (legacyPause == 1f) 0.5f else legacyPause)
            val hold = SpaceHoldAction.entries.firstOrNull { it.name == prefs.getString("space_hold_action", "CURSOR") }
                ?: SpaceHoldAction.CURSOR
            return KeyboardInputPreferences(
                keyGlowEnabled = KeyEffectPreferences.glowEnabled(prefs),
                keyAnimationEnabled = KeyEffectPreferences.animationEnabled(prefs),
                frostedGlass = FrostedGlassPreferences.read(context, isDark),
                showPressBubble = SettingsPreferences.shouldShowPressBubble(context),
                splitKeyboardEnabled = SettingsPreferences.isSplitKeyboardEnabled(context),
                spaceHold = hold,
                cursorGesture = CursorGestureMode.entries.firstOrNull { it.name == prefs.getString("cursor_gesture_mode", null) }
                    ?: if (hold == SpaceHoldAction.CURSOR) CursorGestureMode.SPACE else CursorGestureMode.NONE,
                cursorStepDp = prefs.getFloat("cursor_step_dp", 10f).takeIf { it.isFinite() }?.coerceIn(6f, 24f) ?: 10f,
                cursorHoldSeconds = normalizeCursorHoldSeconds(prefs.getFloat("cursor_hold_seconds", 0.2f)),
                neighborCorrection = prefs.getBoolean("neighbor_correction", true),
                fourteenLetterSwipe = prefs.getBoolean("fourteen_letter_swipe", true),
                fourteenLetterSwipeDp = normalizeLetterSwipeDistance(prefs.getFloat("fourteen_letter_swipe_dp", 24f)),
                symbolInputMode = SymbolInputMode.entries.firstOrNull {
                    it.name == prefs.getString("symbol_input_mode", null)
                } ?: SymbolInputMode.SWIPE_UP,
                reverseSymbolSwipe = prefs.getBoolean("reverse_symbol_swipe", false),
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
            .putFloat("cursor_hold_seconds", normalizeCursorHoldSeconds(cursorHoldSeconds))
            .putBoolean("neighbor_correction", neighborCorrection)
            .putBoolean("fourteen_letter_swipe", fourteenLetterSwipe)
            .putFloat("fourteen_letter_swipe_dp", normalizeLetterSwipeDistance(fourteenLetterSwipeDp))
            .putString("symbol_input_mode", symbolInputMode.name)
            .putBoolean("reverse_symbol_swipe", reverseSymbolSwipe)
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
fun rememberKeyboardInputPreferences(isDark: Boolean? = null): KeyboardInputPreferences {
    val context = LocalContext.current
    val systemAppearance = LocalConfiguration.current.uiMode
    fun readSettings() = KeyboardInputPreferences.read(context, isDark ?: FrostedGlassPreferences.isDark(context))
    var settings by remember(context, isDark, systemAppearance) { mutableStateOf(readSettings()) }
    DisposableEffect(context, isDark, systemAppearance) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val relevantKeys = setOf(KeyEffectPreferences.GLOW, KeyEffectPreferences.ANIMATION, SettingsPreferences.KEY_SHOW_PRESS_BUBBLE,
            SettingsPreferences.KEY_SPLIT_KEYBOARD,
            "space_hold_action", "cursor_gesture_mode", "cursor_hold_seconds", "neighbor_correction", "symbol_input_mode", "cursor_step_dp", "key_text_scale", "fixed_symbols",
            "fourteen_letter_swipe", "fourteen_letter_swipe_dp", "reverse_symbol_swipe",
            "handwriting_pause_seconds", "handwriting_pause_seconds_v2") + FrostedGlassPreferences.keys
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in relevantKeys) settings = readSettings()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}
