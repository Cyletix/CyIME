package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputProfile
import com.kingzcheung.xime.settings.LayoutKind
import com.kingzcheung.xime.viewmodel.ShiftMode

/**
 * 日语 26 键的长音键。键面 ー，送出 "-" 进罗马字转换（ko-hi- → コーヒー），和字母一样接在读音里；
 * 不是直接上屏的符号（m 键角标的 ー 照旧直接上屏）。默认放在 L 键右侧（xime.yaml qwerty_japanese 中排）。
 */
internal const val CHOONPU_KEY = "ー"
internal const val CHOONPU_INPUT = "-"

/** 按住长音键满这么久才算按住 Shift；比这短松手是 ー。 */
internal const val SHIFT_TAP_KEY_HOLD_MS = 300L

/** 「长音键代替 Shift」只管罗马字日语 26 键（不在英数模式）；日语九宫格、其他语言不变。 */
internal fun japaneseChoonpuOnShift(setting: Boolean, ascii: Boolean, profile: InputProfile): Boolean =
    setting && !ascii && profile.language == InputLanguage.JAPANESE && profile.layout.kind == LayoutKind.ALPHABETIC

/** 代替 Shift 时中排拿掉 L 右侧的 ー，恢复 9 键。 */
internal fun withoutChoonpuKey(rows: List<List<String>>): List<List<String>> =
    rows.map { row -> row.filter { it != CHOONPU_KEY } }

/** 长按没打别的键就松手：照 Shift 点按切换，小写→单次大写→锁定→小写（shift_caps 在锁定时是解锁）。 */
internal fun shiftTapKeyLongPressAction(mode: ShiftMode): String =
    if (mode == ShiftMode.OFF) "shift_single" else "shift_caps"
