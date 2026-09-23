package com.kingzcheung.xime.handwriting

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Small synthetic stroke probes, not an accuracy benchmark or a substitute for users' handwriting. */
class HandwritingCandidateAuditTest {
    @Test fun recordWholeCharacterAndSegmentedCandidates() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Install the market ochwpro model on the test device", HandwritingEngine.initialize(context))
        fun stroke(vararg xy: Int): List<Pair<Float, Float>> = xy.toList().chunked(2).map { it[0].toFloat() to it[1].toFloat() }
        val probes = linkedMapOf(
            "一" to listOf(stroke(20, 80, 150, 80)),
            "中" to listOf(stroke(35, 40, 35, 120), stroke(35, 40, 130, 40, 130, 120), stroke(35, 120, 130, 120), stroke(80, 15, 80, 160)),
            "大" to listOf(stroke(20, 60, 150, 60), stroke(90, 15, 85, 65, 65, 110, 20, 150), stroke(85, 65, 110, 110, 155, 150)),
            "木" to listOf(stroke(20, 60, 150, 60), stroke(85, 15, 85, 160), stroke(85, 65, 60, 110, 20, 145), stroke(85, 65, 115, 115, 155, 145)),
        )
        val report = JSONArray()
        try {
            for ((expected, strokes) in probes) {
                val start = System.nanoTime()
                val whole = HandwritingEngine.predict(strokes, 10)
                val split = OverlappedHandwritingRecognizer().recognize(strokes, List(strokes.size) { if (it == 0) 0L else 180L })
                assertTrue("$expected returned no candidates", whole.isNotEmpty())
                assertTrue("Non-Han candidate escaped: $whole", whole.all { HandwritingCandidatePolicy.isHanCharacter(it.char) })
                assertTrue("Non-Han split escaped", split.segments.all { segment -> segment.candidates.all { HandwritingCandidatePolicy.isHanCharacter(it.char) } })
                val row = JSONObject().put("expected", expected).put("whole", JSONArray(whole.map { JSONObject().put("text", it.char).put("score", it.score) }))
                    .put("segmented", split.segments.joinToString("") { it.candidates.first().char })
                    .put("milliseconds", (System.nanoTime() - start) / 1e6)
                report.put(row)
            }
            File(context.getExternalFilesDir(null), "handwriting-candidate-audit.json").writeText(report.toString(2))
        } finally { HandwritingEngine.release() }
    }
}
