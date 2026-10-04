package com.kingzcheung.xime.service

import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import kotlin.math.abs

/** 编辑器光标操作不经过 Rime，所有键盘布局使用相同的选择区语义。 */
internal class EditorCursor {
    private var connection: InputConnection? = null
    private var anchor: Int? = null
    private var active: Int? = null
    private var reportedStart: Int? = null
    private var reportedEnd: Int? = null

    fun observeSelection(ic: InputConnection, start: Int, end: Int) {
        bind(ic)
        reportedStart = start.takeIf { it >= 0 }
        reportedEnd = end.takeIf { it >= 0 }
    }

    fun beginSelection(ic: InputConnection) {
        bind(ic)
        resetSelection()
        snapshot(ic)?.let { syncSelection(it) }
    }

    fun resetSelection() { anchor = null; active = null }

    /** 取消选择保留活动端的位置，反向选择不能误折叠到较大的端点。 */
    fun endSelection(ic: InputConnection) {
        bind(ic)
        val extracted = snapshot(ic)
        extracted?.let { syncSelection(it) }
        val collapsed = active?.let { runCatching { ic.setSelection(it, it) }.getOrDefault(false) } == true
        if (!collapsed) {
            // WebView 等宿主可能没有 ExtractedText 或拒绝 setSelection。
            // 只有确认仍有选区时才交给宿主折叠，避免普通光标被多移动一格。
            val hasSelection = (extracted != null && extracted.selectionStart != extracted.selectionEnd) ||
                runCatching { !ic.getSelectedText(0).isNullOrEmpty() }.getOrDefault(false)
            if (hasSelection) {
                val key = if (active != null && anchor != null && active!! < anchor!!)
                    KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                runCatching { sendDirection(ic, key) }
            }
        }
        resetSelection()
    }

