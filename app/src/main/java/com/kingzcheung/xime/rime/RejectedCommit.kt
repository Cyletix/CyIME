package com.kingzcheung.xime.rime

/**
 * 「删掉刚上屏的词不学习」（B站反馈 10-31，别人的需求，默认关）：误触打出的怪词一上屏就被 Rime 记进用户词库，
 * 在输入框里删掉它也还记着。开着时，Rime 刚上屏的一段中文紧接着被整个删掉（一下下退格删到它的第一个字，
 * 或上滑清空），就从用户词库里减去这一次学习，当作没选过。
 *
 * 只记最近一次 Rime 上屏、且这次真的学了（不在无痕里）、含汉字的那段；中间打了别的字、光标挪走（光标前
 * 已经不是这段的剩余部分）、换了输入框就作废，不会减掉以前别的学习。只删掉后半截不算：可能只是改个尾巴。
 */
internal class RejectedCommit {
    private var pending: String? = null
    private var deleted = 0

    /** Rime 刚交出一段上屏文字；[learned] 为 false（无痕）时这次本来就没学，不跟踪。 */
    @Synchronized fun committed(text: String, learned: Boolean) {
        pending = text.takeIf { learned && hasHan(it) }
        deleted = 0
    }

    @Synchronized fun clear() {
        pending = null
        deleted = 0
    }

    /**
     * 一下退格删正文之前调用。[textBeforeCursor] 至少读到这段的长度；有选区时退格删的是选区，作废。
     * 这一下删掉这段的第一个字时返回要减去学习的词，否则 null。
     */
    @Synchronized fun beforeBackspace(textBeforeCursor: CharSequence?, hasSelection: Boolean): String? {
        val text = pending ?: return null
        val left = text.substring(0, text.length - deleted)
        if (hasSelection || textBeforeCursor == null || !textBeforeCursor.endsWith(left)) {
            clear()
            return null
        }
        deleted += Character.charCount(Character.codePointBefore(left, left.length))
        if (deleted < text.length) return null
        clear()
        return learnedWord(text)
    }

    /** 上滑清空整个输入框之前调用：光标前还以这段（剩下的部分）结尾就算整个删掉。 */
    @Synchronized fun beforeClear(textBeforeCursor: CharSequence?): String? {
        val text = pending ?: return null
        val left = text.substring(0, text.length - deleted)
        clear()
        if (textBeforeCursor == null || !textBeforeCursor.endsWith(left)) return null
        return learnedWord(text)
    }

    /** 要读多少光标前文字才够判断；0 = 没有在跟踪，不必读输入框。 */
    @Synchronized fun neededLength(): Int = pending?.let { it.length - deleted } ?: 0

    companion object {
        /** 上屏里带的标点（如按逗号选词时的「，」）不在词库里，去掉两头的非文字再减。 */
        /** 按码点判断：扩展 B 以后的字（𠀀）是一对代理字符，单看每一半都不算文字。 */
        internal fun learnedWord(text: String): String? {
            var start = 0
            var end = text.length
            while (start < end && !Character.isLetterOrDigit(text.codePointAt(start)))
                start += Character.charCount(text.codePointAt(start))
            while (end > start && !Character.isLetterOrDigit(text.codePointBefore(end)))
                end -= Character.charCount(text.codePointBefore(end))
            return text.substring(start, end).takeIf { it.isNotEmpty() }
        }

        internal fun hasHan(text: String): Boolean =
            text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }
    }
}
