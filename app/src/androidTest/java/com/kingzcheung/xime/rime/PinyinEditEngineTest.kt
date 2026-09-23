package com.kingzcheung.xime.rime

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PinyinEditEngineTest {
    private val engine = RimeEngine.getInstance()
    @Before fun ready(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (user, shared) = RimeConfigHelper.initializeRimeDataAsync(context)
        engine.initialize(user, shared)
        assertTrue(RimeConfigHelper.ensureDeployment(context))
        assertTrue(engine.ensureSession())
        engine.clearQueuedT9Composition()
        engine.clearQueuedComposition()
    }
    private fun mode(id: String) { assertTrue(engine.switchSchema(id)); engine.setOption("ascii_mode", false) }

    @Test fun fullPinyinEditPreservesHostCommitAndSetsRealCaret() {
        mode("rime_ice")
        assertTrue(engine.setInput("nihao"))
        val s = engine.readPinyinEditSnapshot()
        assertEquals("nihao", s[0]); assertEquals("5", s[1])
        assertTrue(engine.applyPinyinEdit(s[0], "nihao", 2))
        assertEquals("2", engine.readPinyinEditSnapshot()[1])
        engine.processKey('m'.code, 0)
        assertEquals("nimhao", engine.getInput())
        assertEquals("编辑不能上屏", "", engine.commit())
        assertFalse("过期草稿不能覆盖新输入", engine.applyPinyinEdit("nihao", "wo", 2))
        assertEquals("nimhao", engine.getInput())
        engine.clearQueuedComposition()
    }
    @Test fun fullPinyinEditKeepsConfirmedPrefix() {
        mode("rime_ice"); engine.setInput("nihao")
        val index = engine.getCandidates().indexOf("你")
        assertTrue("测试需要未消费整串的单字候选", index >= 0)
        assertTrue(engine.selectCandidate(index))
        assertEquals("", engine.commit())
        val s = engine.readPinyinEditSnapshot()
        val confirmed = s[2].toInt()
        assertTrue(confirmed > 0); assertEquals("你", s[3])
        val newInput = s[0].take(confirmed) + "men"
        assertTrue(engine.applyPinyinEdit(s[0], newInput, newInput.length, confirmed, s[3]))
        assertEquals("你", engine.readPinyinEditSnapshot()[3])
        assertTrue(engine.conversionPreview().startsWith("你"))
        assertEquals("", engine.commit())
        engine.clearQueuedComposition()
    }
    @Test fun t9ReplacementRebuildsDigitsSelectionsAndFollowingDelete() {
        mode("t9_pinyin")
        "64426".forEach { engine.processKey(it.code, 0) }; engine.t9FlushRimeInput()
        assertTrue(engine.applyT9PinyinEdit(engine.getInput(), "ni'hao"))
        val edited = engine.getInput()
        assertTrue(edited.contains("ni")); assertTrue(edited.contains("hao"))
        assertEquals("", engine.commit())
        engine.processKey('6'.code, 0); engine.t9FlushRimeInput()
        assertTrue("新数字必须接在编辑后的音节之后", engine.getInput().contains("ni"))
        engine.processKey(0xff08, 0); engine.t9FlushRimeInput()
        assertEquals("删新数字后恢复编辑串，不能出现旧数字", edited, engine.getInput())
        assertFalse(engine.applyT9PinyinEdit("stale", "wo"))
        assertEquals(edited, engine.getInput())
        engine.clearQueuedT9Composition()
    }
    @Test fun mergedAndDoublePinyinEditUseActualCodeWithoutChangingSchema() {
        for ((schema, input, replacement) in listOf(Triple("pinyin_14jian", "bugao", "nihao"), Triple("double_pinyin_flypy", "nihk", "womf"))) {
            mode(schema); engine.clearQueuedComposition(); engine.setInput(input)
            assertTrue(engine.applyPinyinEdit(input, replacement, replacement.length))
            assertEquals(schema, engine.getCurrentSchema()); assertEquals(replacement, engine.getInput())
            assertTrue(engine.getCandidates().isNotEmpty()); assertEquals("", engine.commit())
        }
        engine.clearQueuedComposition()
    }
    @Test fun readOnlyEditorSnapshotDoesNotConsumePendingCommit() {
        mode("rime_ice"); engine.setInput("nihao")
        val index = engine.getCandidates().indexOf("你好")
        assertTrue(index >= 0); assertTrue(engine.selectCandidate(index))
        engine.readPinyinEditSnapshot()
        assertEquals("快照不能吞掉等待上屏的选词", "你好", engine.commit())
    }
    @Test fun editTransactionKeepsPendingCommitForItsOriginalOwner() {
        for (active in listOf(false, true)) {
            mode("rime_ice"); engine.setInput("nihao")
            val index = engine.getCandidates().indexOf("你好")
            assertTrue(index >= 0); assertTrue(engine.selectCandidate(index))
            // A preceding commit is awaiting delivery while another composition already exists.
            engine.setInput("ni")
            val result = engine.editPinyinAndReadState {
                assertTrue("编辑与快照必须在同一把锁内", RimeEngine.rimeLock.isHeldByCurrentThread)
                engine.applyPinyinEdit("ni", "wo", 2, expectedSchema = "rime_ice") { active }
            }
            if (active) {
                assertNotNull(result)
                assertEquals("wo", result!!.inputText)
                assertEquals("编辑快照不得提取待上屏文字", "", result.committedText)
            } else assertNull("旧会话必须被拒绝", result)
            assertEquals("拒绝或成功编辑都不能消费先前待提交", "你好", engine.commit())
            engine.clearQueuedComposition()
        }
    }
    @Test fun sameCodeInNewSchemaOrSessionRejectsOldEditor() {
        mode("rime_ice"); engine.setInput("nihao")
        mode("pinyin_14jian"); engine.setInput("nihao")
        assertFalse(engine.applyPinyinEdit("nihao", "wo", 2, expectedSchema = "rime_ice"))
        assertEquals("nihao", engine.getInput())
        assertFalse(engine.applyPinyinEdit("nihao", "wo", 2, expectedSchema = "pinyin_14jian") { false })
        assertEquals("nihao", engine.getInput())
        engine.clearQueuedComposition()
    }

    @Test fun schemaSwitchReappliesPageSizeToTheLiveSchema() {
        mode("rime_ice")
        for ((schema, code) in listOf("double_pinyin_flypy" to "ui", "rime_ice" to "shi", "pinyin_14jian" to "shi")) {
            engine.setPageSize(schema, 12) // Deliberately set BEFORE switching, as the service does.
            mode(schema); engine.setInput(code)
            assertEquals("$schema lost mobile page size", 12, engine.getCandidates().size)
            engine.setPageSize(schema, 12)
            assertEquals(code, engine.getInput())
            engine.clearQueuedComposition()
        }
        engine.setPageSize("rime_ice", 20)
    }
    @Test fun englishRejectsRestoredFullWidthButChineseRetainsItsOption() {
        mode("rime_ice"); engine.setOption("full_shape", true)
        assertTrue(engine.getOption("full_shape"))
        engine.setOption("ascii_mode", true)
        val result = engine.processKeyAndGetResult('s'.code, 0)
        assertTrue("ASCII passes through or commits half-width: $result", result.committedText in listOf("", "s"))
        assertEquals("", engine.getInput())
        assertFalse(engine.getOption("full_shape"))
        engine.setOption("full_shape", true)
        engine.processKey('d'.code, 0)
        assertTrue(engine.commit() in listOf("", "d"))
        assertFalse(engine.getOption("full_shape"))
        engine.setOption("ascii_mode", false)
    }
    @Test fun queuedNineKeyWaitsForContendedLockAndFlushesBeforeReturning() {
        mode("t9_pinyin")
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            for (key in listOf('6'.code, 0xff08)) {
                val entered = java.util.concurrent.CountDownLatch(1)
                RimeEngine.rimeLock.lock()
                val result = try {
                    val future = worker.submit<Boolean> { entered.countDown(); engine.processQueuedT9Key(key) }
                    assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
                    Thread.sleep(80)
                    assertFalse("a key must wait instead of reporting not-consumed", future.isDone)
                    future
                } finally { RimeEngine.rimeLock.unlock() }
                assertTrue(result.get(5, java.util.concurrent.TimeUnit.SECONDS))
                if (key == '6'.code) assertTrue(engine.getInput().isNotEmpty()) else assertEquals("", engine.getInput())
            }
        } finally { worker.shutdownNow() }
    }
}
