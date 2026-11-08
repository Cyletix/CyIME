package com.kingzcheung.xime.service

import android.icu.text.BreakIterator
import android.text.Spanned
import android.text.SpannedString
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/**
 * Only this touch owns the removed text. Never use the clipboard or the global undo stack.
 * [forward] = right swipe: removes after the caret like the Delete key; the caret stays put.
 */
internal class DistanceBackspace private constructor(
    private val ic: InputConnection, private val original: Window, val forward: Boolean,
) {
    private data class Window(val caret: Int, val before: CharSequence, val after: String,
        val styledAfter: CharSequence, val afterTruncated: Boolean)
    private val stops = buildList {
        val breaks = BreakIterator.getCharacterInstance()
        if (forward) {
            breaks.setText(original.after)
            add(breaks.first())
            var edge = breaks.next()
            while (edge != BreakIterator.DONE) {
                // If the right edge is truncated it might be in the middle of a grapheme.
                if (edge == original.after.length && original.afterTruncated) break
                add(edge)
                edge = breaks.next()
            }
            return@buildList
        }
        breaks.setText(original.before.toString())
        add(breaks.last())
        var edge = breaks.previous()
        while (edge != BreakIterator.DONE) {
            // If the left edge is truncated it might be in the middle of a grapheme.
            if (edge == 0 && original.caret > original.before.length) break
            add(edge)
            edge = breaks.previous()
        }
    }
    private var cut = if (forward) 0 else original.before.length
    val remainingContext get() = if (forward) original.before.toString() else original.before.subSequence(0, cut).toString()
    val removedUnits get() = if (forward) 0 else original.before.length - cut
    /** 这次触摸现在还删着字（没滑回原处）：松手后可以用撤回手势放回。 */
    val hasRemoved get() = cut != (if (forward) 0 else original.before.length)

    /** False permanently retires this gesture if the editor moved, changed or refused an edit. */
    fun move(count: Int): Boolean {
        val next = stops[count.coerceIn(stops.indices)]
        if (next == cut) return true
        val now = read(ic, forward) ?: return false
        if (forward) {
            val remaining = original.after.substring(cut)
            if (now.caret != original.caret || now.before.toString() != original.before.toString() ||
                !(if (original.afterTruncated) now.after.startsWith(remaining) else now.after == remaining)) return false
            // newCursorPosition 0 = caret before the restored text, so it never moves.
            val ok = if (next > cut) ic.deleteSurroundingText(0, next - cut)
                else ic.commitText(original.styledAfter.subSequence(next, cut), 0)
            if (ok) cut = next
            return ok
        }
        if (now.caret != original.caret - removedUnits || now.after != original.after ||
            !now.before.toString().endsWith(remainingContext)) return false
        val ok = if (next < cut) ic.deleteSurroundingText(cut - next, 0)
            else ic.commitText(original.before.subSequence(cut, next), 1)
        if (ok) cut = next
        return ok
    }

    companion object {
        private const val BEFORE = 512
        private const val AFTER = 24
        /** Forward deletion needs as much text after the caret as backward reads before it. */
        private const val FORWARD_AFTER = 512
        fun open(ic: InputConnection, forward: Boolean = false): DistanceBackspace? =
            read(ic, forward)?.let { DistanceBackspace(ic, it, forward) }

        private fun read(ic: InputConnection, forward: Boolean): Window? {
            val afterLimit = if (forward) FORWARD_AFTER else AFTER
            fun window(text: CharSequence?, offset: Int, start: Int, end: Int): Window? {
                if (text == null || offset < 0 || start !in 0..text.length || end != start) return null
                if (text is Spanned && text.getSpans(0, text.length, Any::class.java)
                        .any { text.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0 }) return null
                val before = text.subSequence((start - BEFORE).coerceAtLeast(0), start)
                val afterEnd = (start + afterLimit).coerceAtMost(text.length)
                val after = text.subSequence(start, afterEnd)
                return Window(offset + start, if (before is Spanned) SpannedString(before) else before.toString(),
                    after.toString(), if (after is Spanned) SpannedString(after) else after.toString(),
                    // A full window may stop short of the real end; its last grapheme may be cut.
                    afterEnd - start >= afterLimit)
            }
            ic.readSurroundingWindow(BEFORE, afterLimit, InputConnection.GET_TEXT_WITH_STYLES)?.let {
                if (it.startOffset >= 0) return window(it.text, it.startOffset, it.selectionStart, it.selectionEnd)
            }
            val extracted = ic.getExtractedText(ExtractedTextRequest().apply {
                hintMaxChars = BEFORE + afterLimit
                flags = InputConnection.GET_TEXT_WITH_STYLES
            }, 0) ?: return null
            return window(extracted.text, extracted.startOffset, extracted.selectionStart, extracted.selectionEnd)
        }
    }
}
