package com.kingzcheung.xime.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.EditText
import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * EditText for editors shown in a Compose Popup above the keyboard.
 *
 * Inside the IME such a popup is a sub-window, and Android's Editor disables selection handles
 * and the floating text menu there (their windows would be illegal sub-sub-windows; the action
 * mode request also stops at the popup root). This view restores the gesture itself: long-press
 * selects a word, dragging extends it, and the drawn ends can be dragged afterwards. The
 * composable hosting it shows the copy/cut/paste/select-all actions.
 */
internal class PopupSelectableEditText(context: Context) : EditText(context) {
    /** True while the hosting editor should offer selection actions. */
    var onSelectionActionsChanged: ((Boolean) -> Unit)? = null
    var handleColor: Int = 0
        set(value) { field = value; invalidate() }

    // Fields below may be read by TextView callbacks during super construction; keep them
    // nullable or primitive and initialise lazily where needed.
    private var paint: Paint? = null
    private var dragMode = DRAG_NONE
    private var anchor = 0
    private var wordStart = 0
    private var wordEnd = 0
    private var downX = 0f
    private var downY = 0f
    private var pasteOffered = false
    private var actionsShown = false
    private val longPress = Runnable { startSelection() }

    init {
        // The framework long-click path has no selection controller here; ours replaces it.
        isLongClickable = false
    }

    fun selectAllText() {
        requestFocus()
        selectAll()
    }

    fun copySelection() {
        if (onTextContextMenuItem(android.R.id.copy)) setSelection(max(selectionStart, selectionEnd))
    }

    fun cutSelection() { onTextContextMenuItem(android.R.id.cut) }

    fun pasteClipboard() {
        onTextContextMenuItem(android.R.id.paste)
        pasteOffered = false
        notifyActions()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pasteOffered = false
                notifyActions()
                dragMode = endAt(event.x, event.y)
                if (dragMode != DRAG_NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                downX = event.x
                downY = event.y
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragMode != DRAG_NONE) {
                    dragTo(event.x, event.y)
                    return true
                }
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) removeCallbacks(longPress)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (dragMode != DRAG_NONE) {
                    dragMode = DRAG_NONE
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startSelection() {
        if (layout == null) return
        // The press now belongs to selection; a normal tap-up would move the caret over it.
        MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, downX, downY, 0).also {
            super.onTouchEvent(it)
            it.recycle()
        }
        requestFocus()
        callOnClick()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        parent?.requestDisallowInterceptTouchEvent(true)
        val offset = getOffsetForPosition(downX, downY)
        val word = wordAt(offset)
        if (word == null) {
            setSelection(offset.coerceIn(0, length()))
            pasteOffered = true
            notifyActions()
            dragMode = DRAG_HOLD
            return
        }
        wordStart = word.first
        wordEnd = word.last
        setSelection(wordStart, wordEnd)
        dragMode = DRAG_WORD
    }

    private fun wordAt(offset: Int): IntRange? {
        val value = text?.toString().orEmpty()
        if (value.isEmpty()) return null
        var probe = offset.coerceIn(0, value.length - 1)
        if (value[probe].isWhitespace() && probe > 0 && !value[probe - 1].isWhitespace()) probe--
        if (value[probe].isWhitespace()) return null
        val words = BreakIterator.getWordInstance(textLocale).apply { setText(value) }
        val start = if (words.isBoundary(probe)) probe else words.preceding(probe)
        val end = words.following(probe).takeIf { it != BreakIterator.DONE } ?: value.length
        return if (start in 0 until end) start..end else null
    }

    private fun dragTo(x: Float, y: Float) {
        val offset = getOffsetForPosition(x, y)
        when (dragMode) {
            DRAG_WORD -> if (offset >= wordStart) setSelection(wordStart, max(offset, wordEnd))
                else setSelection(offset, wordEnd)
            DRAG_HOLD -> Unit
            else -> if (offset != anchor) setSelection(min(anchor, offset), max(anchor, offset))
        }
    }

    /** Hit-tests the drawn selection ends; the other end becomes the drag anchor. */
    private fun endAt(x: Float, y: Float): Int {
        val layout = layout ?: return DRAG_NONE
        if (!isFocused || !hasSelection()) return DRAG_NONE
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        val reach = 24 * resources.displayMetrics.density
        var best = DRAG_NONE
        var bestDistance = Float.MAX_VALUE
        for ((mode, offset) in listOf(DRAG_START to start, DRAG_END to end)) {
            val (hx, top, bottom) = endGeometry(layout, offset, mode == DRAG_END)
            val vx = hx - scrollX
            if (y < top - scrollY - reach || y > bottom - scrollY + reach) continue
            val distance = abs(x - vx)
            if (distance <= reach && distance < bestDistance) { best = mode; bestDistance = distance }
        }
        if (best != DRAG_NONE) anchor = if (best == DRAG_START) end else start
        return best
    }

    /** x, line top and line bottom of a selection end, in content coordinates. */
    private fun endGeometry(layout: Layout, offset: Int, isEnd: Boolean): Triple<Float, Float, Float> {
        var line = layout.getLineForOffset(offset)
        // A selection ending at a wrap point belongs to the line it ends, not the next one.
        val x = if (isEnd && line > 0 && offset == layout.getLineStart(line)) {
            line--
            layout.getLineRight(line)
        } else layout.getPrimaryHorizontal(offset)
        return Triple(x + totalPaddingLeft, layout.getLineTop(line) + totalPaddingTop.toFloat(),
            layout.getLineBottom(line) + totalPaddingTop.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = layout ?: return
        if (!isFocused || !hasSelection()) return
        val density = resources.displayMetrics.density
        val p = paint ?: Paint(Paint.ANTI_ALIAS_FLAG).also { paint = it }
        p.color = if (handleColor != 0) handleColor else currentTextColor
        val half = density
        val radius = 4.5f * density
        for ((offset, isEnd) in listOf(min(selectionStart, selectionEnd) to false,
            max(selectionStart, selectionEnd) to true)) {
            val (x, top, bottom) = endGeometry(layout, offset, isEnd)
            canvas.drawRect(x - half, top, x + half, bottom, p)
            canvas.drawCircle(x, if (isEnd) bottom else top, radius, p)
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (selStart != selEnd) pasteOffered = false
        notifyActions()
        invalidate()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!focused) pasteOffered = false
        notifyActions()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }

    private fun notifyActions() {
        val shown = isFocused && (pasteOffered || selectionStart != selectionEnd)
        if (shown == actionsShown) return
        actionsShown = shown
        onSelectionActionsChanged?.invoke(shown)
    }

    private companion object {
        const val DRAG_NONE = 0
        const val DRAG_WORD = 1
        const val DRAG_START = 2
        const val DRAG_END = 3
        /** Long-press on blank space: only offers paste, but still owns the rest of the press. */
        const val DRAG_HOLD = 4
    }
}
