package com.kingzcheung.xime.rime

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Replays the supplied evidence in an isolated user directory, with real Rime. */
class T9ReportedRegressionTest {
    @Test fun reportedRankingsAndEditing() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = RimeEngine.getInstance()
        val (user, shared) = RimeConfigHelper.initializeRimeDataAsync(context)
        engine.initialize(user, shared)
        assertTrue(RimeConfigHelper.ensureDeployment(context))
        val previousSchema = engine.getCurrentSchema()
        engine.destroy()
        val fixture = File(context.filesDir, "ime-lab/t9-reported-${System.currentTimeMillis()}").apply { mkdirs() }
        val results = File(context.filesDir, "ime-lab/t9-reported-results.jsonl")
        results.writeText("")
        val manifest = context.assets.open("rime-bundled-manifest.tsv").bufferedReader().use { it.readText() }
        manifest.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val fields = line.split('\t')
            File(fixture, fields[3]).apply {
                parentFile?.mkdirs()
                if (fields[3].endsWith(".gram")) {
                    // Copy only immutable bytes, not a resource fallback root:
                    // a fallback root can silently supply an existing user DB.
                    // Buffered copy also works across scoped-storage mounts.
                    File(user, fields[3]).inputStream().use { i -> outputStream().use { i.copyTo(it) } }
                } else {
                    context.assets.open(fields[2]).use { i -> outputStream().use { i.copyTo(it) } }
                }
            }
        }
        for (name in listOf("default.yaml", "symbols.yaml", "punctuation.yaml")) {
            try { context.assets.open("rime/$name").use { i -> File(fixture, name).outputStream().use { i.copyTo(it) } } }
            catch (_: java.io.FileNotFoundException) { }
        }
        File(fixture, "default.custom.yaml").writeText("patch:\n  schema_list:\n    - schema: t9_pinyin\n    - schema: rime_ice\n")
        val baseline = InstrumentationRegistry.getArguments().getString("t9_baseline") == "true"
        val learned = InstrumentationRegistry.getArguments().getString("t9_learned") == "true"
        val measureOnly = InstrumentationRegistry.getArguments().getString("t9_measure_only") == "true"
        File(fixture, "t9_pinyin.custom.yaml").writeText("patch:\n  translator/enable_user_dict: $learned\n" +
            (if (baseline) "  t9/joint_decoder: false\n  translator/max_homophones: 1\n  grammar/language: ''\n" else "") +
            InstrumentationRegistry.getArguments().getString("t9_overrides", ""))
        val compiled = File(fixture, "build").apply { mkdirs() }
        for (dictionary in listOf("rime_ice", "melt_eng", "radical_pinyin", "cyime_t9_english")) {
            for (suffix in listOf("table.bin", "reverse.bin")) {
                val source = File(user, "build/$dictionary.$suffix")
                if (source.isFile) source.copyTo(File(compiled, source.name))
            }
        }
        val evidenceFile = InstrumentationRegistry.getArguments().getString("t9_fixture", "t9_reported_cases.json")!!
        val evidence = JSONObject(instrumentation.context.assets.open("ime_lab/$evidenceFile")
            .bufferedReader().use { it.readText() })
        val cases = evidence.getJSONArray("constructed_and_historical_replays")
        val violations = mutableListOf<String>()
        val lockCount = InstrumentationRegistry.getArguments().getString("t9_lock_count", "0")!!.toInt()
        var lockedReadings = emptyList<String>()
        fun snapshot(): Array<Array<String>> {
            engine.awaitT9Refinement()
            return engine.inspectCandidates(100)
        }
        fun input(keys: String) {
            engine.clearQueuedT9Composition()
            keys.forEach { engine.processQueuedT9Key(it.code) }
            lockedReadings.forEach { reading ->
                assertTrue("manual pinyin selection: $reading", engine.selectQueuedT9Pinyin(reading, reading.length)?.processed == true)
            }
            if (lockedReadings.isNotEmpty()) {
                val suffix = keys.drop(lockedReadings.sumOf { it.length })
                assertTrue("locking pinyin must retain the unselected suffix: $suffix / ${engine.getInput()}",
                    engine.getInput().endsWith(suffix))
            }
        }
        try {
            engine.initialize(fixture.path, fixture.path)
            assertTrue(engine.deploy())
            assertTrue(engine.ensureSession())
            assertTrue(engine.switchSchema("t9_pinyin"))
            engine.setOption("ascii_mode", false)
            if (InstrumentationRegistry.getArguments().getString("t9_equivalence") == "true") {
                val streams = listOf("7".repeat(63),
                    "746928374625938472635927483629".repeat(3).take(63),
                    "9642633464942649869464748723294".repeat(3).take(63),
                    "964'263'346'494'264'986'".repeat(3).take(63))
                val expected = mutableListOf<List<List<String>>>()
                var checked = 0
                for (cache in listOf(false, true)) {
                    engine.destroy()
                    File(fixture, "t9_pinyin.custom.yaml").writeText("patch:\n  translator/enable_user_dict: false\n  t9/decoded_cache: $cache\n")
                    engine.initialize(fixture.path, fixture.path)
                    assertTrue(engine.deploy())
                    assertTrue(engine.switchSchema("t9_pinyin"))
                    engine.setOption("ascii_mode", false)
                    var index = 0
                    for (keys in streams) {
                        engine.clearQueuedT9Composition()
                        keys.forEach { digit ->
                            engine.processQueuedT9KeyAndGetResult(digit.code)
                            val actual = engine.inspectCandidates(20).map { it.toList() }
                            if (!cache) expected += actual
                            else {
                                assertEquals("incremental query must equal a full decode at prefix $index", expected[index], actual)
                                checked++
                            }
                            index++
                        }
                    }
                }
                File(context.filesDir, "ime-lab/t9-equivalence.json").writeText(JSONObject()
                    .put("comparedPrefixes", checked).put("candidatesPerPrefix", 20).put("allFieldsEqual", true).toString())
                return@runBlocking
            }
            if (learned) repeat(5) {
                assertTrue(engine.t9Memorize("图书馆", "tu shu guan"))
                assertTrue(engine.t9Memorize("觉得", "jue de"))
            }
            for (i in 0 until cases.length()) {
                val case = cases.getJSONObject(i)
                val id = case.getString("id")
                val selectedCases = InstrumentationRegistry.getArguments().getString("t9_cases", "")
                if (selectedCases.isNotEmpty() && id !in selectedCases.split(',')) continue
                val keys = case.optString("full_digit_keys", case.optString("raw_keys"))
                val readings = case.optString("pinyin").split(' ').filter { it.isNotEmpty() }
                if (lockCount > 0 && (readings.isEmpty() || readings.sumOf { it.length } != keys.length)) continue
                lockedReadings = readings.take(lockCount)
                val targets = if (case.has("target_any")) case.getJSONArray("target_any") else JSONArray().put(case.getString("target"))
                val variants = linkedMapOf((if (lockCount > 0) "locked_$lockCount" else "digits") to keys)
                if (lockCount == 0 && case.has("with_explicit_separator_key_1")) variants["separated"] = case.getString("with_explicit_separator_key_1")
                for ((variant, replay) in variants) {
                    val started = SystemClock.elapsedRealtimeNanos()
                    input(replay)
                    val rows = snapshot()
                    val texts = rows.map { it[0] }
                    val rank = texts.indexOfFirst { text -> (0 until targets.length()).any { targets.getString(it) == text } } + 1
                    val record = JSONObject().put("id", id).put("variant", variant).put("keys", replay)
                        .put("engine_input", engine.getInput()).put("target_rank", rank).put("baseline", baseline)
                        .put("learned_fixture", learned).put("ranking_enforced", !measureOnly && !baseline)
                        .put("scoring_status", engine.t9ScoringStatus())
                        .put("latency_ms", (SystemClock.elapsedRealtimeNanos() - started) / 1e6)
                        .put("candidates", JSONArray(rows.map { JSONArray(it.toList()) }))
                    results.appendText(record.toString() + "\n")
                    if (rank == 0) violations += "$id/$variant missing ${targets}"
                    if (!case.isNull("target_rank_requirement") && case.has("target_rank_requirement") && rank != case.getInt("target_rank_requirement"))
                        violations += "$id/$variant rank=$rank expected=${case.getInt("target_rank_requirement")} top=${texts.take(5)}"
                    val lower = case.optJSONArray("pairwise_lower_priority_in_this_fixture") ?: JSONArray()
                    for (j in 0 until lower.length()) {
                        val other = texts.indexOf(lower.getString(j)) + 1
                        if (other > 0 && (rank == 0 || rank >= other)) violations += "$id/$variant loses to ${lower.getString(j)}"
                    }
                    if (lockedReadings.isNotEmpty()) {
                        // Full-syllable left selection uses the existing segment-undo
                        // contract: backspace unlocks the last syllable before deleting
                        // digits. Replay that selection, rather than appending a digit.
                        val beforeInput = engine.getInput()
                        engine.processQueuedT9Key(0xff08)
                        val releasedSuffix = replay.drop(lockedReadings.dropLast(1).sumOf { it.length })
                        assertTrue("undo pinyin preserves digits: $id", engine.getInput().endsWith(releasedSuffix))
                        val reading = lockedReadings.last()
                        assertTrue(engine.selectQueuedT9Pinyin(reading, reading.length)?.processed == true)
                        assertEquals(beforeInput, engine.getInput())
                        if (texts != snapshot().map { it[0] }) violations += "$id/$variant reselected pinyin changed order"
                    } else if (replay.isNotEmpty()) {
                        engine.processQueuedT9Key(0xff08)
                        engine.processQueuedT9Key(replay.last().code)
                        if (texts != snapshot().map { it[0] }) violations += "$id/$variant backspace replay changed order"
                    }
                    input(replay)
                    if (texts != snapshot().map { it[0] }) violations += "$id/$variant fresh composition changed order"
                    assertEquals("native/global list identity: $id", texts, engine.getAllCandidates(100).map { it.text })
                }
            }
            lockedReadings = emptyList()
            // Check before engine destruction resets the scorer lifecycle.
            if (!baseline && InstrumentationRegistry.getArguments().getString("t9_cases", "").isEmpty())
                assertEquals("grammar=ready;sentence=ready", engine.t9ScoringStatus())
            // Negative feedback retains the exact native candidate objects/order;
            // it neither tombstones component words nor teaches the next item.
            input("6364986743663")
            val blocked = mutableListOf<String>()
            repeat(3) {
                val before = snapshot().map { it[0] }
                assertTrue(before.size > 4)
                val revision = engine.readQueuedComposition()!!.engineRevision
                assertTrue(engine.suppressCandidateAtRevision(0, revision, global = true))
                assertFalse("stale suppression rejected", engine.suppressCandidateAtRevision(0, revision, global = true))
                blocked += before.first()
                val after = snapshot().map { it[0] }
                assertEquals(before.drop(1), after.take(before.size - 1))
                assertFalse(before.first() in after)
                assertEquals(after, engine.getAllCandidates(100).map { it.text })
            }
            engine.destroy()
            engine.initialize(fixture.path, fixture.path)
            assertTrue(engine.ensureSession())
            assertTrue(engine.switchSchema("t9_pinyin"))
            engine.setOption("ascii_mode", false)
            input("6364986743663")
            assertTrue("suppression survives restart", snapshot().none { it[0] in blocked })
            assertTrue("persistent feedback management", engine.getSuppressedCandidates().containsAll(blocked))
            blocked.forEach { assertTrue(engine.restoreSuppressedCandidate(it)) }
            input("6364986743663")
            assertTrue("suppression can be undone", blocked.all { text -> snapshot().any { it[0] == text } })

            // Real native selection, both APIs, after a reorder and a suppression.
            val selectedTexts = snapshot().take(5).map { it[0] }
            selectedTexts.forEach { text ->
                input("6364986743663")
                val row = snapshot().first { it[0] == text }
                assertTrue(engine.selectCandidateByGlobalIndex(row[4].toInt()))
                assertEquals("global selection commits displayed text", text, engine.commit())
                input("6364986743663")
                val firstPage = engine.getCandidates().toList()
                val index = firstPage.indexOf(text)
                if (index >= 0) {
                    assertTrue(engine.selectCandidate(index))
                    assertEquals("page selection commits displayed text", text, engine.commit())
                }
            }
            if (learned && !baseline) {
                // Cache a query BEFORE changing a shared user dictionary through
                // the processor's separate UserDictionary instance. Both learning
                // and forgetting (which does not advance tick) must match a new
                // translator, including native scores, codes and component spans.
                fun coldSnapshot(): List<List<String>> {
                    engine.clearQueuedT9Composition()
                    assertTrue(engine.switchSchema("rime_ice"))
                    assertTrue(engine.switchSchema("t9_pinyin"))
                    engine.setOption("ascii_mode", false)
                    input("5833")
                    return snapshot().map { it.toList() }
                }
                input("5833")
                val beforeLearning = snapshot().map { it.toList() }
                repeat(5) { assertTrue(engine.t9Memorize("觉得", "jue de")) }
                input("5833")
                val afterLearning = snapshot().map { it.toList() }
                assertNotEquals("learning must invalidate previously cached scores", beforeLearning, afterLearning)
                assertEquals("warm learned result equals cold translator", afterLearning, coldSnapshot())
                assertTrue(engine.t9Forget("觉得", "jue de"))
                input("5833")
                val afterForgetting = snapshot().map { it.toList() }
                assertNotEquals("forget must invalidate even without tick increment", afterLearning, afterForgetting)
                assertEquals("warm forgotten result equals cold translator", afterForgetting, coldSnapshot())
            }
            android.util.Log.i("T9ReportedRegression", violations.joinToString("\n"))
            if (!baseline && !measureOnly) assertTrue(violations.joinToString("\n"), violations.isEmpty())
        } finally {
            android.util.Log.i("T9ReportedRegression", "RESULT_FILE=${results.path}")
            engine.destroy()
            engine.initialize(user, shared)
            engine.ensureSession()
            if (previousSchema.isNotEmpty()) engine.switchSchema(previousSchema)
            // Evidence is outside this private fixture; release large copied
            // dictionaries after the native engine has closed its mappings.
            assertTrue("remove isolated replay fixture", fixture.deleteRecursively())
        }
    }
}
