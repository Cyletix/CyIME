package com.kingzcheung.xime.service

/**
 * 英文选词后自动加空格（默认关，别人请求，10-31）：在候选栏点一个英文补全词，词后自动补一个空格，
 * 接着打下一个词不用再按空格。补的这个空格只管紧接着的一个键：
 * - 空格：不再多打一个（已经有了）；
 * - 句末标点 , . ? ! : ;：标点放到空格前面（word, ）；右括号 ) ] }：去掉空格（word)）；
 * - 退格：照常删掉这个空格；其他键照常，空格保留。
 * Shift、切换数字/符号页这些不打字的键不算，选完词切到符号页打 ? ! 照样换位置。
 * 光标前已经不是刚上屏的“词+空格”（挪了光标、宿主改了文字）就当没补过，这个键照常处理。
 */
internal class EnglishAutoSpace {
    /** 刚上屏的“词+空格”；null 表示没有待处理的自动空格。按键线程读、主线程写。 */
    @Volatile private var armed: String? = null

    val isArmed: Boolean get() = armed != null

    fun arm(word: String) { armed = "$word " }

    fun disarm() { armed = null }

    /** 只管紧接着的一个键：取出后即失效。 */
    fun take(): String? = armed.also { armed = null }

    companion object {
        /** 设置键，默认关。 */
        const val PREF_KEY = com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences.ENGLISH_AUTO_SPACE_KEY

        /** 只换键盘页、不打字的键（Shift、切到数字或符号页再切回来）：自动空格留到下一个真正打字的键。 */
        private val NEUTRAL_KEYS = setOf("shift", "mode_change", "number", "common_symbol", "abc")

        fun isNeutral(key: String): Boolean = key in NEUTRAL_KEYS

        private const val MOVE_BEFORE = ".,?!:;"
        private const val DROP_BEFORE = ")]}"

        /**
         * 紧跟自动空格的键要怎么改：返回替换光标前那个空格的文字（空串表示空格键本身，什么都不用再打）；
         * null 表示这个键照常处理、空格保留。
         */
        fun replacementFor(key: String): String? = when {
            key == "space" || key == " " -> ""
            key.length == 1 && key[0] in MOVE_BEFORE -> "$key "
            key.length == 1 && key[0] in DROP_BEFORE -> key
            else -> null
        }
    }
}

/** 选中的联想/补全候选对应的词（英文有原词时第 0 个是原词）；越界返回 null。 */
internal fun associationWordAt(state: CandidateState, index: Int): String? {
    val pending = state.pendingEnglishText
    val hasOriginal = pending.isNotEmpty() && state.englishReplaceSupported
    val words = if (hasOriginal) listOf(pending) + state.associationCandidates else state.associationCandidates
    return words.getOrNull(index)
}
