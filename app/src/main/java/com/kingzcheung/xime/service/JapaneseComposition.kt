package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.RimeCandidate
import kotlin.concurrent.withLock

/** 使用原方案的实际回显确定假名边界，不复制罗马音规则，大小写保持原样。 */
internal fun RimeEngine.japaneseReading(input: String): String {
    setInput(input)
    return getComposition().preedit.filterNot { it.isWhitespace() || it == '\'' }
}

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

internal class JapaneseConversion(private val engine: RimeEngine, val input: String) {
    private val boundaries = RimeEngine.rimeLock.withLock { buildList {
        add(0)
        var end = input.length
        val reversed = mutableListOf(end)
        while (end > 0) { end = engine.previousJapaneseBoundary(input.take(end)); if (end > 0) reversed += end }
        addAll(reversed.reversed())
        engine.setInput(input)
    } }
    private var rangeIndex = boundaries.lastIndex
    var candidateIndex = 0
        private set
    var candidates: Array<RimeCandidate> = emptyArray()
        private set
    var preview = ""
        private set

    fun cycle() { candidateIndex++; refresh() }
    fun moveRange(steps: Int) {
        rangeIndex = (rangeIndex + steps).coerceIn(1, boundaries.lastIndex)
        candidateIndex = 0
        refresh()
    }
    fun choose(index: Int) { candidateIndex = index.coerceAtLeast(0); refresh() }
    fun refresh() {
        val end = boundaries[rangeIndex]
        RimeEngine.rimeLock.lock()
        try {
            val suffix = engine.japaneseReading(input.drop(end))
            engine.setInput(input.take(end))
            candidates = engine.getAllCandidates(200)
            candidateIndex = if (candidates.isEmpty()) 0 else candidateIndex.mod(candidates.size)
            if (candidates.isNotEmpty()) engine.highlightCandidate(candidateIndex)
            preview = engine.conversionPreview().ifEmpty { engine.getComposition().preedit }.replace(" ", "") + suffix
        } finally { engine.setInput(input); RimeEngine.rimeLock.unlock() }
    }
}
