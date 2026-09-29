package com.kingzcheung.xime.rime

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Fixed public strings; never commits text or trains the user dictionary.
 * Controller timings end at Main snapshot delivery, not physical display presentation.
 */
class InputPipelineBenchmarkTest {
    @Test fun sampleTypingPipeline(): Unit = runBlocking {
        val ins = InstrumentationRegistry.getInstrumentation()
        val context = ins.targetContext
        assertTrue(RimeConfigHelper.prepareEngine(context))
        val engine = RimeEngine.getInstance()
        val previous = engine.getCurrentSchema()
        val ascii = engine.isAsciiMode()
        val points = JSONArray()
        val awaiting = AtomicReference<Pair<Long, CountDownLatch>?>(null)
        var delivered = ""
        var elapsed = 0.0
        val controller = T9InputController(onCompositionRefresh = { state, _ ->
            awaiting.get()?.let { (start, done) ->
                delivered = state.input
                elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                done.countDown()
            }
        })
        fun await(done: CountDownLatch) { assertTrue("Input pipeline stalled", done.await(30, TimeUnit.SECONDS)) }
        fun drain() { val done = CountDownLatch(1); controller.enqueueLiteralInput { done.countDown() }; await(done) }
        try {
            assertTrue(engine.switchSchema("t9_pinyin"))
            engine.setOption("ascii_mode", false)
            engine.setVerboseLogging(false)
            val codes = listOf("7", "746", "64426", "93663", "74267424", "9642633464")
            repeat(6) { iteration ->
                for (code in codes) {
                    engine.clearQueuedT9Composition()
                    var prefix = ""
                    for (digit in code) {
                        prefix += digit
                        val done = CountDownLatch(1)
                        ins.runOnMainSync {
                            awaiting.set(SystemClock.elapsedRealtimeNanos() to done)
                            controller.onDigitPressed(digit.toString())
                        }
                        await(done); awaiting.set(null)
                        assertEquals(prefix, delivered)
                        if (iteration > 0) points.put(JSONObject().put("stage", "t9-controller-to-main").put("length", prefix.length).put("ms", elapsed))
                    }
                    val start = SystemClock.elapsedRealtimeNanos()
                    val all = engine.getAllCandidates(500)
                    val ms = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                    if (iteration > 0) points.put(JSONObject().put("stage", "expanded-500-native").put("length", code.length).put("count", all.size).put("ms", ms))
                }
            }
            drain()
            assertTrue(engine.switchSchema("rime_ice")); engine.setOption("ascii_mode", false)
            repeat(6) { iteration ->
                for (code in listOf("nihao", "zenme", "shurufa", "woxiangdashuzi")) {
                    engine.clearQueuedComposition()
                    for (digit in code) {
                        val start = SystemClock.elapsedRealtimeNanos()
                        val result = engine.processQueuedKeyAndGetResult(digit.code, 0)
                        val ms = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                        assertTrue(result.processed)
                        if (iteration > 0) points.put(JSONObject().put("stage", "pinyin-key-and-snapshot").put("ms", ms))
                    }
                }
            }
            val label = InstrumentationRegistry.getArguments().getString("sampleLabel", "sample")!!.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val output = JSONObject().put("label", label).put("device", android.os.Build.MODEL)
                .put("scope", "warm keys; controller-to-main and native costs; no physical frame timing")
                .put("samples", points)
            File(context.getExternalFilesDir(null), "pipeline-$label.json").writeText(output.toString())
        } finally {
            drain(); engine.clearQueuedT9Composition(); engine.clearQueuedComposition()
            engine.switchSchema(previous); engine.setOption("ascii_mode", ascii)
        }
    }
}
