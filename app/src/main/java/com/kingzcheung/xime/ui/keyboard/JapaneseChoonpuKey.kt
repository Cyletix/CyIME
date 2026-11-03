package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputProfile
import com.kingzcheung.xime.settings.LayoutKind
import com.kingzcheung.xime.viewmodel.ShiftMode

/**
 * 日语 26 键的长音 ー 放在哪（「26 键 › 长音键」三选一，默认 L 键）：
 * - [L_KEY]：L 键就是长音（原来 jaroomaji、现在 azooKey 翻译器都把 l 当 ー），不多加键；
 * - [SEPARATE]：L 右侧单独一个 ー 键，L 打回字母 l（la → ぁ）；
 * - [SHIFT]：Shift 键点按是 ー、按住满 [SHIFT_TAP_KEY_HOLD_MS] 是 Shift，L 打回字母 l。
 * ー 键送 "-" 进罗马字转换（ko-hi- → コーヒー），和字母一样接在读音里；m 键角标的 ー 照旧直接上屏。
 */
enum class JapaneseLongVowelKey(val label: String) {
    L_KEY("L 键"), SEPARATE("L 右侧单独键"), SHIFT("代替 Shift");

    /** L 键是不是长音；不是时 L 是字母 l（翻译器选项 `_cyime_l_letter`，显示规则见 JapaneseRomajiPreedit）。 */
    val lIsLongVowel: Boolean get() = this == L_KEY

    companion object {
        fun fromName(name: String?): JapaneseLongVowelKey = entries.firstOrNull { it.name == name } ?: L_KEY
    }
}

internal const val CHOONPU_KEY = "ー"
internal const val CHOONPU_INPUT = "-"

/** 按住长音键满这么久才算按住 Shift；比这短松手是 ー。 */
internal const val SHIFT_TAP_KEY_HOLD_MS = 300L

/** 只管罗马字日语 26 键（不在英数模式）；日语九宫格、其他语言返回 null，键盘不变。 */
internal fun japaneseLongVowelKey(setting: JapaneseLongVowelKey, ascii: Boolean, profile: InputProfile): JapaneseLongVowelKey? =
    setting.takeIf { !ascii && profile.language == InputLanguage.JAPANESE && profile.layout.kind == LayoutKind.ALPHABETIC }

/** 「L 右侧单独键」：有 l 的那一行在 l 后面插一个 ー（自选的字母排列也一样）；没有 l 时加在第二行末尾。 */
internal fun withChoonpuKey(rows: List<List<String>>): List<List<String>> {
    if (rows.any { CHOONPU_KEY in it }) return rows
    val row = rows.indexOfFirst { "l" in it }.takeIf { it >= 0 } ?: 1.coerceAtMost(rows.lastIndex)
    if (row < 0) return rows
    return rows.mapIndexed { index, keys ->
        if (index != row) keys
        else keys.indexOf("l").let { at -> if (at < 0) keys + CHOONPU_KEY else keys.take(at + 1) + CHOONPU_KEY + keys.drop(at + 1) }
    }
}

/** 长音键选「L 键」（默认）时为 true：L 键键面写 ー（它打的就是 ー），不写 l（11-03 Mei 以为长音键没了）。 */
internal val LocalLongVowelOnL = androidx.compose.runtime.staticCompositionLocalOf { false }

/** L 键当长音时键面写 ー；其他键、其他放法原样。Shift 时也一样（大写 L 同样是 ー）。 */
internal fun longVowelKeyLabel(key: String, label: String, lIsLongVowel: Boolean): String =
    if (lIsLongVowel && key.equals("l", ignoreCase = true)) CHOONPU_KEY else label

/** 长按没打别的键就松手：照 Shift 点按切换，小写→单次大写→锁定→小写（shift_caps 在锁定时是解锁）。 */
internal fun shiftTapKeyLongPressAction(mode: ShiftMode): String =
    if (mode == ShiftMode.OFF) "shift_single" else "shift_caps"
