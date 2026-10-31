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

/** 移光标时从中间滑到边缘走几行：0.5–3 行，每档 0.25 行，默认 1 行。 */
internal fun normalizeCursorSwipeLines(value: Float): Float =
    ((value.takeIf { it.isFinite() } ?: 1f).coerceIn(0.5f, 3f) * 4).roundToInt() / 4f

/** 长按语言键打开语言列表的等待时间：0.05–1 秒，每档 0.05 秒，默认 0.15 秒。 */
internal fun normalizeLanguageMenuHoldSeconds(value: Float): Float =
    ((value.takeIf { it.isFinite() } ?: 0.15f).coerceIn(0.05f, 1f) * 20).roundToInt() / 20f

/** 26 键符号键点按的时限：0.3–2 秒，每档 0.1 秒，默认 1 秒；不拖动且在时限内松手才打开符号。 */
internal fun normalizeSymbolKeyHoldSeconds(value: Float): Float =
    ((value.takeIf { it.isFinite() } ?: 1f).coerceIn(0.3f, 2f) * 10).roundToInt() / 10f

/** 九键「下一项」拖到候选算拖动选词的距离：键盘区宽或高的 20%–80%，每档 10%，默认 50%（用户 10-25 定的一半）。 */
internal fun normalizeCandidateDragPercent(value: Float): Float =
    ((value.takeIf { it.isFinite() } ?: 50f).coerceIn(20f, 80f) / 10f).roundToInt() * 10f

/** 手写公式上屏时两边加的 LaTeX 标记。 */
enum class FormulaDelimiter(val label: String, val open: String, val close: String) {
    NONE("不加", "", ""), DOLLAR("\$…\$", "\$", "\$"), DISPLAY("\\[ … \\]", "\\[", "\\]");
    fun wrap(latex: String): String = open + latex + close
}

enum class SpaceHoldAction(val label: String) {
    /** VOICE_TOGGLE 行为是按住说话、松手结束（10-30 起）；名字「语音开关」不改。 */
    CURSOR("移动光标"), REPEAT("连续空格"), VOICE_TOGGLE("语音开关")
}

enum class CursorGestureMode(val label: String) {
    SPACE("长按空格"), KEYBOARD("键盘滑动"), NONE("关闭")
}

