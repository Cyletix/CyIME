package com.kingzcheung.xime.rime

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class T9AsyncRefinementTest {
    @Test fun oldWorkerResultsCannotReplaceNewInputOrConsumeAnOldCandidate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = RimeEngine.getInstance()
        assertTrue(RimeConfigHelper.prepareEngine(context))
        val previous = engine.getCurrentSchema()
        val ascii = engine.isAsciiMode()
        try {
            assertTrue(engine.switchSchema("t9_pinyin"))
            engine.setOption("ascii_mode", false)
            engine.clearQueuedT9Composition()
            val keys = "4845247468745394942"
            keys.forEach { engine.processQueuedT9KeyAndGetResult(it.code) }
            val old = engine.readQueuedComposition()!!.engineRevision
            assertTrue(engine.t9RefinementState() != 0)
            engine.processQueuedT9KeyAndGetResult('2'.code)
            assertNull(engine.refineQueuedT9Result(old))
            assertEquals(keys + "2", engine.getInput())
            val current = engine.readQueuedComposition()!!.engineRevision
            engine.awaitT9Refinement()
            assertEquals(keys + "2", engine.getInput())
            assertFalse(engine.isCandidateRevisionCurrent(current))
            assertNull(engine.selectCandidateAtRevision(0, old, global = true))
            val refined = engine.readQueuedComposition()!!.engineRevision
            assertNotNull(engine.readCandidateBatch(refined, 0, 20))
            engine.clearQueuedT9Composition()
            assertNull(engine.refineQueuedT9Result(refined))
            assertEquals(0, engine.t9RefinementState())
            assertEquals("", engine.getInput())
        } finally {
            engine.clearQueuedT9Composition()
            engine.switchSchema(previous); engine.setOption("ascii_mode", ascii)
        }
    }
}