    // Some web editors omit ExtractedText but expose absolute selection updates and
    // surrounding text. Use those instead of sending DPAD focus-navigation events.
    private fun surroundingSnapshot(ic: InputConnection): ExtractedText? {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val surrounding = runCatching { ic.getSurroundingText(4096, 4096, 0) }.getOrNull()
            if (surrounding != null && surrounding.offset >= 0 &&
                surrounding.selectionStart in 0..surrounding.text.length &&
                surrounding.selectionEnd in 0..surrounding.text.length) {
                return ExtractedText().apply {
                    text = surrounding.text
                    startOffset = surrounding.offset
                    selectionStart = surrounding.selectionStart
                    selectionEnd = surrounding.selectionEnd
                }
            }
        }
        val start = reportedStart ?: return null
        val end = reportedEnd ?: return null
        return runCatching {
            val before = ic.getTextBeforeCursor(4096, 0) ?: return null
            val after = ic.getTextAfterCursor(4096, 0) ?: return null
            val selected = if (start == end) "" else ic.getSelectedText(0)?.toString() ?: return null
            if (selected.length != abs(end - start) || before.length > minOf(start, end)) return null
            ExtractedText().apply {
                text = before.toString() + selected + after
                startOffset = minOf(start, end) - before.length
                selectionStart = start - startOffset
                selectionEnd = end - startOffset
            }
        }.getOrNull()
    }
    private fun snapshot(ic: InputConnection): ExtractedText? =
        runCatching { ic.getExtractedText(ExtractedTextRequest(), 0) }.getOrNull()?.takeIf {
            val length = it.text?.length ?: return@takeIf false
            it.startOffset >= 0 && it.selectionStart in 0..length && it.selectionEnd in 0..length
        } ?: surroundingSnapshot(ic)

    private fun syncSelection(extracted: ExtractedText) {
        val start = extracted.startOffset + extracted.selectionStart
        val end = extracted.startOffset + extracted.selectionEnd
        // InputConnection 可将反向选区规范为 min/max，selectionEnd 不一定是活动端。
        active = when (anchor) {
            start -> end
            end -> start
            else -> { anchor = start; end }
        }
    }

    private fun setSelection(ic: InputConnection, target: Int, selecting: Boolean): Boolean {
        val start = if (selecting) anchor ?: target else target
        if (!runCatching { ic.setSelection(start, target) }.getOrDefault(false)) return false
        reportedStart = start
        reportedEnd = target
        if (selecting) { anchor = start; active = target }
        return true
    }

    private fun bind(ic: InputConnection) {
        if (connection !== ic) {
            connection = ic
            resetSelection()
            reportedStart = null
            reportedEnd = null
        }
    }

    /** 每一个成功的字符步长独立通知，边界和失败不产生额外触觉。 */
    fun moveWithFeedback(ic: InputConnection, steps: Int, onStep: () -> Unit) {
        repeat(abs(steps)) {
            if (!move(ic, steps.compareTo(0))) return
            onStep()
        }
    }

    fun move(ic: InputConnection, steps: Int, selecting: Boolean = false, allowKeyEventFallback: Boolean = true): Boolean {
        if (steps == 0) return false
        bind(ic)
        if (!selecting) resetSelection()
        val extracted = snapshot(ic)
        val text = extracted?.text
        if (text != null && extracted.selectionStart in 0..text.length && extracted.selectionEnd in 0..text.length) {
            val start = extracted.selectionStart
            val end = extracted.selectionEnd
            if (selecting) syncSelection(extracted)
            val collapsed = !selecting && start != end
            val from = if (collapsed) {
                if (steps < 0) minOf(start, end) else maxOf(start, end)
            } else if (selecting) active!! - extracted.startOffset else end
            val remaining = if (collapsed) steps - steps.compareTo(0) else steps
            val next = offsetByCharacters(text, from, remaining)
            val moved = Character.codePointCount(text, minOf(from, next), maxOf(from, next))
            // 片段边缘不一定是文档边缘：查询光标周围文本后计算绝对位置，
            // 在真实文首/文末截停，不能继续发 DPAD 让宿主把焦点移出输入框。
            val target = if (moved == abs(remaining)) extracted.startOffset + next
                else extendedHorizontalTarget(ic, extracted, from, remaining)
            if (target != null && setSelection(ic, target, selecting))
                return target != extracted.startOffset + from || collapsed
        }
        if (!allowKeyEventFallback) return false
        val key = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        var moved = false
        repeat(abs(steps)) {
            val current = snapshot(ic)
            val hasSelection = current?.let { it.selectionStart != it.selectionEnd }
                ?: runCatching { !ic.getSelectedText(0).isNullOrEmpty() }.getOrDefault(false)
            val adjacent = runCatching {
                if (steps < 0) ic.getTextBeforeCursor(1, 0) else ic.getTextAfterCursor(1, 0)
            }.getOrNull()
            if (!hasSelection && adjacent?.isEmpty() == true) return moved
            moved = sendDirection(ic, key, selecting) || moved
        }
        return moved
    }

    /** Never dispatch an arrow to the host: at document edges Android can move focus. */
    fun movePhysical(ic: InputConnection, keyCode: Int, selecting: Boolean = false,
        byWord: Boolean = false, toBoundary: Boolean = false): Boolean {
        val backwards = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP
        val horizontal = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        val direction = if (backwards) -1 else 1
        if (!byWord && !toBoundary) return if (horizontal) move(ic, direction, selecting, allowKeyEventFallback = false)
            else moveVertical(ic, direction, selecting)
        bind(ic)
        if (!selecting) resetSelection()
        val extracted = snapshot(ic) ?: return false
        if (selecting) syncSelection(extracted)
        val position = if (selecting) active!! - extracted.startOffset else extracted.selectionEnd
        val target = if (horizontal && byWord && !toBoundary) {
            val words = java.text.BreakIterator.getWordInstance().apply { setText(extracted.text.toString()) }
            var next = if (backwards) words.preceding(position) else words.following(position)
            while (next != java.text.BreakIterator.DONE && next > 0 && next < extracted.text.length &&
                (if (backwards) extracted.text[next] else extracted.text[next - 1]).isWhitespace()) {
                next = if (backwards) words.preceding(next) else words.following(next)
            }
            if (next == java.text.BreakIterator.DONE) { if (backwards) 0 else extracted.text.length } else next
        } else paragraphBoundary(extracted.text, position, !backwards)
        return setSelection(ic, extracted.startOffset + target, selecting) && target != position
    }

    private fun extendedHorizontalTarget(ic: InputConnection, extracted: ExtractedText, from: Int, steps: Int): Int? {
        // 每码点最多两个 UTF-16 单元；周围文本从选区外端开始，需接上选区内部到活动端的片段。
        val requested = (abs(steps.toLong()) * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val surrounding = runCatching {
            if (steps < 0) ic.getTextBeforeCursor(requested, 0) else ic.getTextAfterCursor(requested, 0)
        }.getOrNull() ?: return null
        return if (steps < 0) {
            val endpoint = minOf(extracted.selectionStart, extracted.selectionEnd)
            val span = surrounding.toString() + extracted.text.subSequence(endpoint, from)
            extracted.startOffset + endpoint - surrounding.length + offsetByCharacters(span, span.length, steps)
        } else {
            val endpoint = maxOf(extracted.selectionStart, extracted.selectionEnd)
            val span = extracted.text.subSequence(from, endpoint).toString() + surrounding
            extracted.startOffset + from + offsetByCharacters(span, 0, steps)
        }
    }

    fun moveToParagraphBoundary(ic: InputConnection, toEnd: Boolean, selecting: Boolean = false) {
        bind(ic)
        if (!selecting) resetSelection()
        val extracted = snapshot(ic)
        if (extracted != null) {
            if (selecting) syncSelection(extracted)
            val position = if (selecting) active!! - extracted.startOffset else extracted.selectionEnd
            val target = paragraphBoundary(extracted.text, position, toEnd)
            val knownBoundary = if (toEnd) target < extracted.text.length else target > 0 || extracted.startOffset == 0
            val absoluteTarget = if (knownBoundary) extracted.startOffset + target
                else extendedParagraphBoundary(ic, extracted, toEnd)
            if (absoluteTarget != null && setSelection(ic, absoluteTarget, selecting)) return
        }
        // 不提供可读文本的宿主只能委托原生 Home/End；保留 Shift 扩选，不能跳到全文首尾。
        sendDirection(ic, if (toEnd) KeyEvent.KEYCODE_MOVE_END else KeyEvent.KEYCODE_MOVE_HOME, selecting)
    }

    private fun extendedParagraphBoundary(ic: InputConnection, extracted: ExtractedText, toEnd: Boolean): Int? {
        val endpoint = extracted.startOffset + if (toEnd) maxOf(extracted.selectionStart, extracted.selectionEnd)
            else minOf(extracted.selectionStart, extracted.selectionEnd)
        var requested = 256
        // 逐步查询，避免把 ExtractedText 的片段末尾或软换行当成段落末尾。
        while (requested <= 1_048_576) {
            val surrounding = runCatching {
                if (toEnd) ic.getTextAfterCursor(requested, 0) else ic.getTextBeforeCursor(requested, 0)
            }.getOrNull() ?: return null
            val index = if (toEnd) surrounding.indexOfFirst(::isParagraphSeparator)
                else surrounding.indexOfLast(::isParagraphSeparator)
            if (index >= 0) return if (toEnd) endpoint + index else endpoint - surrounding.length + index + 1
            if (surrounding.length < requested) return if (toEnd) endpoint + surrounding.length
                else (endpoint - surrounding.length).coerceAtLeast(0)
            requested *= 2
        }
        return null
    }

    /** 只修改当前编辑器选区；不能发送 DPAD_UP/DOWN，宿主可能将它解释为焦点导航。 */
    fun moveVertical(ic: InputConnection, rows: Int, selecting: Boolean = false): Boolean {
        if (rows == 0) return false
        bind(ic)
        if (!selecting) resetSelection()
        var moved = false
        repeat(abs(rows).coerceAtMost(1000)) {
            val extracted = snapshot(ic) ?: return moved
            if (selecting) syncSelection(extracted)
            val position = if (selecting) active!! - extracted.startOffset else extracted.selectionEnd
            val target = verticalTextTarget(extracted.text, position, rows.compareTo(0))
            if (target == position) return moved
            if (!setSelection(ic, extracted.startOffset + target, selecting)) return moved
            moved = true
        }
        return moved
    }
    fun sendDirection(ic: InputConnection, key: Int, selecting: Boolean = false, additionalMeta: Int = 0): Boolean {
        bind(ic)
        if (!selecting) resetSelection()
        else snapshot(ic)?.let {
            syncSelection(it)
            // 恢复方向后再交给宿主处理上下行，避免规范化选区改变 Shift 的活动端。
            runCatching { ic.setSelection(anchor!!, active!!) }
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            return moveVertical(ic, if (key == KeyEvent.KEYCODE_DPAD_UP) -1 else 1, selecting)
        }
        val meta = additionalMeta or if (selecting) KeyEvent.META_SHIFT_ON else 0
        val downAccepted = ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0, meta))
        val upAccepted = ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, key, 0, meta))
        return downAccepted || upAccepted
    }
}

