package com.kingzcheung.xime.settings

import com.kingzcheung.xime.keyboard.GestureAction

/**
 * 合并键布局（14/17/18 键、自建的双字母布局）共用的角标规则，按键的读顺序排（用户 10-30：各布局要统一）：
 * 前 10 个键上方 1-0、下方 + @ # / % - _ * （ ）；其余的键上方依次是 、：！？…，下方依次是 ……～《》…。
 * 14 键正好是 [FourteenKeySymbols] 那张表。印什么上屏什么，空串表示这一格没有符号。
 */
internal object MergedKeySymbols {
    private val digitUp = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val digitDown = listOf("+", "@", "#", "/", "%", "-", "_", "*", "（", "）")
    // 各 16 个：自建布局最多 25 个键（合并一组），前 10 个之外最多 15 个，都排得下。
    // 成对的排在相邻两格，14/17/18 键都不会只拿到半对（18 键第 8 个是 ——）。
    private val restUp = listOf("、", "：", "！", "？", "；", "·", "¥", "——", "【", "】", "「", "」", "『", "』", "$", "€")
    private val restDown = listOf("……", "～", "《", "》", "&", "=", "'", "\"", "[", "]", "<", ">", "\\", "|", "`", "^")

    private fun table(keys: List<String>, digits: List<String>, rest: List<String>) = keys.withIndex().associate { (index, key) ->
        key to if (index < digits.size) digits[index] else rest.getOrElse(index - digits.size) { "" }
    }
    fun up(keys: List<String>): Map<String, String> = table(keys, digitUp, restUp)
    fun down(keys: List<String>): Map<String, String> = table(keys, digitDown, restDown)

    /** 键 id 原样返回；Shift 或分体拆开的单个字母返回它所在的合并键（w → qw）；不是字母键的原样返回。 */
    fun ownerOf(keys: List<String>, key: String): String =
        if (key in keys) key else keys.firstOrNull { key.length == 1 && key in it } ?: key

    /** 设置页的修改 > xime.custom.yaml 里给这个键写的 swipe_up/swipe_down > 预设。不是这个布局的字母键原样返回。 */
    fun apply(keys: List<String>, key: String, original: KeyGestureConfig?, overrides: Map<String, String>): KeyGestureConfig? {
        if (key !in keys) return original
        fun gesture(direction: String, preset: String, configured: GestureDef?): GestureDef {
            val override = overrides["$direction.$key"]
            if (override == null && configured != null) return configured
            val value = override ?: preset
            return GestureDef(label = value, value = value,
                display = if (direction == "up") original?.swipeUp?.display ?: DisplayMode.BOTH else DisplayMode.BOTH,
                action = if (value.isEmpty()) GestureAction.NONE else GestureAction.COMMIT)
        }
        return (original ?: KeyGestureConfig()).copy(
            swipeUp = gesture("up", up(keys).getValue(key), original?.swipeUp),
            swipeDown = gesture("down", down(keys).getValue(key), original?.swipeDown))
    }
}
