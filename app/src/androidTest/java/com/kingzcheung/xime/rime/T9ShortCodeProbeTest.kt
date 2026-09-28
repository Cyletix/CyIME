package com.kingzcheung.xime.rime
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
class T9ShortCodeProbeTest {
 @Test fun inspectShortCodes() = runBlocking {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val e=RimeEngine.getInstance()
  val (u,s)=RimeConfigHelper.initializeRimeDataAsync(c)
  e.initialize(u,s)
  assertTrue(RimeConfigHelper.ensureDeployment(c))
  assertTrue(e.ensureSession())
  val previous=e.getCurrentSchema()
  try {
   assertTrue(e.switchSchema("t9_pinyin"));e.setOption("ascii_mode",false)
   for (code in listOf("7","746","77","65")) {
    e.clearQueuedT9Composition();code.forEach { e.processQueuedT9Key(it.code) }
    val rows=e.inspectCandidates(25)
    if (code == "7") {
     assertEquals("是", rows.first()[0])
     assertEquals("s",e.getProcessResult(true).preeditText)
    }
    if (code == "746") assertFalse("sho must not match shou", rows.first()[1] == "shou")
    android.util.Log.i("ShortCodeProbe",code+" => "+rows.joinToString { it.joinToString("|") })
   }
  } finally { e.clearQueuedT9Composition();e.switchSchema(previous) }
 }
}
