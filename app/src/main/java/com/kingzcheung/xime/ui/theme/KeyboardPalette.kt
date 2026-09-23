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
