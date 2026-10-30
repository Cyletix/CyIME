package com.kingzcheung.xime.handwriting

import kotlin.math.max
import kotlin.math.min

/**
 * 英文手写的词间空格（整词模型只出字母和标点，不出空格）。
 *
 * 一段笔迹（一次停顿认一次）里，笔画按左右范围排开，空隙不小于词距就分成两个词，分别认再用空格连。
 * 停顿后接着写的一段，写在前面笔迹右边隔开一个词距、或整段写回前面笔迹左边（换一行接着写）时算新词，前面补空格；
 * 隔得近的是同一个词写到一半停了一下（an|tique），直接接上。
 *
 * 词距 = 行高 × [WORD_GAP_RATIO]。行高取这一行已写笔迹的高度，和单笔高度 90% 分位的 [STROKE_HEIGHT_SCALE] 倍中较大的：
 * 刚开始只写了几个矮字母时整行高度偏小，字母间的空隙会被当成词距。
 * 电脑回放（公开数据 ImadocSen 测试者的整行句子、你写的 3 句）见 docs/development/handwriting-weak-spots-20261030.md。
 */
internal class EnglishWordSpacing {
    internal data class Box(val left: Float, val right: Float, val top: Float, val bottom: Float) {
        val height get() = bottom - top
        fun union(o: Box) = Box(min(left, o.left), max(right, o.right), min(top, o.top), max(bottom, o.bottom))

        companion object {
            fun of(strokes: List<List<Pair<Float, Float>>>): Box? {
                val pts = strokes.flatten()
                if (pts.isEmpty()) return null
                return Box(pts.minOf { it.first }, pts.maxOf { it.first }, pts.minOf { it.second }, pts.maxOf { it.second })
            }
        }
    }

    /** 一段笔迹的结果：[startsWord] 前面要不要补空格；[words] 分成的词，每个词是笔画序号（书写顺序），词从左到右。 */
    internal data class Chunk(val startsWord: Boolean, val words: List<List<Int>>)

    private var previous: Box? = null
    private var lineHeight = 0f
    private val strokeHeights = mutableListOf<Float>()

    /** 打了空格、回车、标点或换了输入框：下一段笔迹从头算，不补空格。 */
    fun reset() {
        previous = null
        lineHeight = 0f
        strokeHeights.clear()
    }

    fun next(strokes: List<List<Pair<Float, Float>>>): Chunk {
        val box = Box.of(strokes) ?: return Chunk(false, emptyList())
        for (s in strokes) if (s.isNotEmpty()) strokeHeights += s.maxOf { it.second } - s.minOf { it.second }
        val sorted = strokeHeights.sorted()
        val tall = sorted[((sorted.size - 1) * STROKE_HEIGHT_QUANTILE).toInt()]
        lineHeight = maxOf(lineHeight, box.height, tall * STROKE_HEIGHT_SCALE)
        val gap = lineHeight * WORD_GAP_RATIO
        val prev = previous
        val startsWord = prev != null && (box.left - prev.right >= gap || box.right <= prev.left)
        previous = if (prev == null || startsWord) box else prev.union(box)
        return Chunk(startsWord, split(strokes, gap))
    }

    companion object {
        /** 词距占行高的比例。公开数据 2300 行：0.22 时 95% 的词间空隙找得出、1.6% 的词被拆开。 */
        const val WORD_GAP_RATIO = 0.22f

        /**
         * 行高至少是单笔高度（取 90% 分位，去掉 i 的点、t 的横这类小笔画）的这么多倍。
         * 回放：不加这条你的 3 句词对 45%；1.6 倍 68%，公开数据 119 行不受影响（词对 79%）；2 倍你的 77% 但公开数据降到 76%。
         */
        const val STROKE_HEIGHT_SCALE = 1.6f
        private const val STROKE_HEIGHT_QUANTILE = 0.9

        /** 笔画按左右范围排开，空隙不小于 [gap] 处分词。 */
        internal fun split(strokes: List<List<Pair<Float, Float>>>, gap: Float): List<List<Int>> {
            val spans = strokes.indices.filter { strokes[it].isNotEmpty() }
                .map { i -> Triple(strokes[i].minOf { it.first }, strokes[i].maxOf { it.first }, i) }.sortedBy { it.first }
            if (spans.isEmpty()) return emptyList()
            val words = mutableListOf<MutableList<Int>>(mutableListOf(spans[0].third))
            var right = spans[0].second
            for ((left, r, i) in spans.drop(1)) {
                if (left - right >= gap) words += mutableListOf<Int>()
                words.last() += i
                right = max(right, r)
            }
            return words.map { it.sorted() }
        }
    }
}
