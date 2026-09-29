package com.kingzcheung.xime.rime

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import com.kingzcheung.xime.keyboard.RepeatInput

/** Public, deterministic stress strings. No selection/commit or user-dictionary learning. */
class T9LongInputBenchmarkTest {
    private val stressLength get() = InstrumentationRegistry.getArguments()
        .getString("stressLength", "63")!!.toInt()
    private fun repeatedToLength(code: String) = code.repeat((stressLength + code.length - 1) / code.length).take(stressLength)

    @Test fun queuedTypingAndHeldDeleteRemainResponsive(): Unit = runBlocking {
        val ins = InstrumentationRegistry.getInstrumentation()
        val context = ins.targetContext
        assertTrue(RimeConfigHelper.prepareEngine(context))
        val engine = RimeEngine.getInstance()
        val schema = engine.getCurrentSchema()
        val ascii = engine.isAsciiMode()
        val code = repeatedToLength("746928374625938472635927483629")
        val controller = T9InputController(engine)
        val samples = JSONArray()
        val main = Handler(Looper.getMainLooper())
        var beatTime = SystemClock.elapsedRealtime()
        var maxMainGap = 0L
        val beat = object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                maxMainGap = maxOf(maxMainGap, now - beatTime)
                beatTime = now
                main.postDelayed(this, 16)
            }
        }
        fun drain() {
            val done = CountDownLatch(1)
            controller.enqueueLiteralInput { done.countDown() }
            assertTrue("input queue stalled", done.await(10, TimeUnit.SECONDS))
        }
        try {
            assertTrue(engine.switchSchema("t9_pinyin"))
            engine.setOption("ascii_mode", false)
            engine.clearQueuedT9Composition()
            main.post(beat)
            code.forEachIndexed { index, digit ->
                val pressed = SystemClock.elapsedRealtimeNanos()
                ins.runOnMainSync { controller.onDigitPressed(digit.toString()) }
                controller.enqueueLiteralInput {
                    assertEquals(code.take(index + 1), engine.getInput())
                    samples.put((SystemClock.elapsedRealtimeNanos() - pressed) / 1e6)
                }
                SystemClock.sleep(35) // Faster than typical two-thumb typing, without waiting for decoding.
            }
            drain()
            assertEquals("Every queued key must be checked", code.length, samples.length())
            assertEquals(code, engine.getInput())
            val hold = RepeatInput()
            val empty = CountDownLatch(1)
            var previousLength = code.length
            val deleteStarted = SystemClock.elapsedRealtime()
            val repeatDelete = object : Runnable {
                override fun run() {
                    hold.dispatch {
                        controller.onDeleted { result ->
                            assertEquals(T9InputController.DeleteResult.DELETED, result)
                            val length = controller.inputBuffer.length
                            assertEquals(previousLength - 1, length)
                            previousLength = length
                            if (length == 0) { hold.stop(); empty.countDown() }
                        }
                    }
                    if (hold.isActive) main.postDelayed(this, 70)
                }
            }
            main.post(repeatDelete)
            try {
                assertTrue("held backspace stalled", empty.await(10, TimeUnit.SECONDS))
            } finally {
                hold.stop()
                main.removeCallbacks(repeatDelete)
            }
            drain()
            assertEquals("", engine.getInput())
            val report = JSONObject().put("keyToEngineCompleteMs", samples)
                .put("heldDeleteTotalMs", SystemClock.elapsedRealtime() - deleteStarted)
                .put("maxMainHeartbeatGapMs", maxMainGap)
                .put("scope", "production controller FIFO and Main heartbeat; not actual display frames")
            File(context.getExternalFilesDir(null), "t9-long-queue.json").writeText(report.toString())
        } finally {
            main.removeCallbacks(beat)
            drain(); controller.close()
            engine.clearQueuedT9Composition()
            engine.switchSchema(schema); engine.setOption("ascii_mode", ascii)
        }
    }

    @Test fun longAmbiguousInputAndBackspace(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(RimeConfigHelper.prepareEngine(context))
        val engine = RimeEngine.getInstance()
        val schema = engine.getCurrentSchema()
        val ascii = engine.isAsciiMode()
        val samples = JSONArray()
        val candidates = JSONArray()
        val prefixCandidates = mutableMapOf<Pair<String, Int>, List<String>>()
        val label = InstrumentationRegistry.getArguments().getString("sampleLabel", "sample")!!
            .replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val output = File(context.getExternalFilesDir(null), "t9-long-$label.json")
        fun record(case: String, action: String, length: Int, block: () -> Unit) {
            val start = SystemClock.elapsedRealtimeNanos()
            block()
            val ms = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
            samples.put(JSONObject().put("case", case).put("action", action)
                .put("length", length).put("ms", ms))
            Log.i("T9LongBenchmark", "$case $action $length: $ms ms")
            output.writeText(samples.toString())
            val visible = engine.getAllCandidates(20).map { "${it.text}|${it.comment}" }
            if (action == "key") prefixCandidates[case to length] = visible
            else if (length > 0) assertEquals("$case prefix $length must not depend on typing vs backspace",
                prefixCandidates[case to length], visible)
            candidates.put(JSONObject().put("case", case).put("action", action).put("length", length)
                .put("candidates", JSONArray(visible)))
            File(context.getExternalFilesDir(null), "t9-long-$label-candidates.json").writeText(candidates.toString())
        }
        try {
            assertTrue(engine.switchSchema("t9_pinyin"))
            engine.setOption("ascii_mode", false)
            for ((name, code) in listOf(
                "repeated7" to "7".repeat(stressLength),
                "random" to repeatedToLength("746928374625938472635927483629"),
                "sentence" to repeatedToLength("9642633464942649869464748723294"),
                "separators" to repeatedToLength("964'263'346'494'264'986'"),
            )) {
                engine.clearQueuedT9Composition()
                code.forEachIndexed { index, digit ->
                    record(name, "key", index + 1) {
                        assertNotNull(engine.processQueuedT9KeyAndGetResult(digit.code))
                    }
                    assertEquals(code.take(index + 1), engine.getInput())
                }
                for (length in code.length - 1 downTo 0) {
                    record(name, "delete", length) {
                        assertNotNull(engine.processQueuedT9KeyAndGetResult(0xff08))
                    }
                    assertEquals(code.take(length), engine.getInput())
                }
            }
        } finally {
            engine.clearQueuedT9Composition()
            engine.switchSchema(schema)
            engine.setOption("ascii_mode", ascii)
        }
    }
}
