package com.kingzcheung.xime.ui

import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.keyboard.candidatePrefixCount
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Same font engine and inputs; isolates eager vs prefix measurement, not end-to-end typing. */
class CandidateMeasurementBenchmarkTest {
    @get:Rule val rule = createComposeRule()
    @Test fun compareEagerAndVisiblePrefixMeasurement() {
        lateinit var measurer: TextMeasurer
        rule.setContent { MaterialTheme { measurer = rememberTextMeasurer(cacheSize = 0) } }
        val records = JSONArray()
        rule.runOnIdle {
            for (total in listOf(20, 50)) {
                val words = List(total) { listOf("输入", "你好", "测试", "拼音", "PS", "候选词", "布局", "文字")[it % 8] }
                repeat(24) { iteration ->
                    val results = mutableListOf<Int>()
                    for (bounded in if (iteration % 2 == 0) listOf(false, true) else listOf(true, false)) {
                        var measurements = 0
                        val start = SystemClock.elapsedRealtimeNanos()
                        fun width(index: Int): Int {
                            measurements++
                            return measurer.measure(AnnotatedString(words[index]), TextStyle(fontSize = 19.sp), softWrap = false).size.width + 20
                        }
                        val visible = if (bounded) candidatePrefixCount(total, 620, 10, ::width)
                            else candidatePrefixCount(words.indices.map(::width), 620, 10)
                        val ms = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                        results += visible
                        if (iteration > 3) records.put(JSONObject().put("method", if (bounded) "visible-prefix" else "eager")
                            .put("total", total).put("measured", measurements).put("visible", visible).put("ms", ms))
                    }
                    assertEquals(results[0], results[1])
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "candidate-measurement.json").writeText(records.toString())
    }
}
