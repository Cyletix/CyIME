package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageHousekeepingTest {
    @get:Rule val tmp = TemporaryFolder()
    private val id = "0f8fad5b-d9cb-469f-a165-70867728950e"

    @Test fun `只认出中断留下的临时文件`() {
        val cache = tmp.newFolder("cache")
        val models = tmp.newFolder("models")
        val rime = tmp.newFolder("rime")
        val stale = listOf(
            File(cache, "model-download-$id").apply { mkdirs(); File(this, "encoder.onnx").writeText("partial") },
            File(cache, "model-import-$id").apply { mkdirs() },
            File(cache, "schema-install-$id").apply { mkdirs() },
            File(cache, "-1234567.tar.bz2").apply { writeText("archive") },
            File(cache, "ocr-frames/$id.png").apply { parentFile!!.mkdirs(); writeText("png") },
            File(models, ".t9-ngram-$id.previous").apply { mkdirs() },
            File(models, ".download-t9-ngram-small-$id").apply { mkdirs(); File(this, "t9_ngram.lm").writeText("partial") },
            File(models, ".import-ochwpro-$id").apply { mkdirs() },
            File(models, ".bundle-paraformer-zh-en-int8-$id").apply { mkdirs() },
            File(rime, ".t9_ngram.lm.$id.installing").apply { writeText("link") },
        )
        val kept = listOf(
            File(cache, "coil_cache").apply { mkdirs() },
            File(cache, "clipboard_images").apply { mkdirs() },
            File(cache, "model-download-notauuid").apply { mkdirs() },
            File(models, "t9-ngram").apply { mkdirs() },
            File(models, "download-t9-ngram-$id").apply { mkdirs() },
            File(rime, "t9_ngram.lm").apply { writeText("model") },
            File(rime, "t9_pinyin.schema.yaml.installing").apply { writeText("schema") },
        )
        val found = StorageHousekeeping.findLeftovers(cache, models, rime).toSet()
        assertEquals(stale.toSet(), found)
        assertTrue(kept.none { it in found })
    }

    @Test fun `删除残留并报告腾出的空间`() {
        val cache = tmp.newFolder("cache")
        val partial = File(cache, "model-download-$id").apply { mkdirs(); File(this, "a.onnx").writeText("12345") }
        val freed = StorageHousekeeping.removeLeftovers(listOf(partial))
        assertEquals(5L, freed)
        assertFalse(partial.exists())
    }

    @Test fun `替换中途被杀时把挪开的模型放回原处`() {
        val models = tmp.newFolder("models")
        val previous = File(models, ".paraformer-zh-en-int8-$id.previous").apply {
            mkdirs(); File(this, "model.onnx").writeText("weights")
        }
        StorageHousekeeping.removeLeftovers(listOf(previous))
        assertFalse(previous.exists())
        assertEquals("weights", File(models, "paraformer-zh-en-int8/model.onnx").readText())
    }

    @Test fun `新模型已装好时删掉挪开的旧模型`() {
        val models = tmp.newFolder("models")
        File(models, "paraformer-zh-en-int8").apply { mkdirs(); File(this, "model.onnx").writeText("new") }
        val previous = File(models, ".paraformer-zh-en-int8-$id.previous").apply {
            mkdirs(); File(this, "model.onnx").writeText("old")
        }
        StorageHousekeeping.removeLeftovers(listOf(previous))
        assertFalse(previous.exists())
        assertEquals("new", File(models, "paraformer-zh-en-int8/model.onnx").readText())
    }
}
