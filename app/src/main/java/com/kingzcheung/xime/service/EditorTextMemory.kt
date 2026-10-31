package com.kingzcheung.xime.service

/**
 * 宿主输入框光标前文字的已知副本，供 [TrackedInputConnection] 回答 getTextBeforeCursor，
 * 免得每上屏一次、每打一个新词都同步等宿主一次（B 站放视频时宿主主线程忙，一次 130 ms 左右）。
 *
 * - 真读到一次后记下文字和当时的光标位置；之后自己 commitText(…, 1) 上屏的文字直接接在后面，光标位置跟着加。
 * - 宿主报来的选区是自己上屏后该到的位置（含还没报到的更早几次）就继续用；对不上
 *   （点了别处、宿主自己改了字、发送后清空）就整份丢掉，下次照旧去读。
 * - 任何别的编辑（退格、删字、移光标、组合区、编辑器动作、图片……）一律丢掉，光标位置也不再猜。
 * - 有选区、有组合区时不记；宿主还没回执过自己上屏的位置之前，刚上屏的那段不拿来回答。
 *
 * 只是宿主文字的一份副本：拿不准就返回 null，由调用方照旧问宿主。
 */
internal class EditorTextMemory(private val capacity: Int = CAPACITY) {
    private var text: String? = null
    /** [text] 从输入框开头算起（上一次真读到的比要的短）。 */
    private var reachesStart = false
    /** [text] 已知时：自己每次上屏后光标该到的位置，最早的在前，最后一个就是现在的位置。 */
    private val carets = ArrayDeque<Int>()
    /** [text] 未知时对宿主光标的估计（宿主最近报来的折叠选区，加上之后自己上屏的长度）；不知道是 [UNKNOWN]。 */
    private var caret = UNKNOWN
    /** 自己写了组合区（或宿主报来有组合区）：光标前文字里混着会被替换的编码，不记。 */
    private var composing = false
    /** 这个输入框回执过自己上屏后的光标位置：之后刚上屏、还没回执的文字也可以拿来回答。 */
    private var echoesCommits = false
    /** 密码框、终端：不记任何文字。 */
    private var disabled = false

    /** 光标前 [length] 个字符；不确定时 null。 */
    @Synchronized fun textBefore(length: Int): String? {
        val known = text ?: return null
        if (disabled || composing || length < 0) return null
        if (carets.size > 1 && !echoesCommits) return null
        return when {
            known.length >= length -> known.substring(known.length - length)
            reachesStart && length <= capacity -> known
            else -> null
        }
    }

    /** 刚真读到光标前的文字（要了 [requested] 个字符）。光标位置拿不准时不记。 */
    @Synchronized fun learn(before: CharSequence?, requested: Int) {
        if (disabled || composing || before == null) return
        val at = currentCaret()
        if (at < 0) return
        // 已知时只刷新文字：还没报到的上屏位置留着，回执到了才知道这个输入框会回执。
        if (text == null) { carets.clear(); carets.addLast(at) }
        text = before.toString().takeLast(capacity)
        reachesStart = before.length < requested && before.length <= capacity
    }

    /** commitText 已交给宿主。 */
    @Synchronized fun committed(inserted: CharSequence?, newCursorPosition: Int, accepted: Boolean) {
        val replacedComposition = composing
        composing = false
        if (!accepted || inserted == null || newCursorPosition != 1 || replacedComposition) { edited(); return }
        if (inserted.isEmpty()) return
        val at = currentCaret()
        if (at < 0) return
        val known = text
        if (known == null) { caret = at + inserted.length; return }
        val joined = known + inserted
        reachesStart = reachesStart && joined.length <= capacity
        text = joined.takeLast(capacity)
        carets.addLast(at + inserted.length)
        while (carets.size > MAX_PENDING) carets.removeFirst()
    }

    /** setComposingText 已交给宿主：写了编码就不再记；清空不存在的组合区什么都没改。 */
    @Synchronized fun composingSet(composingText: CharSequence?, accepted: Boolean) {
        if (!accepted) { edited(); return }
        if (!composingText.isNullOrEmpty()) { composing = true; edited(); return }
        if (composing) { composing = false; edited() }
    }

    /** finishComposingText：组合区里的编码变成正文，位置不再可知；没有组合区时什么都没改。 */
    @Synchronized fun composingFinished() {
        if (composing) { composing = false; edited() }
    }

    /** setComposingRegion：把已有文字标成组合区。 */
    @Synchronized fun composingRegion(start: Int, end: Int) {
        composing = start != end
        edited()
    }

    /** 不是 commitText(…, 1) 的任何编辑：丢掉文字，光标位置也不再猜，等宿主下一次报来。 */
    @Synchronized fun edited() {
        text = null
        reachesStart = false
        carets.clear()
        caret = UNKNOWN
    }

    /** onUpdateSelection。 */
    @Synchronized fun selectionReported(selStart: Int, selEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        val hasComposition = candidatesStart >= 0 && candidatesEnd != candidatesStart
        if (hasComposition) composing = true
        val collapsed = !hasComposition && selStart >= 0 && selStart == selEnd
        if (text != null) {
            val at = if (collapsed) carets.indexOf(selStart) else -1
            if (at >= 0) {
                // 自己某次上屏的回执：比它早的也都到了。
                if (at > 0) echoesCommits = true
                repeat(at) { carets.removeFirst() }
                return
            }
            edited()
        }
        caret = if (collapsed) selStart else UNKNOWN
    }

    /** 新输入框（或同一输入框重启）：从框架给的初始选区开始，之前的一律不算。 */
    @Synchronized fun started(selStart: Int, selEnd: Int, enabled: Boolean) {
        edited()
        composing = false
        echoesCommits = false
        disabled = !enabled
        caret = if (selStart >= 0 && selStart == selEnd) selStart else UNKNOWN
    }

    private fun currentCaret(): Int = if (text != null) carets.last() else caret

    companion object {
        const val CAPACITY = 64
        private const val MAX_PENDING = 32
        private const val UNKNOWN = -1
    }
}
