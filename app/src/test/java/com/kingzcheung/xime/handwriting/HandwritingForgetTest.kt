package com.kingzcheung.xime.handwriting

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Files

/** 长按手写候选＝忘掉：只删选候选学到的笔迹（pick:），训练样本（train:）和别的字不动。 */
class HandwritingForgetTest {
    @Test fun forgettingRemovesOnlyLearnedPicksOfThatCharacter() {
        val dir = Files.createTempDirectory("hw-forget").toFile()
        val templates = File(dir, "handwriting_templates").apply { mkdirs() }
        fun entry(label: String, source: String) = JSONObject().put("l", label).put("s", source)
            .put("v", JSONArray().put(1.0).put(0.0))
        File(templates, "latin.json").writeText(JSONArray()
            .put(entry("c", "pick:1")).put(entry("c", "pick:2")).put(entry("c", "train:abc"))
            .put(entry("a", "pick:3")).toString())
        val context = mock<Context> { on { filesDir } doReturn dir }

        assertEquals(2, HandwritingTemplates.forget(context, "c"))
        val saved = JSONArray(File(templates, "latin.json").readText())
        val left = (0 until saved.length()).map { saved.getJSONObject(it).let { o -> o.getString("l") + "/" + o.getString("s") } }
        assertEquals(listOf("c/train:abc", "a/pick:3"), left)
        assertEquals(0, HandwritingTemplates.forget(context, "c"))
        dir.deleteRecursively()
    }
}
