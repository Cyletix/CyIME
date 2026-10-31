package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.RimeCandidate
import kotlin.concurrent.withLock

/** 使用原方案的实际回显确定假名边界，不复制罗马音规则，大小写保持原样。 */
internal fun RimeEngine.japaneseReading(input: String): String {
    setInput(input)
    return getComposition().preedit.filterNot { it.isWhitespace() || it == '\'' }
}

/**
 * 读音长度探测边界（仅供转换预览的切换范围使用）。
 *
 * 不要再用于退格删除单位：ん 与 っ 都是单字符，长度比较判不出假名边界，会把整串一次删光
 * （2026-09-25 复现）。删除单位已改为同源的 [com.kingzcheung.xime.rime.romajiDeleteStart]；
 * 显示边界同理，走纯字符串的 [com.kingzcheung.xime.rime.japanesePreedit]（在 UI 出口归一化），
 * 不再用引擎探测"是否拼完"——探测会把单字母的促音/拨音兜底回显当成已拼完。
 */
internal fun RimeEngine.previousJapaneseBoundary(input: String): Int {
    if (input.isEmpty()) return 0
    RimeEngine.rimeLock.lock()
    try {
        val reading = japaneseReading(input)
        for (end in input.lastIndex downTo 0) {
            val shorter = japaneseReading(input.take(end))
            if (shorter.length < reading.length && reading.startsWith(shorter)) return end
        }
        return 0
    } finally { setInput(input); RimeEngine.rimeLock.unlock() }
}

/**
 * 日语转换状态。[converted] 为 false 时只是光标挪进了读音（九键左右键，Gboard、iOS 日文翻页键盘同样）：
 * 读音原样显示，光标处画竖线，候选只给光标前那段；按変換才转成汉字。[caretKana]：光标前有几个假名。
 */
internal class JapaneseConversion(private val engine: RimeEngine, val input: String,
    converted: Boolean = true, caretKana: Int? = null) {
    private val boundaries = RimeEngine.rimeLock.withLock { buildList {
        add(0)
        var end = input.length
        val reversed = mutableListOf(end)
        while (end > 0) { end = engine.previousJapaneseBoundary(input.take(end)); if (end > 0) reversed += end }
        addAll(reversed.reversed())
        engine.setInput(input)
    } }
    private var rangeIndex = caretKana?.coerceIn(1, boundaries.lastIndex) ?: boundaries.lastIndex
    var converted = converted
        private set
    var candidateIndex = 0
        private set
    var candidates: Array<RimeCandidate> = emptyArray()
        private set
    var preview = ""
        private set
    /** 当前段（光标前或转换段）按高亮候选转出的字；没有候选时是读音。 */
    var segment = ""
        private set
    /** 光标或转换段没到末尾：后面还有读音。 */
    val partial get() = rangeIndex < boundaries.lastIndex
    /** 当前段之后的原编码，确认当前段后留着接着打。 */
    val rest get() = input.drop(boundaries[rangeIndex])

    fun cycle() { candidateIndex++; refresh() }
    fun convert() { converted = true; refresh() }
    /** 按假名移光标/调转换段；到头不动，返回 false。 */
    fun moveRange(steps: Int): Boolean {
        val next = (rangeIndex + steps).coerceIn(1, boundaries.lastIndex)
        if (next == rangeIndex) return false
        rangeIndex = next
        candidateIndex = 0
        refresh()
        return true
    }
    /** 删掉光标前那个假名后的编码，以及光标前还剩几个假名。 */
    fun withoutKanaBeforeCaret(): Pair<String, Int> =
        input.take(boundaries[rangeIndex - 1]) + input.drop(boundaries[rangeIndex]) to rangeIndex - 1
    fun choose(index: Int) { candidateIndex = index.coerceAtLeast(0); refresh() }
    fun refresh() {
        val end = boundaries[rangeIndex]
        RimeEngine.rimeLock.lock()
        try {
            val suffix = engine.japaneseReading(input.drop(end))
            val reading = if (converted) "" else engine.japaneseReading(input.take(end))
            engine.setInput(input.take(end))
            candidates = engine.getAllCandidates(200)
            candidateIndex = if (candidates.isEmpty()) 0 else candidateIndex.mod(candidates.size)
            if (candidates.isNotEmpty()) engine.highlightCandidate(candidateIndex)
            segment = engine.conversionPreview().ifEmpty { engine.getComposition().preedit }.replace(" ", "")
            preview = if (converted) segment + suffix
                else reading + (if (suffix.isEmpty()) "" else PREEDIT_CARET.toString()) + suffix
        } finally { engine.setInput(input); RimeEngine.rimeLock.unlock() }
    }
}
