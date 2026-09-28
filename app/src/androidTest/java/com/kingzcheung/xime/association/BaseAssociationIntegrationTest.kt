package com.kingzcheung.xime.association
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class BaseAssociationIntegrationTest {
 @Test fun bundledPriorWorksWithoutOnnxAndDoesNotWriteHistory() = runBlocking {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val cache=File(c.filesDir,"user_ngram_cache.json")
  val before=if(cache.exists()) cache.readBytes().toList() else null
  val fusion=NgramFusionEngine(c)
  assertTrue(fusion.initialize())
  val words=fusion.fuseCandidates(emptyList(),"今天晚上")
  assertTrue("Bundled model must supply candidates without ONNX",words.isNotEmpty())
  android.util.Log.i("BaseCorpusProbe", "今天晚上 => "+words.take(6).joinToString { it.text })
  assertEquals(before,if(cache.exists()) cache.readBytes().toList() else null)
 }
}
