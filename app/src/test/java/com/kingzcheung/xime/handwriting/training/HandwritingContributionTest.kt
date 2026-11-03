package com.kingzcheung.xime.handwriting.training

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HandwritingContributionTest {

    private fun bundled(): List<TrainingPrompt>? {
        val file = listOf(File("src/main/assets/handwriting"), File("app/src/main/assets/handwriting"))
            .firstOrNull { it.isDirectory }?.resolve("contribute_prompts.tsv") ?: return null
        return file.bufferedReader(Charsets.UTF_8).useLines { HandwritingContribution.parse(it) }
    }

    @Test fun bundledPromptsAreFixedChineseSetWithUniqueIds() {
        val prompts = bundled() ?: return
        assertEquals(150, prompts.size)
        assertEquals(prompts.size, prompts.map { it.id }.toSet().size)
        assertEquals(100, prompts.count { it.kind == "char" })
        assertEquals(20, prompts.count { it.kind == "rare" })
        assertEquals(30, prompts.count { it.kind == "word" })
        assertTrue(prompts.all { it.type == TrainingPromptType.Chinese && it.source == "contribute" })
        // 生僻字题下方给读音，没读音的人也知道写的是什么
        assertTrue(prompts.filter { it.kind == "rare" }.all { !it.hint.isNullOrEmpty() })
        assertTrue(prompts.any { it.label == "犇" && it.hint == "bēn" })
    }

    @Test fun parseSkipsCommentsAndBlankRows() {
        val prompts = HandwritingContribution.parse(sequenceOf("# 注释", "", "char\t的\t按平时的速度写", "word\t\t", "rare\t犇"))
        assertEquals(listOf("的", "犇"), prompts.map { it.label })
        assertEquals(null, prompts[1].hint)
    }

    @Test fun contributedSampleCarriesWriterButTrainingSampleDoesNot() {
        val prompt = HandwritingContribution.parse(sequenceOf("char\t的\t按平时的速度写")).single()
        val sample = TrainingSample(prompt, 100f, 100f, 2f,
            listOf(TrainingStroke(listOf(TrainingPoint(1f, 2f, 0L), TrainingPoint(3f, 4f, 16L)))), 0L)
        val contributed = JSONObject(TrainingSampleCodec.encode(sample, "1.0", "Phone", writer = "abc"))
        assertEquals("abc", contributed.getString("writer"))
        assertEquals("contribute:char:的", contributed.getString("prompt_id"))
        assertEquals("char", contributed.getString("kind"))
        val training = JSONObject(TrainingSampleCodec.encode(sample, "1.0", "Phone"))
        assertFalse(training.has("writer"))
        assertNotNull(training.getJSONArray("strokes"))
    }

    @Test fun uploadStaysHiddenUntilReceiverExists() {
        assertEquals(HandwritingContribution.UPLOAD_URL.isNotEmpty(), HandwritingContribution.uploadAvailable())
    }
}
