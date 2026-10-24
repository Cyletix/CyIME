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

/** 长按语言键打开语言列表的等待时间：0.05–1 秒，每档 0.05 秒，默认 0.15 秒。 */
internal fun normalizeLanguageMenuHoldSeconds(value: Float): Float =
    ((value.takeIf { it.isFinite() } ?: 0.15f).coerceIn(0.05f, 1f) * 20).roundToInt() / 20f

/** 26 键符号键点按的时限：0.3–2 秒，每档 0.1 秒，默认 1 秒；不拖动且在时限内松手才打开符号。 */
internal fun normalizeSymbolKeyHoldSeconds(value: Float): Float =
    ((value.takeIf { it.isFinite() } ?: 1f).coerceIn(0.3f, 2f) * 10).roundToInt() / 10f

/** 手写公式上屏时两边加的 LaTeX 标记。 */
enum class FormulaDelimiter(val label: String, val open: String, val close: String) {
    NONE("不加", "", ""), DOLLAR("\$…\$", "\$", "\$"), DISPLAY("\\[ … \\]", "\\[", "\\]");
    fun wrap(latex: String): String = open + latex + close
}

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
    /** 分体中缝比例（竖屏/横屏），null 为自动。 */
    val splitGapPortrait: Float? = null,
    val splitGapLandscape: Float? = null,
    /** 26 键三行错位；自定义时取 [qwertyStaggerCustom]（0 手机常见，1 实体键盘）。 */
    val qwertyStagger: QwertyStaggerMode = QwertyStaggerMode.AUTO,
    val qwertyStaggerCustom: Float = 0.5f,
    /** 空格左侧逗号键、右侧句号键。 */
    val qwertyCommaKey: Boolean = true,
    val qwertyPeriodKey: Boolean = true,
    /** 各固定栏按位置保存的符号（每行一个位置）；未设置的沿用旧的共用列表。 */
    val sideSymbols: Map<String, String> = emptyMap(),
    val cursorGesture: CursorGestureMode = if (spaceHold == SpaceHoldAction.CURSOR) CursorGestureMode.SPACE else CursorGestureMode.NONE,
    val neighborCorrection: Boolean = true,
    val frostedGlass: FrostedGlassConfig = FrostedGlassConfig(),
    val symbolInputMode: SymbolInputMode = SymbolInputMode.SWIPE_UP,
    val reverseSymbolSwipe: Boolean = false,
    val cursorHoldSeconds: Float = 0.2f,
    /** 移动光标时每走一字轻震一下；原来一直有，默认开保持手感（2026-10-22 起可关）。 */
    val cursorStepHaptic: Boolean = true,
    /** 移动光标时每走一字播放按键音；新加，默认关。 */
    val cursorStepSound: Boolean = false,
    val fourteenLetterSwipe: Boolean = true,
    val fourteenLetterSwipeDp: Float = 24f,
    val kanaDirectionHints: Boolean = false,
    /**
     * 拂动时在按键上方显示四向圆盘并在键上显示方向箭头（日语假名、九键拂动、14 键字母对）；关闭时与 26 键一样在键内渐变提示。
     * 默认开（2026-10-16 用户要求恢复日语拂动浮窗；10.11 曾改为默认关）。
     */
    val flickDirectionDisc: Boolean = true,
    /** 英文 26 键滑行输入；整键盘滑动移动光标时让位。 */
    val glideTyping: Boolean = false,
    /** 长按弹出字母气泡（26/14 键大小写和带音调字母、九键本键字母）；默认关，免得打断上下拖动输入角标。 */
    val letterLongPressMenu: Boolean = false,
    /** 九键拂动选字母（左上右下），数字改为长按。 */
    val t9FlickLetters: Boolean = false,
    /** 长按语言键弹出列表，与空格长按分开设置；默认 0.15 秒（2026-10-13 用户要求）。 */
    val languageMenuHoldSeconds: Float = 0.15f,
    /** 按住符号键当 Ctrl；不拖动且在这个时限内松手才打开符号面板，超过时限松手什么都不做；默认 1 秒（2026-10-24 用户要求）。 */
    val symbolKeyHoldSeconds: Float = 1f,
    /** 手写公式上屏时两边的标记，默认 $…$。 */
    val formulaDelimiter: FormulaDelimiter = FormulaDelimiter.DOLLAR,
) {
    val effectiveSpaceHold: SpaceHoldAction get() = if (cursorGesture == CursorGestureMode.SPACE) SpaceHoldAction.CURSOR
        else spaceHold.takeUnless { it == SpaceHoldAction.CURSOR } ?: SpaceHoldAction.REPEAT
    /** 空格的所有长按行为（移动光标、连续空格、语音）共用“空格长按触发”时长，默认 0.2 秒。 */
    val spaceHoldDelayMs: Long get() = (normalizeCursorHoldSeconds(cursorHoldSeconds) * 1000).roundToInt().toLong()
    val symbolKeyHoldMs: Long get() = (normalizeSymbolKeyHoldSeconds(symbolKeyHoldSeconds) * 1000).roundToInt().toLong()
    companion object {
        /** 正在显示的键盘监听这些键，改了就重新读取；[save] 写入的每个键都必须在内，否则设置页的改动要等输入法重启才生效。 */
        internal val watchedKeys: Set<String> by lazy {
            setOf(KeyEffectPreferences.GLOW, KeyEffectPreferences.ANIMATION, SettingsPreferences.KEY_SHOW_PRESS_BUBBLE,
                SettingsPreferences.KEY_SPLIT_KEYBOARD, SettingsPreferences.KEY_SPLIT_GAP_PORTRAIT, SettingsPreferences.KEY_SPLIT_GAP_LANDSCAPE,
                "space_hold_action", "cursor_gesture_mode", "cursor_hold_seconds", "neighbor_correction", "symbol_input_mode", "cursor_step_dp", "cursor_step_haptic", "cursor_step_sound", "key_text_scale", "fixed_symbols",
                "fourteen_letter_swipe", "fourteen_letter_swipe_dp", "reverse_symbol_swipe", "kana_direction_hints", "flick_direction_disc", "flick_direction_disc_v2",
                "glide_typing", "glide_typing_v2", "letter_long_press_menu", "t9_flick_letters", "language_key_hold_seconds", "language_key_hold_seconds_v2", "symbol_key_hold_seconds",
                "qwerty_stagger_mode", "qwerty_stagger_custom", "qwerty_comma_key", "qwerty_period_key", "qwerty_period_key_v2",
                *SideSymbolTarget.entries.map { it.prefKey }.toTypedArray(),
                "handwriting_pause_seconds", "handwriting_pause_seconds_v2", "handwriting_formula_delimiter") + FrostedGlassPreferences.keys
        }

        fun read(context: Context, isDark: Boolean = FrostedGlassPreferences.isDark(context)): KeyboardInputPreferences {
            val prefs = SettingsPreferences.getPrefsPublic(context)
            val legacyPause = prefs.getFloat("handwriting_pause_seconds", 0.5f)
            // 本轮将旧的一秒默认值迁移为半秒；其他已设时长保留。
            val pause = prefs.getFloat("handwriting_pause_seconds_v2", if (legacyPause == 1f) 0.5f else legacyPause)
            // 语言键旧默认 0.1 秒迁移为 0.15 秒；其他已设时长保留。
            val legacyLanguageHold = prefs.getFloat("language_key_hold_seconds", 0.15f)
            val languageHold = prefs.getFloat("language_key_hold_seconds_v2", if (legacyLanguageHold == 0.1f) 0.15f else legacyLanguageHold)
            val hold = SpaceHoldAction.entries.firstOrNull { it.name == prefs.getString("space_hold_action", "CURSOR") }
                ?: SpaceHoldAction.CURSOR
            return KeyboardInputPreferences(
                keyGlowEnabled = KeyEffectPreferences.glowEnabled(prefs),
                keyAnimationEnabled = KeyEffectPreferences.animationEnabled(prefs),
                // 过透时自动加深到在白底和黑底上都看得清键面文字。
                frostedGlass = FrostedGlassPreferences.read(context, isDark)
                    .legibleForTheme(SettingsPreferences.getKeyboardTheme(context), isDark),
                showPressBubble = SettingsPreferences.shouldShowPressBubble(context),
                splitKeyboardEnabled = SettingsPreferences.isSplitKeyboardEnabled(context),
                splitGapPortrait = SettingsPreferences.getSplitGapFraction(context, false),
                splitGapLandscape = SettingsPreferences.getSplitGapFraction(context, true),
                qwertyStagger = QwertyStaggerMode.entries.firstOrNull { it.name == prefs.getString("qwerty_stagger_mode", null) }
                    ?: QwertyStaggerMode.AUTO,
                qwertyStaggerCustom = prefs.getFloat("qwerty_stagger_custom", 0.5f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
                qwertyCommaKey = prefs.getBoolean("qwerty_comma_key", true),
                // 句号键 10-19 起默认开。旧键会被 save() 连同其他设置一起写成旧默认 false，
                // 分不出是不是用户自己关的，所以只认新键；没有新键就是默认开。
                qwertyPeriodKey = prefs.getBoolean("qwerty_period_key_v2", true),
                sideSymbols = SideSymbolTarget.entries.mapNotNull { target ->
                    prefs.getString(target.prefKey, null)?.let { target.key to it }
                }.toMap(),
                spaceHold = hold,
                cursorGesture = CursorGestureMode.entries.firstOrNull { it.name == prefs.getString("cursor_gesture_mode", null) }
                    ?: if (hold == SpaceHoldAction.CURSOR) CursorGestureMode.SPACE else CursorGestureMode.NONE,
                cursorStepDp = prefs.getFloat("cursor_step_dp", 10f).takeIf { it.isFinite() }?.coerceIn(6f, 24f) ?: 10f,
                cursorHoldSeconds = normalizeCursorHoldSeconds(prefs.getFloat("cursor_hold_seconds", 0.2f)),
                cursorStepHaptic = prefs.getBoolean("cursor_step_haptic", true),
                cursorStepSound = prefs.getBoolean("cursor_step_sound", false),
                languageMenuHoldSeconds = normalizeLanguageMenuHoldSeconds(languageHold),
                symbolKeyHoldSeconds = normalizeSymbolKeyHoldSeconds(prefs.getFloat("symbol_key_hold_seconds", 1f)),
                neighborCorrection = prefs.getBoolean("neighbor_correction", true),
                fourteenLetterSwipe = prefs.getBoolean("fourteen_letter_swipe", true),
                fourteenLetterSwipeDp = normalizeLetterSwipeDistance(prefs.getFloat("fourteen_letter_swipe_dp", 24f)),
                symbolInputMode = SymbolInputMode.entries.firstOrNull {
                    it.name == prefs.getString("symbol_input_mode", null)
                } ?: SymbolInputMode.SWIPE_UP,
                reverseSymbolSwipe = prefs.getBoolean("reverse_symbol_swipe", false),
                kanaDirectionHints = prefs.getBoolean("kana_direction_hints", false),
                // 10.11 的默认关已写进旧键；v2 一次性恢复为开，之后用户手动关闭的保留。
                flickDirectionDisc = prefs.getBoolean("flick_direction_disc_v2", true),
                // 滑行输入 10-19 起默认关。旧键会被 save() 连同其他设置一起写成旧默认 true，
                // 分不出是不是用户自己开的，所以只认新键；没有新键就是默认关。
                glideTyping = prefs.getBoolean("glide_typing_v2", false),
                letterLongPressMenu = prefs.getBoolean("letter_long_press_menu", false),
                t9FlickLetters = prefs.getBoolean("t9_flick_letters", false),
                keyTextScale = prefs.getFloat("key_text_scale", 1.15f).takeIf { it.isFinite() }?.coerceIn(0.8f, 1.6f) ?: 1.15f,
                handwritingPauseSeconds = normalizeHandwritingPause(pause),
                formulaDelimiter = FormulaDelimiter.entries.firstOrNull { it.name == prefs.getString("handwriting_formula_delimiter", null) }
                    ?: FormulaDelimiter.DOLLAR,
                fixedSymbols = prefs.getString("fixed_symbols", "").orEmpty(),
            )
        }
    }

    fun save(context: Context) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString("space_hold_action", spaceHold.name)
            .putString("cursor_gesture_mode", cursorGesture.name)
            .putFloat("cursor_hold_seconds", normalizeCursorHoldSeconds(cursorHoldSeconds))
            .putFloat("language_key_hold_seconds_v2", normalizeLanguageMenuHoldSeconds(languageMenuHoldSeconds))
            .putFloat("language_key_hold_seconds", normalizeLanguageMenuHoldSeconds(languageMenuHoldSeconds))
            .putFloat("symbol_key_hold_seconds", normalizeSymbolKeyHoldSeconds(symbolKeyHoldSeconds))
            .putBoolean("neighbor_correction", neighborCorrection)
            .putBoolean("fourteen_letter_swipe", fourteenLetterSwipe)
            .putFloat("fourteen_letter_swipe_dp", normalizeLetterSwipeDistance(fourteenLetterSwipeDp))
            .putString("symbol_input_mode", symbolInputMode.name)
            .putBoolean("reverse_symbol_swipe", reverseSymbolSwipe)
            .putBoolean("kana_direction_hints", kanaDirectionHints)
            .putBoolean("flick_direction_disc_v2", flickDirectionDisc)
            .putBoolean("flick_direction_disc", flickDirectionDisc)
            .putBoolean("glide_typing_v2", glideTyping)
            .putBoolean("letter_long_press_menu", letterLongPressMenu)
            .putBoolean("glide_typing", glideTyping)
            .putBoolean("t9_flick_letters", t9FlickLetters)
            .putFloat("cursor_step_dp", cursorStepDp.coerceIn(6f, 24f))
            .putBoolean("cursor_step_haptic", cursorStepHaptic)
            .putBoolean("cursor_step_sound", cursorStepSound)
            .putFloat("key_text_scale", keyTextScale.coerceIn(0.8f, 1.6f))
            .putFloat("handwriting_pause_seconds_v2", normalizeHandwritingPause(handwritingPauseSeconds))
            .putFloat("handwriting_pause_seconds", normalizeHandwritingPause(handwritingPauseSeconds))
            .putString("handwriting_formula_delimiter", formulaDelimiter.name)
            .putString("fixed_symbols", fixedSymbols)
            .putString("qwerty_stagger_mode", qwertyStagger.name)
            .putFloat("qwerty_stagger_custom", qwertyStaggerCustom.coerceIn(0f, 1f))
            .putBoolean("qwerty_comma_key", qwertyCommaKey)
            .putBoolean("qwerty_period_key_v2", qwertyPeriodKey)
            .putBoolean("qwerty_period_key", qwertyPeriodKey).apply()
    }

    /** 某个固定栏的自定义内容；该栏未单独设置时沿用旧的共用列表，都没有则为 null（用默认）。 */
    fun symbolsFor(target: SideSymbolTarget): List<String>? =
        SideSymbolTarget.parse(sideSymbols[target.key]) ?: symbols()

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
/** Transient resize material draft; never writes user settings until the resize is confirmed. */
internal val LocalFrostedGlassPreview = compositionLocalOf<com.kingzcheung.xime.settings.FrostedGlassProfiles?> { null }
val LocalKeyboardInputActions = staticCompositionLocalOf { KeyboardInputActions() }

@Composable
fun rememberKeyboardInputPreferences(isDark: Boolean? = null): KeyboardInputPreferences {
    val context = LocalContext.current
    val systemAppearance = LocalConfiguration.current.uiMode
    fun readSettings() = KeyboardInputPreferences.read(context, isDark ?: FrostedGlassPreferences.isDark(context))
    var settings by remember(context, isDark, systemAppearance) { mutableStateOf(readSettings()) }
    DisposableEffect(context, isDark, systemAppearance) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val relevantKeys = KeyboardInputPreferences.watchedKeys
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in relevantKeys) settings = readSettings()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val preview = LocalFrostedGlassPreview.current
    return if (preview != null) {
        val dark = isDark ?: FrostedGlassPreferences.isDark(context)
        settings.copy(frostedGlass = preview.forAppearance(dark)
            .legibleForTheme(SettingsPreferences.getKeyboardTheme(context), dark))
    } else settings
}
