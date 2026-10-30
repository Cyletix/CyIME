package com.kingzcheung.xime.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechModelSelectionTest {
    private val bases = SpeechModelSelection.primaryIds
    private val modes = bases + listOf(SpeechModelCatalog.TWO_PASS, SpeechModelCatalog.ZIPFORMER_TWO_PASS)

    @Test fun everyPrimaryCanBeSelectedFromEveryStoredMode() {
        modes.forEach { from -> bases.forEach { to ->
            val result = SpeechModelSelection.selectPrimary(from, to)
            assertEquals(to, SpeechModelSelection.primary(result))
            assertEquals(to in listOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.ZIPFORMER) && SpeechModelSelection.hasCorrection(from),
                SpeechModelSelection.hasCorrection(result))
        } }
    }

    @Test fun correctionNeverChangesPrimaryOrPairsSenseVoiceWithItself() {
        modes.forEach { mode -> listOf(false, true).forEach { enabled ->
            val result = SpeechModelSelection.withCorrection(mode, enabled)
            assertEquals(SpeechModelSelection.primary(mode), SpeechModelSelection.primary(result))
            assertEquals(enabled && SpeechModelSelection.primary(mode) in listOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.ZIPFORMER), SpeechModelSelection.hasCorrection(result))
        } }
    }

    @Test fun storedPairsRemainCompatible() {
        assertEquals(SpeechModelCatalog.ZIPFORMER_TWO_PASS, SpeechModelSelection.withCorrection(SpeechModelCatalog.ZIPFORMER, true))
        assertEquals(SpeechModelCatalog.TWO_PASS, SpeechModelSelection.withCorrection(SpeechModelCatalog.PARAFORMER, true))
        assertEquals(SpeechModelCatalog.SENSEVOICE, SpeechModelSelection.withCorrection(SpeechModelCatalog.SENSEVOICE, true))
    }

    @Test(expected = IllegalArgumentException::class) fun pairCannotBeSelectedAsPrimary() {
        SpeechModelSelection.selectPrimary(SpeechModelCatalog.ZIPFORMER, SpeechModelCatalog.TWO_PASS)
    }

    @Test fun removedDialectCorrectionFallsBackToSenseVoice() {
        assertEquals(SpeechModelCatalog.TWO_PASS, SpeechModelSelection.withoutRetiredCorrector("${SpeechModelCatalog.PARAFORMER}+dolphin-base-ctc-int8"))
        assertEquals(SpeechModelCatalog.ZIPFORMER_TWO_PASS, SpeechModelSelection.withoutRetiredCorrector("${SpeechModelCatalog.ZIPFORMER}+telespeech-ctc-int8"))
        modes.forEach { assertEquals(it, SpeechModelSelection.withoutRetiredCorrector(it)) }
    }

    /** One rule for the :asr service and the settings page: which model writes the final Chinese text. */
    @Test fun chineseFinalizerMatchesWhatTheServiceRuns() {
        val c = SpeechModelCatalog
        fun final(mode: String, whole: Boolean, xasr: Boolean, chinese: Boolean = true) =
            SpeechModelSelection.pipeline(mode, chinese, whole, xasr).finalizer
        assertEquals(c.XASR, final(c.TWO_PASS, whole = true, xasr = true))
        assertEquals(c.XASR, final(c.ZIPFORMER_TWO_PASS, whole = true, xasr = true))
        assertEquals(c.SENSEVOICE, final(c.TWO_PASS, whole = false, xasr = true))
        assertEquals(c.SENSEVOICE, final(c.TWO_PASS, whole = true, xasr = false))
        // SenseVoice alone (lite default) never hands Chinese to X-ASR, even when it is downloaded.
        assertEquals(c.SENSEVOICE, final(c.SENSEVOICE, whole = true, xasr = true))
        assertEquals(c.PARAFORMER, final(c.PARAFORMER, whole = true, xasr = true))
        // English corrects with SenseVoice; X-ASR is Chinese only.
        assertEquals(c.SENSEVOICE, final(c.TWO_PASS, whole = true, xasr = true, chinese = false))
    }

    @Test fun wholeSentenceAppliesOnlyWhereTheSessionUsesIt() {
        val c = SpeechModelCatalog
        listOf(c.TWO_PASS, c.ZIPFORMER_TWO_PASS, c.SENSEVOICE).forEach {
            assertTrue(it, SpeechModelSelection.wholeSentenceApplies(it, chinese = true))
            assertFalse(it, SpeechModelSelection.wholeSentenceApplies(it, chinese = false))
        }
        listOf(c.PARAFORMER, c.ZIPFORMER, c.ZIPFORMER_JA, c.ZIPFORMER_JA_INT8).forEach {
            assertFalse(it, SpeechModelSelection.wholeSentenceApplies(it, chinese = true))
        }
        assertFalse(SpeechModelSelection.pipeline(c.TWO_PASS, chinese = true, wholeSentenceOn = false, xasrReady = true).wholeSentence)
        assertEquals(c.PARAFORMER, SpeechModelSelection.pipeline(c.TWO_PASS, true, true, false).preview)
        assertNull(SpeechModelSelection.pipeline(c.SENSEVOICE, true, true, false).preview)
    }
}
