package com.kingzcheung.xime.handwriting

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Files

/** 训练页开关个人汉字模型后，假名模板（用这个模型的输出当特征）立刻作废重来，不用重启进程。 */
class HandwritingRecognizerChangeTest {
    @Test fun switchingTheChineseModelDropsKanaFeaturesBuiltWithTheOldOne() {
        val dir = Files.createTempDirectory("hw-model-switch").toFile()
        val templates = File(dir, "handwriting_templates").apply { mkdirs() }
        val context = mock<Context> { on { filesDir } doReturn dir }
        // 模板缓存是进程内单例：先清一次，免得别的测试读过的空列表留在缓存里
        HandwritingTemplates.onRecognizerChanged(context)
        val kana = File(templates, HandwritingTemplates.Script.KANA.fileName)
        kana.writeText(JSONArray().put(JSONObject().put("l", "あ").put("s", "train:x")
            .put("i", JSONArray().put(3)).put("v", JSONArray().put(1.0))).toString())
        assertEquals(1, HandwritingTemplates.count(context, HandwritingTemplates.Script.KANA))

        HandwritingTemplates.onRecognizerChanged(context)

        assertFalse(kana.exists())
        assertEquals(0, HandwritingTemplates.count(context, HandwritingTemplates.Script.KANA))
        assertTrue(templates.isDirectory)
        dir.deleteRecursively()
    }
}
