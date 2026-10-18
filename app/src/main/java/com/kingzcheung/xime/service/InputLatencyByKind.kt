package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.InputCommandSample

/**
 * 按动作类别（九键数字、删除、选词、停顿重排等）分别统计排队、执行与占用引擎的时长，
 * 每类满 [size] 次给出一次分位数。执行时长减去引擎时长即主线程往返等其他开销。不记按键与文字。
 */
internal class InputLatencyByKind(private val size: Int = 32) {
    data class Summary(
        val kind: String, val samples: Int,
        val totalP50: Long, val totalP95: Long, val totalMax: Long, val waitP95: Long,
        val executionP50: Long, val executionP95: Long, val engineP50: Long, val engineP95: Long, val engineMax: Long,
    )

    private class Window(size: Int) {
        val waits = LongArray(size)
        val executions = LongArray(size)
        val engines = LongArray(size)
        var count = 0
    }

    private val windows = HashMap<String, Window>()

    @Synchronized fun record(sample: InputCommandSample): Summary? {
        val window = windows.getOrPut(sample.kind) { Window(size) }
        window.waits[window.count] = sample.waitMillis.coerceAtLeast(0)
        window.executions[window.count] = sample.executionMillis.coerceAtLeast(0)
        window.engines[window.count] = sample.engineMillis.coerceAtLeast(0)
        window.count++
        if (window.count < size) return null
        val n = window.count
        val totals = LongArray(n) { window.waits[it] + window.executions[it] }.apply { sort() }
        val waits = window.waits.copyOf(n).apply { sort() }
        val executions = window.executions.copyOf(n).apply { sort() }
        val engines = window.engines.copyOf(n).apply { sort() }
        val p50 = (n - 1) / 2
        val p95 = (n * 95 + 99) / 100 - 1
        window.count = 0
        return Summary(sample.kind, n, totals[p50], totals[p95], totals.last(), waits[p95],
            executions[p50], executions[p95], engines[p50], engines[p95], engines.last())
    }
}
