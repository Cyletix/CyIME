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

/**
 * 末段是否为完整音节（纯字符串快路径）：以元音或长音符结尾的罗马音一定已拼完，
 * 无需再问引擎；其余（声母、n、x 等）交给引擎判定。返回 true 表示不必探测。
 */
internal fun japaneseSyllableClosedByVowel(input: String): Boolean {
    val last = input.lastOrNull() ?: return true
    return !last.isLetter() || last.lowercaseChar() in "aiueo"
}

/**
 * 日语罗马音显示文本：已拼成假名的部分沿用原方案回显，末尾尚未拼完的罗马音
 * 显示为按下的字母。
 *
 * 原方案的 preedit_format 为促音/拨音准备了单字母规则（k→っ、n→ん 等），
 * 只按下声母时屏幕会显示成 っ / ん，看不到自己按下的键；这里把未拼完的尾部
 * 换回原始字母。是否拼完由引擎判定（能译成假名即算拼完），不复制罗马音规则。
 *
 * 返回 null 表示无需改写：输入为空、以元音结尾，或末段已是完整假名。
 */
internal fun RimeEngine.japaneseDisplayText(input: String): String? {
    if (input.isEmpty() || japaneseSyllableClosedByVowel(input)) return null
    RimeEngine.rimeLock.lock()
    try {
        val boundary = previousJapaneseBoundary(input)
        val tail = input.drop(boundary)
        if (tail.isEmpty() || isTranslatableSyllable(tail)) return null
        return japaneseReading(input.take(boundary)) + tail
    } finally {
        setInput(input)
        RimeEngine.rimeLock.unlock()
    }
}

/** 末段是否已能译成假名：只回显原始字母说明还没拼完。锁内调用。 */
private fun RimeEngine.isTranslatableSyllable(text: String): Boolean {
    setInput(text)
    return getCandidates().any { it != text }
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
