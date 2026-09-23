package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** 功能键使用带少量主题色的中性容器，避免直接把高饱和主色铺满深色键盘。 */
internal fun softDarkKeyContainer(accent: Color): Color =
    lerp(Color(0xFF30343C), accent, 0.30f)

/** 固定蓝灰调色板；不读取壁纸，因此手机、平板及不同 Android 版本的色值相同。 */
object SoftBlueTheme {
    const val ID = "soft_blue"

    fun create(id: String = ID, name: String = "柔和蓝") = KeyboardColorScheme(
        id = id,
        name = name,
        specialKeyLight = Color(0xFFDCE6FA),
        specialKeyDark = Color(0xFF495A7D),
        accentLight = Color(0xFF42639C),
        accentDark = Color(0xFFB4CAFA),
        primaryLight = Color(0xFF42639C),
        primaryDark = Color(0xFFB4CAFA),
        primaryContainerLight = Color(0xFFDCE6FA),
        primaryContainerDark = Color(0xFF495A7D),
        surfaceLight = Color(0xFFF4F6FA),
        surfaceDark = Color(0xFF191C22),
        keyboardBgLight = Color(0xFFF4F6FA),
        keyboardBgDark = Color(0xFF191C22),
        keyBgLight = Color.White,
        keyBgDark = Color(0xFF2E323A),
        candidateBarBgLight = Color(0xFFF4F6FA),
        candidateBarBgDark = Color(0xFF191C22),
        keyTextColorLight = Color(0xFF20242C),
        keyTextColorDark = Color(0xFFE9EDF5),
        candidateTextColorLight = Color(0xFF42639C),
        candidateTextColorDark = Color(0xFFB4CAFA),
        candidateSelectedTextColorLight = Color(0xFF2C4D85),
        candidateSelectedTextColorDark = Color(0xFFDCE6FA),
        dividerColorLight = Color(0xFFD4DBE7),
        dividerColorDark = Color(0xFF454C5B),
        useThemeColors = true,
    )
}

/** 完整主题的功能键与预览同源；兼容旧式自定义主题的全局覆盖。 */
fun resolvedSpecialKeyColor(theme: KeyboardColorScheme, isDark: Boolean, legacyOverride: Color?): Color {
    val themed = if (isDark) theme.specialKeyDark else theme.specialKeyLight
    return if (theme.isDynamic || theme.useThemeColors) themed else legacyOverride ?: themed
}

/** 薰衣草使用不透明的中性紫灰键帽，避免半透明白叠深紫导致泛灰。 */
object SoftLavenderTheme {
    fun create() = KeyboardColorScheme(
        id = "lavender_purple", name = "薰衣草紫",
        specialKeyLight = Color(0xFFE8DEF8), specialKeyDark = softDarkKeyContainer(Color(0xFFD0BCFF)),
        accentLight = Color(0xFF8F73E2), accentDark = Color(0xFFD0BCFF),
        surfaceLight = Color(0xFFF7F4FA), surfaceDark = Color(0xFF211E28),
        keyboardBgLight = Color(0xFFF7F4FA), keyboardBgDark = Color(0xFF211E28),
        candidateBarBgLight = Color(0xFFF7F4FA), candidateBarBgDark = Color(0xFF211E28),
        keyBgLight = Color(0xFFFFFBFF), keyBgDark = Color(0xFF38333F),
        keyTextColorLight = Color(0xFF25212C), keyTextColorDark = Color(0xFFF0EAF5),
        candidateTextColorLight = Color(0xFF685191), candidateTextColorDark = Color(0xFFD0BCFF),
        candidateSelectedTextColorLight = Color(0xFF59417F), candidateSelectedTextColorDark = Color(0xFFE9DDFF),
        useThemeColors = true,
    )
}

/** 用户指定的固定深色主题；跟随系统亮暗时也保留相同的键盘配色。 */
object Advance858Theme {
    const val ID = "858AdvanceColor"
    fun create() = KeyboardColorScheme(
        id = ID, name = ID,
        specialKeyLight = Color(0xFF6D717C), specialKeyDark = Color(0xFF6D717C),
        enterKeyLight = Color(0xFF3F4E68), enterKeyDark = Color(0xFF3F4E68),
        accentLight = Color(0xFFC3CDDF), accentDark = Color(0xFFC3CDDF),
        surfaceLight = Color(0xFF292929), surfaceDark = Color(0xFF292929),
        keyboardBgLight = Color(0xFF292929), keyboardBgDark = Color(0xFF292929),
        candidateBarBgLight = Color(0xFF292929), candidateBarBgDark = Color(0xFF292929),
        keyBgLight = Color(0xFF525252), keyBgDark = Color(0xFF525252),
        keyTextColorLight = Color.White, keyTextColorDark = Color.White,
        specialKeyTextColorLight = Color.White, specialKeyTextColorDark = Color.White,
        candidateTextColorLight = Color(0xFFE0E0E0), candidateTextColorDark = Color(0xFFE0E0E0),
        candidateSelectedTextColorLight = Color.White, candidateSelectedTextColorDark = Color.White,
        useThemeColors = true,
    )
}
