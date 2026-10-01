package com.kingzcheung.xime.rime

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Runs the shipped native decoder, not a second implementation of its ranking. */
class InputQualityRepairTest {
    @Test fun reportedInputsSelectionAndPersistentNegativeFeedback(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(RimeConfigHelper.prepareEngine(context))
        val engine = RimeEngine.getInstance()
        val (user, shared) = RimeConfigHelper.initializeRimeDataAsync(context)
        val previous = engine.getCurrentSchema()
        engine.destroy()
        val fixture = File(context.filesDir, "input-quality-repair-fixture").apply { mkdirs() }
        // Recreate only this test's private directory; never touch real user data.
        fixture.listFiles()?.forEach { it.deleteRecursively() }
        File(user).listFiles()?.filter { it.isFile && it.extension in listOf("yaml", "json") }
            ?.forEach { it.copyTo(File(fixture, it.name)) }
        File(user, "build").walkTopDown().filter { it.isFile }.forEach { source ->
            File(fixture, "build/" + source.relativeTo(File(user, "build")).path).also {
                it.parentFile!!.mkdirs(); source.copyTo(it)
            }
        }
        // A shared directory containing the app's userdb defeats isolation: Rime
        // resolves a missing fixture DB through shared-data fallback. Both roots
        // must therefore point to the fixture, which contains compiled assets only.
        assertFalse(fixture.walkTopDown().any { it.name.endsWith(".userdb") })
        // Lua filters and OpenCC data are runtime dependencies, not learned data.
        for (directory in listOf("lua", "opencc")) {
            File(user, directory).takeIf { it.isDirectory }?.copyRecursively(File(fixture, directory))
        }
        File(user).listFiles()?.filter { it.isFile && it.extension == "lua" }
            ?.forEach { it.copyTo(File(fixture, it.name)) }
        val args = InstrumentationRegistry.getArguments()
        args.getString("maxHomophones")?.toInt()?.let { count ->
            require(count in 1..16)
            val schema = File(fixture, "build/t9_pinyin.schema.yaml")
            schema.writeText(schema.readText().replace(Regex("(?m)^translator:\n"), "translator:\n  max_homophones: $count\n"))
        }
        val report = JSONObject()
        val samples = JSONArray()
        val failures = mutableListOf<String>()
        fun start(schema: String) {
            engine.initialize(fixture.path, fixture.path)
            assertTrue(engine.ensureSession()); assertTrue(engine.switchSchema(schema))
            engine.setOption("ascii_mode", false)
        }
        fun query(schema: String, input: String): Array<Array<String>> {
            assertTrue(engine.switchSchema(schema))
            engine.clearQueuedComposition()
            if (schema == "t9_pinyin") engine.clearQueuedT9Composition()
            assertEquals("", engine.getInput())
            input.forEach {
                if (schema == "t9_pinyin") engine.processQueuedT9Key(it.code)
                else engine.processQueuedKeyAndGetResult(it.code, 0)
            }
            return engine.inspectCandidates(100)
        }
        fun record(schema: String, code: String, target: String, top: Int) {
            val begin = System.nanoTime()
            val rows = query(schema, code)
            val rank = rows.indexOfFirst { it[0] == target }
            samples.put(JSONObject().put("schema", schema).put("code", code).put("target", target)
                .put("rank", if (rank < 0) -1 else rank + 1)
                .put("elapsedMs", (System.nanoTime() - begin) / 1e6)
                .put("candidates", JSONArray(rows.map { JSONArray(it.toList()) })))
            if (rank !in 0 until top) failures += "$schema $code -> $target: rank ${rank + 1}, expected top $top"
        }
        try {
            start("rime_ice")
            for ((code, target) in linkedMapOf("ddzm" to "到底怎么", "d'd'z'm" to "到底怎么",
                "daodizenme" to "到底怎么", "wzd" to "我知道", "wbzd" to "我不知道",
                "zmb" to "怎么办", "zmhs" to "怎么回事", "wsm" to "为什么", "njsm" to "你叫什么",
                "wjs" to "我就是", "ygwt" to "一个问题", "zgy" to "这个月", "jtws" to "今天晚上")) {
                record("rime_ice", code, target, 10)
            }
            for ((code, target) in linkedMapOf("3" to "的", "5833" to "觉得", "3242458" to "大概率",
                "9692674264" to "我晚上", "645831316" to "你觉得呢", "94294744" to "下意识",
                "32942426" to "打一遍", "936848" to "问题太", "743663484" to "什么鬼")) {
                record("t9_pinyin", code, target, if (code == "3") 1 else if (code in listOf("5833", "3242458", "9692674264", "32942426")) 3 else 20)
            }
            for ((code, accepted) in listOf("65" to setOf("OK", "ok"), "77" to setOf("QQ", "qq"))) {
                val rows = query("t9_pinyin", code)
                if (rows.take(20).none { it[0] in accepted }) failures += "$code: missing English $accepted"
                if (code == "77" && rows.any { it[2] == "date" }) failures += "77 must not trigger date"
            }
            if (query("t9_pinyin", "7474").none { it[2] == "date" }) failures += "7474 missing date"
            for (code in listOf("ddzm", "d'd'z'm", "wbzd", "njsm")) {
                val rows = query("rime_ice", code)
                if (rows.take(20).any { it[0].all { ch -> ch in '\u3400'..'\u9fff' } && it[0].length > 4 })
                    failures += "$code: untyped suffix leaked into candidates"
            }
            val t9 = query("t9_pinyin", "743663484")
            if (t9.take(20).any { it[0] in setOf("神媒体", "审美提") }) failures += "unsupported sentences in top20"

            // Selection must commit only encoded syllables, not its source word's suffix.
            for (code in listOf("ddzm", "d'd'z'm", "daodizenme")) {
                val rows = query("rime_ice", code)
                val index = rows.indexOfFirst { it[0] == "到底怎么" }
                if (index < 0) continue
                assertEquals("Prefix spelling must preserve paired comment formatting",
                    rows[index][1].startsWith("［"), rows[index][1].endsWith("］"))
                val revision = engine.readQueuedComposition()!!.engineRevision
                val selected = engine.selectCandidateAtRevision(index, revision, global = true)
                if (selected?.committedText != "到底怎么") failures += "$code committed ${selected?.committedText}"
                if (engine.getInput().isNotEmpty()) failures += "$code left residual input"
            }
            // Text deletion is a durable user decision, across all Chinese layouts.
            val before = query("t9_pinyin", "743663484")
            val index = before.indexOfFirst { it[0] == "什么鬼" }
            assertTrue(index >= 0)
            val revision = engine.readQueuedComposition()!!.engineRevision
            assertTrue(engine.deleteCandidateAtRevision(index, revision, global = true))
            assertFalse(query("t9_pinyin", "743663484").any { it[0] == "什么鬼" })
            engine.destroy(); start("t9_pinyin")
            assertFalse(query("t9_pinyin", "743663484").any { it[0] == "什么鬼" })
            assertFalse(query("rime_ice", "shenmegui").any { it[0] == "什么鬼" })
            report.put("persistentHide", true)
        } finally {
            report.put("samples", samples).put("failures", JSONArray(failures))
            File(context.filesDir, "input-quality-repair.json").writeText(report.toString(2))
            engine.destroy(); engine.initialize(user, shared); engine.ensureSession()
            if (previous.isNotEmpty()) engine.switchSchema(previous)
            fixture.deleteRecursively()
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
