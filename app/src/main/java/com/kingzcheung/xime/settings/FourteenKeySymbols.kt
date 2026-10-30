package com.kingzcheung.xime.settings

import android.content.Context

/**
 * 中文 14 键自己的一套角标，按键（qw、er…m）配，不再借 26 键单个字母的（用户 10-30：照搬 26 键欠考虑）。
 * 前两排：上方 1-0，下方运算和网址常用的半角符号，@ # % * 在电脑 Shift+2/3/5/8 的位置，（ ）在 9、0 下面；
 * 第三排：上方 、：！？，下方 …… ～ 《 》。逗号句号在空格两边，不占角标。
 * 和 26 键一样：印什么上屏什么，不随全角／半角转换；手势方向、长按输入符号照旧。
 * 这张表就是合并键共用规则 [MergedKeySymbols] 排在 14 个键上的结果，这里另存一份是给符号设置页和测试对照。
 */
internal object FourteenKeySymbols {
    val rows = listOf(listOf("qw", "er", "ty", "ui", "op"), listOf("as", "df", "gh", "jk", "l"), listOf("zx", "cv", "bn", "m"))
    val keys = rows.flatten()
    private fun preset(vararg values: String) = keys.zip(values.toList()).toMap()
    val up = preset(
        "1", "2", "3", "4", "5",
        "6", "7", "8", "9", "0",
        "、", "：", "！", "？")
    val down = preset(
        "+", "@", "#", "/", "%",
        "-", "_", "*", "（", "）",
        "……", "～", "《", "》")
    /** 键 id 原样返回；拆开的单个字母返回它所在的双字母键（w → qw），其他键原样返回。 */
    fun keyOf(key: String): String = MergedKeySymbols.ownerOf(keys, key)
    private const val PREFIX = "k14_symbols_zh_"
    /** 28 个位置：先全部上方，再全部下方。 */
    val slotIds: List<String> get() = listOf("up", "down").flatMap { direction -> keys.map { "$direction.$it" } }

    fun load(context: Context): Map<String, String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        return buildMap {
            for (id in slotIds) if (prefs.contains(PREFIX + id)) put(id, prefs.getString(PREFIX + id, "").orEmpty())
        }
    }

    /** 和 26 键一样只存与预设不同的位置；null 清掉修改回到预设。 */
    fun save(context: Context, values: Map<String, String>) {
        val edit = SettingsPreferences.getPrefsPublic(context).edit()
        for (id in slotIds) {
            val value = values[id]
            if (value == null) edit.remove(PREFIX + id) else edit.putString(PREFIX + id, value)
        }
        check(edit.commit()) { "无法保存符号设置" }
        KeysConfigHelper.loadConfig(context)
    }

    /** 符号设置页的修改 > xime.custom.yaml 里给这个键写的 swipe_up/swipe_down > 预设。 */
    fun apply(key: String, original: KeyGestureConfig?, overrides: Map<String, String>): KeyGestureConfig? =
        MergedKeySymbols.apply(keys, key, original, overrides)
}
