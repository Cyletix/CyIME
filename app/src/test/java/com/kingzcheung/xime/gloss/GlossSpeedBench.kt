package com.kingzcheung.xime.gloss

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 释义每键耗时：同一份按键回放的候选（每键前 20 个），比较“只查整词”（v3 的做法）和现在“查整词 + 逐词拼”的耗时。
 * 只在设置了 CYIME_GLOSS_BENCH=<词典文件>;<候选文件> 时运行（平时跳过，不进普通单测）。不走缓存，按最坏情况量。
 */
class GlossSpeedBench {
    @Test fun perKeyCost() {
        val spec = System.getenv("CYIME_GLOSS_BENCH")
        assumeTrue(!spec.isNullOrEmpty())
        val (dict, keys) = spec!!.split(';')
        val table = GlossTable.open(File(dict))
        val lists = File(keys).readLines(Charsets.UTF_8).map { it.split('\t') }
        val empty = lists.map { list -> List(list.size) { "" } }

        fun measure(block: (Int) -> Unit): LongArray {
            repeat(3) { lists.indices.forEach(block) }   // 预热
            return LongArray(lists.size) { index ->
                val start = System.nanoTime()
                block(index)
                System.nanoTime() - start
            }.also { it.sort() }
        }
        val lookupOnly = measure { i -> lists[i].forEach { table.lookup(CandidateGlossary.keyOf(it)) } }
        val withCompose = measure { i -> CandidateGlossary.annotate(table, lists[i], empty[i]) }
        fun LongArray.p(q: Double) = this[((size - 1) * q).toInt()] / 1000.0
        println("GLOSS_BENCH keys=${lists.size} lookupOnly p50=%.1fus p95=%.1fus  withCompose p50=%.1fus p95=%.1fus"
            .format(lookupOnly.p(0.5), lookupOnly.p(0.95), withCompose.p(0.5), withCompose.p(0.95)))
    }
}
