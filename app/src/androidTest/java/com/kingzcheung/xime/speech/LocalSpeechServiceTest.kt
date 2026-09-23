package com.kingzcheung.xime.speech

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.service.AsrInferenceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class LocalSpeechServiceTest {
    @Test fun modelSwitchStopAndCancelUseTheIndependentService() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.getExternalFilesDir(null), "speech-eval")
        assumeTrue(File(root, "zh.wav").isFile)
        for (id in listOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.SENSEVOICE)) {
            val info = AsrModelManager(context).getAsrModels().first { it.id == id }
            val destination = File(context.filesDir, "models/$id").apply { mkdirs() }
            for (name in info.files) {
                val from = File(root, "$id/$name")
                assumeTrue(from.isFile)
                val to = File(destination, name)
                if (!to.isFile || to.length() != from.length()) from.copyTo(to, overwrite = true)
            }
        }
        val pcm = File(root, "zh.wav").readBytes().copyOfRange(44, File(root, "zh.wav").length().toInt())
        val client = AsrInferenceClient(context)
        val snapshots = Collections.synchronizedList(mutableListOf<String>())
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val cb = object : AsrInferenceClient.AsrCallback {
            override fun onPartialResult(text: String) { snapshots.add(text) }
            override fun onFinalResult(text: String) { }
            override fun onError(message: String) { errors.add(message) }
        }
        try {
            assertTrue(client.ensureBound())
            for (id in listOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.TWO_PASS, SpeechModelCatalog.SENSEVOICE)) {
                assertTrue("$id: $errors", client.startAsr(id, cb))
                for (start in pcm.indices step 3200) {
                    client.pushAsrAudio(pcm.copyOfRange(start, (start + 3200).coerceAtMost(pcm.size)))
                    Thread.sleep(100)
                }
                val text = client.stopAsr()
                assertTrue("$id: $text / $errors", text.contains("时间") && text.contains("下午"))
                assertTrue(errors.toString(), errors.isEmpty())
            }
            assertTrue(client.startAsr(SpeechModelCatalog.SENSEVOICE, cb))
            client.pushAsrAudio(pcm)
            client.cancelAsr()
            val afterCancel = snapshots.size
            Thread.sleep(500)
            assertEquals("Cancelled callback escaped session ownership", afterCancel, snapshots.size)
        } finally { client.releaseAsr(); client.unbind() }
    }
}
