package com.kingzcheung.xime.speech

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** The pre-optimized Zipformer encoder is used only for the exact source file it was made from. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class OptimizedEncoderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val modelDir = File(context.filesDir, "models/zipformer-zh-int8").apply { mkdirs() }
    private val cache = File(context.noBackupFilesDir, "asr-optimized/zipformer-zh-int8")
    private val source = File(modelDir, "encoder.int8.onnx")

    private fun publish() {
        cache.mkdirs()
        File(cache, "encoder.basic.onnx").writeBytes(byteArrayOf(1, 2, 3))
        File(cache, "encoder.basic.stamp").writeText("${source.name}:${source.length()}:${source.lastModified()}")
    }

    @Test fun `copy made from this source is used, any change to the source drops it`() {
        source.writeBytes(ByteArray(10))
        assertNull(OptimizedEncoder.path(context, modelDir, source.name))
        publish()
        assertEquals(File(cache, "encoder.basic.onnx"), OptimizedEncoder.path(context, modelDir, source.name))
        source.writeBytes(ByteArray(11)) // re-downloaded or replaced
        assertNull(OptimizedEncoder.path(context, modelDir, source.name))
    }

    @Test fun `rejected or forgotten copies are not used again`() {
        source.writeBytes(ByteArray(10))
        publish()
        OptimizedEncoder.reject(context, modelDir.name)
        assertNull(OptimizedEncoder.path(context, modelDir, source.name))
        publish()
        OptimizedEncoder.forget(context, modelDir.name)
        assertFalse(cache.exists())
    }

    @Test fun `the copy lives outside the model directory so a resident engine is not reloaded`() {
        source.writeBytes(ByteArray(10))
        publish()
        assertEquals(listOf("encoder.int8.onnx"), modelDir.listFiles()!!.map { it.name })
    }
}
