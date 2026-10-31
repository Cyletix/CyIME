package com.kingzcheung.xime.glide

import com.kingzcheung.xime.keyboard.LetterKeyRect
import kotlin.math.abs

/**
 * 判断一次触摸是不是滑行。只有确认是滑行后才接管手势，此前点按、上下滑符号、长按照常。
 *
 * - 必须从字母键起手；按住超过 [HOLD_MS] 才移动的留给长按，不再接管。
 * - 竖着滑一行（上下滑符号）不接管：要么经过至少三个字母键且横向跨过 [MIN_SPAN_KEYS] 个键宽
 *   或纵向跨过两行，要么两个字母键之间以横向为主、跨过 [MIN_SPAN_KEYS] 个键宽（as、we 这类相邻词）。
 */
class GlideTracker(
    private val keys: Map<Char, LetterKeyRect>,
    private val touchSlop: Float,
    x: Float, y: Float, private val downTime: Long,
) {
    private val keyWidth = keys.values.map { it.width }.sorted().let { it.getOrNull(it.size / 2) } ?: 1f
    private val keyHeight = keys.values.map { it.height }.sorted().let { it.getOrNull(it.size / 2) } ?: 1f
    private val startX = x
    private val startY = y
    private var minX = x; private var maxX = x; private var minY = y; private var maxY = y
    private var moved = false
    private val visited = LinkedHashSet<Char>()
    private val pointsX = ArrayList<Float>()
    private val pointsY = ArrayList<Float>()

    val startsOnLetter: Boolean = keyAt(x, y) != null
    var claimed = false
        private set
    /** 已判定不是滑行（按住后才移动、离开键盘区等），本次触摸不再接管。 */
    var abandoned = !startsOnLetter
        private set

    init { add(x, y) }

    fun xs(): FloatArray = pointsX.toFloatArray()
    fun ys(): FloatArray = pointsY.toFloatArray()

    /** 加入一个轨迹点；返回本次是否刚刚确认为滑行。 */
    fun move(x: Float, y: Float, time: Long): Boolean {
        if (abandoned) return false
        if (!moved && (abs(x - startX) > touchSlop || abs(y - startY) > touchSlop)) {
            moved = true
            if (time - downTime > HOLD_MS && !claimed) { abandoned = true; return false }
        }
        add(x, y)
        if (claimed) return false
        val spanX = (maxX - minX) / keyWidth
        val spanY = (maxY - minY) / keyHeight
        val horizontal = abs(x - startX) > abs(y - startY)
        claimed = (visited.size >= 3 && (spanX >= MIN_SPAN_KEYS || spanY >= MIN_ROWS)) ||
            (visited.size >= 2 && horizontal && spanX >= MIN_SPAN_KEYS)
        return claimed
    }

    fun abandon() { if (!claimed) abandoned = true }

    private fun add(x: Float, y: Float) {
        pointsX += x; pointsY += y
        minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
        keyAt(x, y)?.let { visited += it }
    }

    private fun keyAt(x: Float, y: Float): Char? = keys.entries.firstOrNull { (_, rect) ->
        x >= rect.x && x < rect.x + rect.width && y >= rect.y && y < rect.y + rect.height
    }?.key

    companion object {
        const val HOLD_MS = 300L
        const val MIN_SPAN_KEYS = 0.9f
        const val MIN_ROWS = 1.9f
    }
}

/**
 * 滑行整词上屏的文字：前面紧挨字母、数字或句末标点时补一个空格，
 * 开头、空白后、左括号或左引号后不补。
 */
fun glideCommitText(word: String, before: Char?): String =
    if (before == null || before.isWhitespace() || before in "([{<\"'“‘（【《「『") word else " $word"

/** 按 Shift 状态决定大小写：单次大写只改首字母，锁定时全部大写。 */
fun glideCased(word: String, shifted: Boolean, capsLocked: Boolean): String = when {
    capsLocked -> word.uppercase()
    shifted -> word.replaceFirstChar { it.uppercaseChar() }
    else -> word
}

/**
 * 中文滑行适用的键盘：全拼、双拼的字母键盘（26 键、分体、自定义字母排列）。
 * 14 键、九键、注音、日文、五笔不滑行；双拼只认得出键位表的方案。
 */
fun chineseGlideSupported(profile: com.kingzcheung.xime.settings.InputProfile, schemaId: String): Boolean =
    profile.language == com.kingzcheung.xime.settings.InputLanguage.CHINESE &&
        profile.layout.kind == com.kingzcheung.xime.settings.LayoutKind.ALPHABETIC &&
        (profile.scheme == com.kingzcheung.xime.settings.InputScheme.PINYIN ||
            profile.scheme == com.kingzcheung.xime.settings.InputScheme.DOUBLE_PINYIN &&
            com.kingzcheung.xime.settings.DoublePinyinSchemes.find(schemaId) != null)
