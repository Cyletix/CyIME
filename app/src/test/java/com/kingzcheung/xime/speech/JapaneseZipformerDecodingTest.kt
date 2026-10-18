package com.kingzcheung.xime.speech

import org.junit.Assert.*
import org.junit.Test

class JapaneseZipformerDecodingTest {
    @Test fun silenceIsNotAnOmissionButVoicedTailIs() {
        assertFalse(JapaneseZipformerDecoding.suspectGap(listOf(1f, 2f), 8.0, listOf(0.0 to 1.2)))
        assertTrue(JapaneseZipformerDecoding.suspectGap(listOf(1f, 2f), 8.0, listOf(0.0 to 7.0)))
    }
    @Test fun paddingAndRecursiveCutsPreserveEverySampleAndSeparateOutputs() {
        val pcm = FloatArray(160000) { if (it in 78000..81000) 0f else (it + 1) / 160000f }
        val inputs = mutableListOf<FloatArray>()
        val text = JapaneseZipformerDecoding.decode(pcm, recognize = { padded ->
            assertTrue(padded.take(14400).all { it == 0f })
            assertTrue(padded.takeLast(14400).all { it == 0f })
            val input = padded.copyOfRange(14400, padded.size - 14400)
            inputs.add(input)
            if (inputs.size == 1) JapaneseZipformerDecoding.Output("漏れ", listOf("漏", "れ"), listOf(0f, 1f))
            else JapaneseZipformerDecoding.Output("東京", listOf("東", "京"), (0..(input.size / 16000)).map { it + .9f })
        }, speech = { listOf(0.0 to it.size / 16000.0) })
        assertEquals(3, inputs.size)
        assertArrayEquals(pcm, inputs[1] + inputs[2], 0f)
        assertEquals("東京 東京", text)
        assertTrue(inputs[1].size in 78000..81000)
    }
    @Test fun spacingDoesNotSplitFirstTokenOrDelayedHiraganaAndNeverChangesWords() {
        val output = JapaneseZipformerDecoding.Output("そう東京です他のイベント", "そう東京です他のイベント".map { "$it" },
            listOf(0f, 1.5f, 2.3f, 2.4f, 2.5f, 3.5f, 4.3f, 4.4f, 5.1f, 5.2f, 5.3f, 5.4f))
        val spaced = JapaneseZipformerDecoding.spaced(output)
        assertEquals("そう 東京です 他の イベント", spaced)
        assertEquals(output.text, spaced.replace(" ", ""))
        assertEquals(output.text, JapaneseZipformerDecoding.spaced(output.copy(tokens = listOf("unknown"))))
    }
    @Test fun japaneseIsStandaloneAndCannotBecomeChineseOrUseCorrection() {
        val ja = SpeechModelCatalog.ZIPFORMER_JA
        assertTrue(SpeechLanguages.supports(ja, com.kingzcheung.xime.settings.InputLanguage.JAPANESE))
        assertFalse(SpeechLanguages.supports(ja, com.kingzcheung.xime.settings.InputLanguage.CHINESE))
        assertEquals(ja, SpeechModelSelection.withCorrection(ja, true))
        assertEquals(ja, SpeechLanguages.select(com.kingzcheung.xime.settings.InputLanguage.JAPANESE, ja) { true })
    }
    @Test fun quietVoiceIsKeptWhenVadDetectsItWithoutAnEnergyGate() {
        val captured = mutableListOf<Float>()
        val detector = object : SpeechDetector { override fun speech(frame: FloatArray) = true; override fun reset() {} }
        val segmenter = SpeechSegmenter(detector, 20000, { _, _ -> }, { _, pcm, _ -> captured.addAll(pcm.toList()) }, useEnergyPause = false)
        val pcm = FloatArray(32000) { .00001f }
        segmenter.accept(pcm); segmenter.finishInput()
        assertArrayEquals(pcm, captured.toFloatArray(), 0f)
    }
}
