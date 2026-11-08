package com.kingzcheung.xime.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.kingzcheung.xime.platform.AndroidPlatform

/**
 * 莫奈取色（Material You 动态配色，Android 12+）：从系统壁纸调色板（system_accent/neutral 资源）
 * 构建键盘主题，随用户壁纸变化。
 * 10-11 按「只留四个主题」从列表隐藏，11-03 用户要求加回配色方案；Android 12 以下不出现在列表里。
 *
 * framework 资源编号与 Material tonal tone 反向对应：编号 n ≈ tone (1000 - n) / 100 的近似档位，
 * 即 system_neutral1_10 近白（tone 99）、system_neutral1_900 近黑（tone 10）。
 * 档位选择对齐 material3 dynamicLight/DarkColorScheme 的浅深色规范。
 */
object DynamicThemes {
    /** 内置动态配色方案的 id；xime.custom.yaml 里 style.color_scheme 也可以写 dynamic。 */
    const val THEME_ID = "dynamic"

    /** 配色方案列表里的名字（用户的叫法）。 */
    const val NAME = "莫奈取色"

    /** 动态配色仅在 Android 12+ 可用。 */
    fun isSupported(): Boolean = AndroidPlatform.dynamicColor

    /**
     * 读取当前系统取色的主强调色（浅/深），用于与缓存中的动态主题对比，
     * 判断壁纸取色是否发生了变化。不支持时返回 null。
     */
    fun currentAccentColors(context: Context): Pair<Color, Color>? {
        if (!isSupported()) return null
        val p = SystemPalette(context)
        return p.accent1(600) to p.accent1(200)
    }

    internal fun fallback(id: String = THEME_ID, name: String = NAME) =
        SoftBlueTheme.create(id, if (id == THEME_ID) NAME else name)

    /**
     * 构建动态配色键盘主题：强调色来自壁纸，深色功能键使用柔和中性容器。
     * Android 12 以下使用中性深灰/蓝色兼容配色，保留同一主题入口。
     */
    fun create(context: Context, id: String = THEME_ID, name: String = NAME): KeyboardColorScheme? {
        if (!isSupported()) return fallback(id, name)
        val p = SystemPalette(context)
        val accentLight = p.accent1(600)
        val accentDark = p.accent1(200)
        return KeyboardColorScheme(
            id = id,
            name = if (id == THEME_ID) NAME else name,
            // 浅色底用中性 100 色调，白键与底约 1.25:1；功能键再深一级，不与底色同一色调。
            specialKeyLight = p.accent1(200),
            specialKeyDark = softDarkKeyContainer(accentDark),
            accentLight = accentLight,
            accentDark = accentDark,
            primaryLight = accentLight,
            primaryDark = accentDark,
            primaryContainerLight = p.accent1(100),
            primaryContainerDark = softDarkKeyContainer(accentDark),
            surfaceLight = p.neutral1(100),
            surfaceDark = p.neutral1(900),
            keyboardBgLight = p.neutral1(100),
            keyboardBgDark = p.neutral1(900),
            keyBgLight = p.neutral1(0),
            keyBgDark = p.neutral1(800),
            candidateBarBgLight = p.neutral1(100),
            candidateBarBgDark = p.neutral1(900),
            keyTextColorLight = p.neutral1(900),
            keyTextColorDark = p.neutral1(100),
            candidateTextColorLight = p.accent1(700),
            candidateTextColorDark = accentDark,
            candidateSelectedTextColorLight = accentLight,
            candidateSelectedTextColorDark = accentDark,
            dividerColorLight = p.neutral2(100),
            dividerColorDark = p.neutral2(700),
            isDynamic = true,
            // 与其他完整主题同一套规则：设置页也按这些颜色生成，和键盘同源
            // （10-11 用户截图里设置页偏蓝紫、键盘偏青蓝，两边不一致）。
            useThemeColors = true,
        )
    }
}

/** framework 系统调色板资源读取器，按名缓存解析结果。 */
private class SystemPalette(context: Context) {
    private val appContext = context.applicationContext
    private val cache = HashMap<String, Color>()

    /** 资源缺失时的兜底色（中性深灰/蓝色），保证动态主题在任何设备上不崩溃。 */
    private fun resolve(resource: String, fallback: Color): Color {
        cache[resource]?.let { return it }
        val color = try {
            val id = appContext.resources.getIdentifier(resource, "color", "android")
            if (id != 0) Color(appContext.getColor(id)) else null
        } catch (_: Throwable) {
            null
        }
        return if (color != null) {
            cache[resource] = color
            color
        } else {
            fallback
        }
    }

    fun accent1(resource: Int): Color =
        resolve("system_accent1_$resource", if (resource >= 500) Color(0xFF405F91) else Color(0xFFA8C7FA))

    fun neutral1(resource: Int): Color =
        resolve("system_neutral1_$resource", Color(0xFF191C20))

    fun neutral2(resource: Int): Color =
        resolve("system_neutral2_$resource", Color(0xFF3C4043))
}