data class KeyboardInputPreferences(
    val spaceHold: SpaceHoldAction = SpaceHoldAction.CURSOR,
    val cursorStepDp: Float = 10f,
    val keyTextScale: Float = 1.15f,
    /** 字母键帽一律显示大写（只改显示，输入照旧跟 Shift）；默认关，保持小写。 */
    val uppercaseKeyLabels: Boolean = false,
    val fixedSymbols: String = "",
    val keyGlowEnabled: Boolean = false,
    val keyAnimationEnabled: Boolean = false,
    val handwritingPauseSeconds: Float = 0.5f,
    val showPressBubble: Boolean = false,
    val splitKeyboardEnabled: Boolean = false,
    /** 分体 26 键右半中排再放一个 G（默认关）。 */
    val splitRightG: Boolean = false,
    /** 中缝和两侧留空用键盘底色填满（默认镂空）。 */
    val fillBlankAreas: Boolean = false,
    /** 加宽按键：两侧留白和横向键缝变小，键帽更宽（默认关，见 [KeyVisualPolicy.widened]）。 */
    val wideKeys: Boolean = false,
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
    /** 双拼 26 键邻键纠错，默认关；方案补丁见 SpellingPatches。 */
    val doublePinyinCorrection: Boolean = false,
    /** 双拼 26 键每个键上写出翘舌声母和韵母（双拼键位提示），默认关；见 DoublePinyinKeyHints。 */
    val doublePinyinKeyHints: Boolean = false,
    /** 中文全拼 26 键中英混输（nihaohello → 你好hello），默认关；见 app/data/cyime_mixed_input.lua。 */
    val mixedInput: Boolean = false,
    /** 中文全拼 26 键补出生僻字（瀺 chan），默认关；见 app/data/cyime_rare_chars.lua。 */
    val rareCharacters: Boolean = false,
    /** 中文候选和上屏用台湾正体（台灣、計畫），默认关；Rime 选项 traditionalization，转换表见 assets/rime_chinese/opencc。 */
    val traditionalOutput: Boolean = false,
    /** 打出“时间、日期、星期”时紧跟着给当前时间和日期（15:35、2026-10-31），默认关（更新不改原有习惯，要用在 语言与方案 › 中文输出 打开）；Rime 滤镜 cyime_datetime（librime-t9 datetime_filter.h）。 */
    val datetimeCandidates: Boolean = false,
    /** 英文选词后自动加空格（别人请求，默认关）；见 service/EnglishAutoSpace.kt。 */
    val englishAutoSpace: Boolean = false,
    val frostedGlass: FrostedGlassConfig = FrostedGlassConfig(),
    val symbolInputMode: SymbolInputMode = SymbolInputMode.SWIPE_UP,
    val reverseSymbolSwipe: Boolean = false,
    /** 打字中（有拼音、候选或联想）清空时连同整个输入框一起清，默认关只清拼音（10-31 别人提的需求）。 */
    val clearAllWhileComposing: Boolean = false,
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
    /** 中文全拼、双拼 26 键滑行输入，与英文分开、默认关（用户 10-31：开着英文滑行的老用户不能被带着开）。 */
    val chineseGlideTyping: Boolean = false,
    /** 长按弹出字母气泡（26/14 键大小写和带音调字母、九键本键字母）；默认关，免得打断上下拖动输入角标。 */
    val letterLongPressMenu: Boolean = false,
    /** 九键拂动选字母（左上右下），数字改为长按。 */
    val t9FlickLetters: Boolean = false,
    /** 26 键 Z X C V 的上角标改成撤销/剪切/复制/粘贴（发 Ctrl+字母，同按住 !@# 当 Ctrl）；别人提的，默认关（10-31）。 */
    val zxcvEditShortcuts: Boolean = false,
    /** 长按语言键弹出列表，与空格长按分开设置；默认 0.15 秒（2026-10-13 用户要求）。 */
    val languageMenuHoldSeconds: Float = 0.15f,
    /** 按住符号键当 Ctrl；不拖动且在这个时限内松手才打开符号面板，超过时限松手什么都不做；默认 1 秒（2026-10-24 用户要求）。 */
    val symbolKeyHoldSeconds: Float = 1f,
    /** 九键从「下一项」拖出超过键盘区宽或高的这个百分比才算拖动选词，没拖这么远仍是键自己的左右滑；默认 50（用户 10-31 要求可调）。 */
    val candidateDragPercent: Float = 50f,
    /** 手写公式上屏时两边的标记，默认 $…$。 */
    val formulaDelimiter: FormulaDelimiter = FormulaDelimiter.DOLLAR,
    /** 移光标时从中间滑到屏幕边缘走几行，按输入框一行能放几个字换算每字距离。 */
    val cursorSwipeLines: Float = 1f,
) {
    val effectiveSpaceHold: SpaceHoldAction get() = if (cursorGesture == CursorGestureMode.SPACE) SpaceHoldAction.CURSOR
        else spaceHold.takeUnless { it == SpaceHoldAction.CURSOR } ?: SpaceHoldAction.REPEAT
    /** 长按空格不再移动光标时，滑动移动光标改成哪种：键盘滑动；滑行输入锁着键盘滑动就关掉。 */
    val cursorGestureAfterLeavingSpace: CursorGestureMode get() =
        if (InputGestureLocks.lockFor(this, InputGestureLock.Option.CURSOR_GESTURE, CursorGestureMode.KEYBOARD) == null)
            CursorGestureMode.KEYBOARD else CursorGestureMode.NONE
    /** 设置页选长按空格连续空格/语音：长按空格正在移动光标时一并把移动光标改成 [cursorGestureAfterLeavingSpace]，选了立刻生效。 */
    fun withSpaceHold(action: SpaceHoldAction): KeyboardInputPreferences = copy(spaceHold = action,
        cursorGesture = if (cursorGesture == CursorGestureMode.SPACE) cursorGestureAfterLeavingSpace else cursorGesture)
    /** 空格的所有长按行为（移动光标、连续空格、语音）共用“空格长按触发”时长，默认 0.2 秒。 */
    val spaceHoldDelayMs: Long get() = (normalizeCursorHoldSeconds(cursorHoldSeconds) * 1000).roundToInt().toLong()
    val symbolKeyHoldMs: Long get() = (normalizeSymbolKeyHoldSeconds(symbolKeyHoldSeconds) * 1000).roundToInt().toLong()
    companion object {
        /** 中英混输开关；输入法每次按字母键读它（ImeKeyRouter），改了下一键就生效。 */
        const val MIXED_INPUT_KEY = "mixed_input"
        /** 生僻字开关；同中英混输，输入法按字母键前读它（ImeKeyRouter）。 */
        const val RARE_CHARACTERS_KEY = "rare_characters"
        /** Z X C V 上角标改成撤销/剪切/复制/粘贴，默认关。 */
        const val ZXCV_EDIT_SHORTCUTS_KEY = "zxcv_edit_shortcuts"
        /** 繁体输出开关；同中英混输，输入法按键前读它（ImeKeyRouter）。 */
        const val TRADITIONAL_OUTPUT_KEY = "traditional_output"
        /** 打字中清空整个输入框；输入法在清空时读它（ImeKeyRouter）。 */
        const val CLEAR_ALL_WHILE_COMPOSING_KEY = "clear_all_while_composing"
        /** 字母键帽显示大写。 */
        const val UPPERCASE_KEY_LABELS_KEY = "uppercase_key_labels"
        /** 时间日期候选开关；同中英混输，输入法按键前读它（ImeKeyRouter），关掉时设 Rime 选项 _cyime_datetime_off。 */
        const val DATETIME_CANDIDATES_KEY = "datetime_candidates"
        /** 中文滑行输入；新键，旧版本没写过，读不到就是关。 */
        const val CHINESE_GLIDE_KEY = "glide_typing_chinese"
        /** 双拼键位提示开关；设置在「双拼方案」页。 */
        const val DOUBLE_PINYIN_KEY_HINTS_KEY = "double_pinyin_key_hints"
        /** 英文选词后自动加空格开关；输入法选词时读它，改了下一次选词就生效。 */
        const val ENGLISH_AUTO_SPACE_KEY = "english_auto_space"

        /** 正在显示的键盘监听这些键，改了就重新读取；[save] 写入的每个键都必须在内，否则设置页的改动要等输入法重启才生效。 */
        internal val watchedKeys: Set<String> by lazy {
            setOf(KeyEffectPreferences.GLOW, KeyEffectPreferences.ANIMATION, SettingsPreferences.KEY_SHOW_PRESS_BUBBLE,
                SettingsPreferences.KEY_SPLIT_KEYBOARD, SettingsPreferences.KEY_SPLIT_RIGHT_G, SettingsPreferences.KEY_FILL_KEYBOARD_BLANK, SettingsPreferences.KEY_WIDE_KEYS, SettingsPreferences.KEY_SPLIT_GAP_PORTRAIT, SettingsPreferences.KEY_SPLIT_GAP_LANDSCAPE, CHINESE_GLIDE_KEY, ENGLISH_AUTO_SPACE_KEY,
                "space_hold_action", "cursor_gesture_mode", "cursor_hold_seconds", "neighbor_correction", com.kingzcheung.xime.settings.SpellingPatches.DOUBLE_PINYIN_CORRECTION_KEY, MIXED_INPUT_KEY, RARE_CHARACTERS_KEY, TRADITIONAL_OUTPUT_KEY, DATETIME_CANDIDATES_KEY, "symbol_input_mode", "cursor_step_dp", "cursor_swipe_lines", "cursor_step_haptic", "cursor_step_sound", "key_text_scale", UPPERCASE_KEY_LABELS_KEY, "fixed_symbols",
                "fourteen_letter_swipe", "fourteen_letter_swipe_dp", "reverse_symbol_swipe", CLEAR_ALL_WHILE_COMPOSING_KEY, ZXCV_EDIT_SHORTCUTS_KEY, "kana_direction_hints", "flick_direction_disc", "flick_direction_disc_v2",
                "glide_typing", "glide_typing_v2", "letter_long_press_menu", "t9_flick_letters", "language_key_hold_seconds", "language_key_hold_seconds_v2", "symbol_key_hold_seconds", "t9_candidate_drag_percent",
                "qwerty_stagger_mode", "qwerty_stagger_custom", "qwerty_comma_key", "qwerty_period_key", "qwerty_period_key_v2",
                DOUBLE_PINYIN_KEY_HINTS_KEY,
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
                splitRightG = SettingsPreferences.isSplitRightGEnabled(context),
                fillBlankAreas = SettingsPreferences.isKeyboardBlankFilled(context),
                wideKeys = SettingsPreferences.isWideKeysEnabled(context),
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
                cursorSwipeLines = normalizeCursorSwipeLines(prefs.getFloat("cursor_swipe_lines", 1f)),
                cursorHoldSeconds = normalizeCursorHoldSeconds(prefs.getFloat("cursor_hold_seconds", 0.2f)),
                cursorStepHaptic = prefs.getBoolean("cursor_step_haptic", true),
                cursorStepSound = prefs.getBoolean("cursor_step_sound", false),
                languageMenuHoldSeconds = normalizeLanguageMenuHoldSeconds(languageHold),
                symbolKeyHoldSeconds = normalizeSymbolKeyHoldSeconds(prefs.getFloat("symbol_key_hold_seconds", 1f)),
                candidateDragPercent = normalizeCandidateDragPercent(prefs.getFloat("t9_candidate_drag_percent", 50f)),
                neighborCorrection = prefs.getBoolean("neighbor_correction", true),
                doublePinyinCorrection = prefs.getBoolean(com.kingzcheung.xime.settings.SpellingPatches.DOUBLE_PINYIN_CORRECTION_KEY, false),
                doublePinyinKeyHints = prefs.getBoolean(DOUBLE_PINYIN_KEY_HINTS_KEY, false),
                mixedInput = prefs.getBoolean(MIXED_INPUT_KEY, false),
                rareCharacters = prefs.getBoolean(RARE_CHARACTERS_KEY, false),
                traditionalOutput = prefs.getBoolean(TRADITIONAL_OUTPUT_KEY, false),
                datetimeCandidates = prefs.getBoolean(DATETIME_CANDIDATES_KEY, false),
                englishAutoSpace = prefs.getBoolean(ENGLISH_AUTO_SPACE_KEY, false),
                fourteenLetterSwipe = prefs.getBoolean("fourteen_letter_swipe", true),
                fourteenLetterSwipeDp = normalizeLetterSwipeDistance(prefs.getFloat("fourteen_letter_swipe_dp", 24f)),
                symbolInputMode = SymbolInputMode.entries.firstOrNull {
                    it.name == prefs.getString("symbol_input_mode", null)
                } ?: SymbolInputMode.SWIPE_UP,
                reverseSymbolSwipe = prefs.getBoolean("reverse_symbol_swipe", false),
                clearAllWhileComposing = prefs.getBoolean(CLEAR_ALL_WHILE_COMPOSING_KEY, false),
                kanaDirectionHints = prefs.getBoolean("kana_direction_hints", false),
                // 10.11 的默认关已写进旧键；v2 一次性恢复为开，之后用户手动关闭的保留。
                flickDirectionDisc = prefs.getBoolean("flick_direction_disc_v2", true),
                // 滑行输入 10-19 起默认关。旧键会被 save() 连同其他设置一起写成旧默认 true，
                // 分不出是不是用户自己开的，所以只认新键；没有新键就是默认关。
                glideTyping = prefs.getBoolean("glide_typing_v2", false),
                chineseGlideTyping = prefs.getBoolean(CHINESE_GLIDE_KEY, false),
                letterLongPressMenu = prefs.getBoolean("letter_long_press_menu", false),
                t9FlickLetters = prefs.getBoolean("t9_flick_letters", false),
                zxcvEditShortcuts = prefs.getBoolean(ZXCV_EDIT_SHORTCUTS_KEY, false),
                keyTextScale = prefs.getFloat("key_text_scale", 1.15f).takeIf { it.isFinite() }?.coerceIn(0.8f, 1.6f) ?: 1.15f,
                uppercaseKeyLabels = prefs.getBoolean(UPPERCASE_KEY_LABELS_KEY, false),
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
            .putFloat("t9_candidate_drag_percent", normalizeCandidateDragPercent(candidateDragPercent))
            .putBoolean("neighbor_correction", neighborCorrection)
            .putBoolean(com.kingzcheung.xime.settings.SpellingPatches.DOUBLE_PINYIN_CORRECTION_KEY, doublePinyinCorrection)
            .putBoolean(DOUBLE_PINYIN_KEY_HINTS_KEY, doublePinyinKeyHints)
            .putBoolean(MIXED_INPUT_KEY, mixedInput)
            .putBoolean(RARE_CHARACTERS_KEY, rareCharacters)
            .putBoolean(TRADITIONAL_OUTPUT_KEY, traditionalOutput)
            .putBoolean(DATETIME_CANDIDATES_KEY, datetimeCandidates)
            .putBoolean(ENGLISH_AUTO_SPACE_KEY, englishAutoSpace)
            .putBoolean("fourteen_letter_swipe", fourteenLetterSwipe)
            .putFloat("fourteen_letter_swipe_dp", normalizeLetterSwipeDistance(fourteenLetterSwipeDp))
            .putString("symbol_input_mode", symbolInputMode.name)
            .putBoolean("reverse_symbol_swipe", reverseSymbolSwipe)
            .putBoolean(CLEAR_ALL_WHILE_COMPOSING_KEY, clearAllWhileComposing)
            .putBoolean("kana_direction_hints", kanaDirectionHints)
            .putBoolean("flick_direction_disc_v2", flickDirectionDisc)
            .putBoolean("flick_direction_disc", flickDirectionDisc)
            .putBoolean("glide_typing_v2", glideTyping)
            .putBoolean("letter_long_press_menu", letterLongPressMenu)
            .putBoolean("glide_typing", glideTyping)
            .putBoolean(CHINESE_GLIDE_KEY, chineseGlideTyping)
            .putBoolean("t9_flick_letters", t9FlickLetters)
            .putBoolean(ZXCV_EDIT_SHORTCUTS_KEY, zxcvEditShortcuts)
            .putFloat("cursor_step_dp", cursorStepDp.coerceIn(6f, 24f))
            .putFloat("cursor_swipe_lines", normalizeCursorSwipeLines(cursorSwipeLines))
            .putBoolean("cursor_step_haptic", cursorStepHaptic)
            .putBoolean("cursor_step_sound", cursorStepSound)
            .putFloat("key_text_scale", keyTextScale.coerceIn(0.8f, 1.6f))
            .putBoolean(UPPERCASE_KEY_LABELS_KEY, uppercaseKeyLabels)
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
    /** 按住空格说话松手：还在听写就结束并上屏，没开起来就什么都不做（不能像 [onVoiceToggle] 那样反过来开麦）。 */
    val onVoiceHoldEnd: (() -> Unit)? = null,
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
