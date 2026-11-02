package com.kingzcheung.xime.service

/**
 * 韩语两벌식组字：按下的字母（兼容字母 ㄱ ㅏ…）拼成音节（가 각 갃），换音节时把上一个音节交出去。
 * 只记当前这个音节按过的字母，退格一次去掉一个字母（각 → 가 → ㄱ → 空）。
 * 元音跟在收音后面时收音移到下一个音节（갑 + ㅏ → 가바，닭 + ㅏ → 달가）。
 */
internal class HangulComposer {
    private val keys = mutableListOf<Char>()

    /** 正在拼的音节；没在拼时为空。 */
    @Volatile var composing: String = ""
        private set

    val isActive: Boolean get() = composing.isNotEmpty()

    /** 打一个字母，返回因此拼完、该交出去的文字（可能为空）。 */
    fun input(jamo: Char): String {
        require(isJamo(jamo)) { "not a hangul letter: $jamo" }
        val joined = compose(keys + jamo)
        if (joined != null) {
            keys += jamo
            composing = joined
            return ""
        }
        // 元音接在收音后：最后一个辅音带走去做下一个音节的声母。
        if (jamo in VOWELS && keys.size >= 3 && keys.last() in CONSONANTS) {
            val moved = keys.last()
            val done = compose(keys.dropLast(1)).orEmpty()
            keys.clear()
            keys += moved
            keys += jamo
            composing = compose(keys).orEmpty()
            return done
        }
        val done = composing
        keys.clear()
        keys += jamo
        composing = jamo.toString()
        return done
    }

    /** 去掉最后打的一个字母；没在拼时返回 false（交给普通退格）。 */
    fun backspace(): Boolean {
        if (keys.isEmpty()) return false
        keys.removeAt(keys.lastIndex)
        composing = compose(keys).orEmpty()
        return true
    }

    fun reset() {
        keys.clear()
        composing = ""
    }

    companion object {
        private const val INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        private const val MEDIALS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        /** 下标 0 是没有收音。 */
        private const val FINALS = "\u0000ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
        private const val CONSONANTS = INITIALS
        /** 键盘上能单独打的元音（复合元音由两个拼成）。 */
        private const val VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅛㅜㅠㅡㅣ"
        private val COMPOUND_VOWELS = mapOf(
            "ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ', "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ')
        private val COMPOUND_FINALS = mapOf(
            "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ',
            "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ')
        /** Shift 打出的双辅音和 ㅒ ㅖ。 */
        val SHIFTED = mapOf('ㅂ' to 'ㅃ', 'ㅈ' to 'ㅉ', 'ㄷ' to 'ㄸ', 'ㄱ' to 'ㄲ', 'ㅅ' to 'ㅆ', 'ㅐ' to 'ㅒ', 'ㅔ' to 'ㅖ')

        fun isJamo(char: Char): Boolean = char in CONSONANTS || char in VOWELS

        /** 这些字母正好拼成一个音节（或单独一个字母、一个复合元音）时返回它，否则 null。 */
        private fun compose(keys: List<Char>): String? {
            if (keys.isEmpty()) return null
            // 先读元音：一个或两个拼成的复合元音。
            fun vowel(at: Int): Pair<Char, Int>? {
                val first = keys.getOrNull(at)?.takeIf { it in VOWELS } ?: return null
                val second = keys.getOrNull(at + 1)
                COMPOUND_VOWELS["$first$second"]?.let { return it to 2 }
                return first to 1
            }
            if (keys[0] in VOWELS) {
                val (medial, used) = vowel(0) ?: return null
                return if (used == keys.size) medial.toString() else null
            }
            val initial = keys[0]
            if (keys.size == 1) return initial.toString()
            val (medial, used) = vowel(1) ?: return null
            val rest = keys.drop(1 + used)
            val final = when (rest.size) {
                0 -> '\u0000'
                1 -> rest[0].takeIf { it != '\u0000' && FINALS.indexOf(it) > 0 } ?: return null
                2 -> COMPOUND_FINALS["${rest[0]}${rest[1]}"] ?: return null
                else -> return null
            }
            val code = 0xAC00 + (INITIALS.indexOf(initial) * 21 + MEDIALS.indexOf(medial)) * 28 + FINALS.indexOf(final)
            return code.toChar().toString()
        }
    }
}
