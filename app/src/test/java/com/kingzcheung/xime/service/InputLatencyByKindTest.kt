package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.EngineTimeMeter
import com.kingzcheung.xime.rime.InputCommandQueue
import com.kingzcheung.xime.rime.InputCommandSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** 动作类别分开统计，满一窗才给分位数；引擎占用时间只记到正在执行的那个动作。 */
class InputLatencyByKindTest {
    @Test fun kindsAreSummarizedSeparatelyOncePerWindow() {
        val stats = InputLatencyByKind(size = 4)
        assertNull(stats.record(InputCommandSample("t9-digit", 0, 10, 8)))
        assertNull(stats.record(InputCommandSample("select", 5, 40, 2)))
        assertNull(stats.record(InputCommandSample("t9-digit", 1, 20, 18)))
        assertNull(stats.record(InputCommandSample("t9-digit", 2, 30, 25)))
        val digit = stats.record(InputCommandSample("t9-digit", 3, 100, 90))!!
        assertEquals("t9-digit", digit.kind)
        assertEquals(4, digit.samples)
        assertEquals(103L, digit.totalMax)
        assertEquals(20L, digit.executionP50)
        assertEquals(100L, digit.executionP95)
        assertEquals(90L, digit.engineMax)
        // 另一类别还没攒满。
        assertNull(stats.record(InputCommandSample("select", 0, 1, 0)))
    }

    @Test fun engineTimeIsChargedToTheRunningAction() = runBlocking {
        val samples = Collections.synchronizedList(mutableListOf<InputCommandSample>())
        val queue = InputCommandQueue(this, Dispatchers.Default, reportSample = { samples += it })
        queue.submit(kind = "t9-digit") {
            EngineTimeMeter.add(7_000_000)
            // 跨线程往返（如切到主线程交付）后回来仍记在同一动作上。
            withContext(Dispatchers.IO) { }
            EngineTimeMeter.add(5_000_000)
        }.join()
        queue.execute("select") { EngineTimeMeter.add(3_000_000) }
        // 队列外的调用不计入任何动作。
        EngineTimeMeter.add(100_000_000)
        queue.close()
        assertEquals(listOf("t9-digit", "select"), samples.map { it.kind })
        assertEquals(12L, samples[0].engineMillis)
        assertEquals(3L, samples[1].engineMillis)
        assertTrue(samples.all { it.waitMillis >= 0 && it.executionMillis >= 0 })
    }
}
