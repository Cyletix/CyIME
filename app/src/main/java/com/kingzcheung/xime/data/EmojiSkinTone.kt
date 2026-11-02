package com.kingzcheung.xime.data

/**
 * 表情肤色：长按人物/手势表情时给出 5 种肤色（U+1F3FB–1F3FF）。
 * 点按仍上屏原表情（黄色），不记忆上次肤色。
 * 只对 Unicode 规定可加肤色的字符生效；家庭类组合没有肤色，不弹。
 */
object EmojiSkinTone {
    val MODIFIERS = intArrayOf(0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF)
    private const val ZWJ = 0x200D
    private const val VS16 = 0xFE0F
    private const val HEART = 0x2764
    private const val HANDSHAKE = 0x1F91D

    /** Emoji_Modifier_Base 中实际有肤色序列的字符（去掉 👪 👯 🤼）。 */
    private val BASE_RANGES = arrayOf(
        0x261D..0x261D, 0x26F9..0x26F9, 0x270A..0x270D,
        0x1F385..0x1F385, 0x1F3C2..0x1F3C4, 0x1F3C7..0x1F3C7, 0x1F3CA..0x1F3CC,
        0x1F442..0x1F443, 0x1F446..0x1F450, 0x1F466..0x1F469, 0x1F46B..0x1F46E,
        0x1F470..0x1F478, 0x1F47C..0x1F47C, 0x1F481..0x1F483, 0x1F485..0x1F487,
        0x1F48F..0x1F48F, 0x1F491..0x1F491, 0x1F4AA..0x1F4AA, 0x1F574..0x1F575,
        0x1F57A..0x1F57A, 0x1F590..0x1F590, 0x1F595..0x1F596, 0x1F645..0x1F647,
        0x1F64B..0x1F64F, 0x1F6A3..0x1F6A3, 0x1F6B4..0x1F6B6, 0x1F6C0..0x1F6C0,
        0x1F6CC..0x1F6CC, 0x1F90C..0x1F90C, 0x1F90F..0x1F90F, 0x1F918..0x1F91F,
        0x1F926..0x1F926, 0x1F930..0x1F939, 0x1F93D..0x1F93E, 0x1F977..0x1F977,
        0x1F9B5..0x1F9B6, 0x1F9B8..0x1F9B9, 0x1F9BB..0x1F9BB, 0x1F9CD..0x1F9CF,
        0x1F9D1..0x1F9DD, 0x1FAC3..0x1FAC5, 0x1FAF0..0x1FAF8,
    )

    /** 默认文字样式的基字符，去掉肤色后要补回 FE0F 才显示成彩色表情。 */
    private val TEXT_DEFAULT = setOf(0x261D, 0x26F9, 0x270C, 0x270D, 0x1F3CB, 0x1F3CC,
        0x1F574, 0x1F575, 0x1F590)

    private fun isBase(cp: Int) = BASE_RANGES.any { cp in it }
    private fun isModifier(cp: Int) = cp in 0x1F3FB..0x1F3FF

    /** 去掉已有肤色，得到黄色原表情（最近使用里存的是带肤色的）。 */
    fun base(emoji: String): String {
        val cps = emoji.codePoints().toArray()
        if (cps.none(::isModifier)) return emoji
        val out = ArrayList<Int>(cps.size + 1)
        cps.forEachIndexed { i, cp ->
            if (isModifier(cp)) {
                val prev = cps.getOrNull(i - 1)
                if (prev != null && prev in TEXT_DEFAULT && cps.getOrNull(i + 1) != VS16) out += VS16
            } else out += cp
        }
        return String(out.toIntArray(), 0, out.size)
    }

    /** 可加肤色时返回 [原表情, 5 种肤色]；否则返回空表。 */
    fun variants(emoji: String): List<String> {
        val plain = base(emoji)
        val cps = plain.codePoints().toArray()
        val hasZwj = ZWJ in cps
        val bases = cps.indices.filter { i ->
            isBase(cps[i]) && !(hasZwj && cps[i] == HANDSHAKE)
        }
        if (bases.isEmpty()) return emptyList()
        // 组合里两个人：情侣（❤）和牵手（🤝）可统一加肤色；家庭没有肤色。
        if (bases.size > 1 && HEART !in cps && HANDSHAKE !in cps) return emptyList()
        return listOf(plain) + MODIFIERS.map { modifier ->
            val out = ArrayList<Int>(cps.size + bases.size)
            var i = 0
            while (i < cps.size) {
                out += cps[i]
                if (i in bases) {
                    out += modifier
                    if (cps.getOrNull(i + 1) == VS16) i++ // 肤色取代 FE0F
                }
                i++
            }
            String(out.toIntArray(), 0, out.size)
        }
    }

    fun supports(emoji: String): Boolean = variants(emoji).isNotEmpty()
}
