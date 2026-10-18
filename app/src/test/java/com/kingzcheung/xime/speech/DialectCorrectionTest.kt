package com.kingzcheung.xime.speech

import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.*
import org.junit.Test

class DialectCorrectionTest {
    @Test fun dialectModeKeepsPrimaryAndCorrector() {
        for (primary in listOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.ZIPFORMER))
            for (corrector in listOf(DialectCorrection.DOLPHIN, DialectCorrection.TELESPEECH)) {
                val mode = DialectCorrection.mode(primary, corrector)
                assertEquals(primary, DialectCorrection.primary(mode))
                assertEquals(corrector, DialectCorrection.corrector(mode))
            }
    }

    @Test fun existingModesAreNotDialectModes() {
        for (mode in SpeechModelSelection.primaryIds + listOf(SpeechModelCatalog.TWO_PASS, SpeechModelCatalog.ZIPFORMER_TWO_PASS)) {
            assertNull(mode, DialectCorrection.primary(mode))
            assertNull(mode, DialectCorrection.corrector(mode))
        }
        assertNull(DialectCorrection.corrector("${SpeechModelCatalog.PARAFORMER}+unknown"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun senseVoiceIsNotEncodedAsDialectMode() {
        DialectCorrection.mode(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.SENSEVOICE)
    }

    @Test fun onlyChineseOutputPasses() {
        for (text in listOf("他这个管儿一箱都通", "我用 Wi-Fi 打电话 2026", "对我做了介绍啊，那么。"))
            assertFalse(text, DialectCorrection.hasForeignScript(text))
        for (text in listOf("これは", "안녕하세요", "สวัสดี", "xin chào привет"))
            assertTrue(text, DialectCorrection.hasForeignScript(text))
    }

    @Test fun pieceSpacingIsRemovedBetweenChineseWords() {
        assertEquals("他这个管儿一箱都通", DialectCorrection.clean(" 他这个 管 儿 一箱 都通"))
    }

    @Test fun pinnedDownloadsAreComplete() {
        assertEquals(listOf(DialectCorrection.DOLPHIN, DialectCorrection.TELESPEECH), DialectCorrection.models.map { it.id })
        for (model in DialectCorrection.models) {
            val files = model.versions.single().files
            assertEquals(listOf("model.int8.onnx", "tokens.txt"), files.map { it.name })
            files.forEach { assertTrue(it.downloadUrl.startsWith("https://") && it.sha256.length == 64) }
        }
    }

    @Test fun selectionKeepsDialectCorrectorAcrossPrimaryChanges() {
        val dolphin = SpeechModelSelection.withCorrector(SpeechModelCatalog.PARAFORMER, DialectCorrection.DOLPHIN)
        assertTrue(SpeechModelSelection.hasCorrection(dolphin))
        assertEquals(SpeechModelCatalog.PARAFORMER, SpeechModelSelection.primary(dolphin))
        assertEquals(DialectCorrection.DOLPHIN, SpeechModelSelection.corrector(dolphin))
        assertEquals(dolphin, SpeechModelSelection.withCorrection(dolphin, true))
        assertEquals(SpeechModelCatalog.PARAFORMER, SpeechModelSelection.withCorrection(dolphin, false))
        val zip = SpeechModelSelection.selectPrimary(dolphin, SpeechModelCatalog.ZIPFORMER)
        assertEquals(DialectCorrection.DOLPHIN, SpeechModelSelection.corrector(zip))
        assertEquals(SpeechModelCatalog.SENSEVOICE, SpeechModelSelection.selectPrimary(dolphin, SpeechModelCatalog.SENSEVOICE))
        assertEquals(SpeechModelCatalog.TWO_PASS, SpeechModelSelection.withCorrector(dolphin, SpeechModelCatalog.SENSEVOICE))
        assertEquals(SpeechModelCatalog.SENSEVOICE, SpeechModelSelection.corrector(SpeechModelCatalog.TWO_PASS))
        assertNull(SpeechModelSelection.corrector(SpeechModelCatalog.PARAFORMER))
    }

    @Test fun dialectModesAreChineseOnly() {
        val tele = SpeechModelSelection.withCorrector(SpeechModelCatalog.PARAFORMER, DialectCorrection.TELESPEECH)
        assertTrue(SpeechLanguages.supports(tele, InputLanguage.CHINESE))
        assertFalse(SpeechLanguages.supports(tele, InputLanguage.ENGLISH))
        assertFalse(SpeechLanguages.supports(tele, InputLanguage.JAPANESE))
    }

    @Test fun dialectModelsAreDownloadableButNeverPrimary() {
        DialectCorrection.models.forEach { model ->
            assertTrue(SpeechModelCatalog.models.any { it.id == model.id })
            assertFalse(model.id in SpeechModelSelection.primaryIds)
        }
    }
}
