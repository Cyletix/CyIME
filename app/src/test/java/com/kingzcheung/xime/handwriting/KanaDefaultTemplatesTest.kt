package com.kingzcheung.xime.handwriting

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KanaDefaultTemplatesTest {
    private fun asset(): JSONObject {
        val file = listOf("src/main/assets/handwriting/kana_default_strokes.json", "app/src/main/assets/handwriting/kana_default_strokes.json")
            .map(::File).first { it.isFile }
        return JSONObject(file.readText(Charsets.UTF_8))
    }

    @Test fun builtinKanaCoversHiraganaAndKatakanaWithStrokes() {
        val chars = asset().getJSONObject("chars")
        for (kana in listOf("あ", "い", "う", "ん", "ア", "イ", "ン", "が", "ぱ", "ッ")) assertTrue(kana, chars.has(kana))
        assertTrue(chars.length() >= 170)
        for (label in chars.keys()) {
            assertTrue(label, HandwritingTemplates.Script.KANA.accepts(label))
            val variants = chars.getJSONArray(label)
            assertTrue(variants.length() >= 1)
            val first = variants.getJSONArray(0)
            assertTrue(label, first.length() >= 1)
            assertEquals(2, first.getJSONArray(0).getJSONArray(0).length())
        }
    }
}
