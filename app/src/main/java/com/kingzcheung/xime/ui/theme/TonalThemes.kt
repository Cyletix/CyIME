package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import com.kingzcheung.xime.settings.BackgroundConfig

/**
 * 1.9.15 移除的旧主题，按单色相 OKLCH 明度阶梯重建后恢复（2026-10-18 用户要求加回，每个色系只留一套）。
 * 表面与主题同色相、低彩度，只靠明度分层；只有回车带彩度。回车图标沿用功能键文字色，
 * 所以浅色回车取亮色调、深色回车取中明度，两者都与图标保持 4.5:1 以上。
 * 生成：python tools/theme_rebuild.py（色值与对比度检查都在脚本里），不要手改色值。
 */
object TonalThemes {
    // 每组依次为：底、字母键、功能键、文字、回车、选中候选/强调、候选文字、分隔线、选中标签底。
    private fun tonal(id: String, name: String, light: LongArray, dark: LongArray, background: BackgroundConfig? = null): KeyboardColorScheme {
        fun Long.c() = Color(0xFF000000 or this)
        return KeyboardColorScheme(
            id = id, name = name,
            specialKeyLight = light[2].c(), specialKeyDark = dark[2].c(),
            accentLight = light[5].c(), accentDark = dark[5].c(),
            primaryContainerLight = light[8].c(), primaryContainerDark = dark[8].c(),
            surfaceLight = light[0].c(), surfaceDark = dark[0].c(),
            keyBgLight = light[1].c(), keyBgDark = dark[1].c(),
            keyTextColorLight = light[3].c(), keyTextColorDark = dark[3].c(),
            candidateTextColorLight = light[6].c(), candidateTextColorDark = dark[6].c(),
            candidateSelectedTextColorLight = light[5].c(), candidateSelectedTextColorDark = dark[5].c(),
            dividerColorLight = light[7].c(), dividerColorDark = dark[7].c(),
            keyboardBackground = background,
            useThemeColors = true,
            enterKeyLight = light[4].c(), enterKeyDark = dark[4].c(),
            specialKeyTextColorLight = light[3].c(), specialKeyTextColorDark = dark[3].c(),
        )
    }

    fun create(): List<KeyboardColorScheme> = listOf(
        tonal("sakura_pink", "樱花粉黛",
            longArrayOf(0xFFDCE0, 0xFFFAFB, 0xF9BDC5, 0x3E1820, 0xFC9AAA, 0x8B173B, 0x57494B, 0xDECCCE, 0xFDD1D7),
            longArrayOf(0x250B10, 0x4B2D31, 0x3B191F, 0xFCEBED, 0xE3125E, 0xFFBDC6, 0xC4B2B4, 0x372A2C, 0x38191F)),
        tonal("sunset_gradient", "落日余晖",
            longArrayOf(0xFBE8E2, 0xFFFDFC, 0xECCFC6, 0x2E1913, 0xFE8E6C, 0x882700, 0x574A45, 0xDECDC8, 0xFDD3C7),
            longArrayOf(0x180A07, 0x3C2E2A, 0x2C1C17, 0xF7EEEB, 0xD0440D, 0xFFC0AD, 0xC4B3AE, 0x372A27, 0x381B11),
            BackgroundConfig(type = "gradient", angle = 90, colors = listOf(0xF9E7E1, 0xFDCABB), colorsDark = listOf(0x100402, 0x2D0800))),
        tonal("aurora_gradient", "极光青蓝",
            longArrayOf(0xDDF2F0, 0xF9FFFE, 0xBDDEDA, 0x072523, 0x2BC3BA, 0x005955, 0x41514F, 0xC3D5D3, 0xB9E9E5),
            longArrayOf(0x021210, 0x253534, 0x0F2523, 0xE9F2F1, 0x0A837D, 0x60EAE1, 0xAABCBA, 0x223130, 0x032A28),
            BackgroundConfig(type = "gradient", angle = 90, colors = listOf(0xDCF0EE, 0xA4E6E0), colorsDark = listOf(0x000A09, 0x011B19))),
        tonal("mint_green", "薄荷浅绿",
            longArrayOf(0xD3F0D2, 0xF6FEF6, 0xB4DCB3, 0x112E12, 0x7CCA7C, 0x015E11, 0x475046, 0xC9D5C9, 0xCBE7CA),
            longArrayOf(0x071A07, 0x283D28, 0x132C13, 0xE9F4E9, 0x37853A, 0x98E898, 0xB0BBB0, 0x283128, 0x142A14)),
        tonal("cream_beige", "奶油米白",
            longArrayOf(0xF7E1D9, 0xFFFBF9, 0xE6C8BD, 0x34211A, 0xD1AFA3, 0x62453B, 0x534B49, 0xD8CFCC, 0xEFD9D1),
            longArrayOf(0x1E110C, 0x42332D, 0x32201A, 0xF6EEEB, 0x8E6F64, 0xEBC8BC, 0xBEB5B2, 0x332C2A, 0x30201A)),
        tonal("olivcelso_desert", "大漠流金",
            longArrayOf(0xF4ECDD, 0xFFFDFA, 0xE0D5BC, 0x271E0A, 0xD4AB4F, 0x624800, 0x524C41, 0xD7D0C3, 0xEDDCB9),
            longArrayOf(0x130D03, 0x373125, 0x271F10, 0xF3F0E9, 0x957115, 0xF5CB70, 0xBDB7A9, 0x322D22, 0x2F2203),
            BackgroundConfig(type = "image", src = "themes/olivcelso-desert-bg.jpg", fit = "cover", overlayAlpha = 0.72f, overlayAlphaDark = 0.69f, overlayColor = 0xF4ECDD, overlayColorDark = 0x130D03)),
    )
}