private fun isParagraphSeparator(char: Char): Boolean =
    char == '\n' || char == '\r' || char == '\u2028' || char == '\u2029'

/** 段落按实际换行分隔，CRLF 是一个边界；视觉软换行不产生新段落。 */
internal fun paragraphBoundary(text: CharSequence, position: Int, toEnd: Boolean): Int {
    var cursor = position.coerceIn(0, text.length)
    if (cursor > 0 && cursor < text.length && text[cursor - 1] == '\r' && text[cursor] == '\n') cursor--
    if (toEnd) {
        while (cursor < text.length && !isParagraphSeparator(text[cursor])) cursor++
    } else {
        while (cursor > 0 && !isParagraphSeparator(text[cursor - 1])) cursor--
    }
    return cursor
}

/** UTF-16 下每步越过完整码点，避免把光标放进 emoji 的代理对中间。 */
internal fun offsetByCharacters(text: CharSequence, position: Int, steps: Int): Int {
    var cursor = position.coerceIn(0, text.length)
    repeat(abs(steps)) {
        if (steps < 0 && cursor > 0) {
            cursor -= Character.charCount(Character.codePointBefore(text, cursor))
        } else if (steps > 0 && cursor < text.length) {
            cursor += Character.charCount(Character.codePointAt(text, cursor))
        }
    }
    return cursor
}

/** 保持码点列号，跨显式换行；未暴露视觉折行时停在当前段首/尾。 */
internal fun verticalTextTarget(text: CharSequence, position: Int, direction: Int): Int {
    val from = position.coerceIn(0, text.length)
    val start = paragraphBoundary(text, from, false)
    val end = paragraphBoundary(text, from, true)
    val column = Character.codePointCount(text, start, from)
    if (direction < 0) {
        if (start == 0) return 0
        var previousEnd = start - 1
        if (previousEnd > 0 && text[previousEnd] == '\n' && text[previousEnd - 1] == '\r') previousEnd--
        val previousStart = paragraphBoundary(text, previousEnd, false)
        return offsetByCharacters(text.subSequence(previousStart, previousEnd), 0, column) + previousStart
    }
    if (end == text.length) return text.length
    var nextStart = end + 1
    if (text[end] == '\r' && nextStart < text.length && text[nextStart] == '\n') nextStart++
    val nextEnd = paragraphBoundary(text, nextStart, true)
    return offsetByCharacters(text.subSequence(nextStart, nextEnd), 0, column) + nextStart
}
