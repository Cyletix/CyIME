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
            assertEquals(to != SpeechModelCatalog.SENSEVOICE && SpeechModelSelection.hasCorrection(from),
                SpeechModelSelection.hasCorrection(result))
        } }
    }

    @Test fun correctionNeverChangesPrimaryOrPairsSenseVoiceWithItself() {
        modes.forEach { mode -> listOf(false, true).forEach { enabled ->
            val result = SpeechModelSelection.withCorrection(mode, enabled)
            assertEquals(SpeechModelSelection.primary(mode), SpeechModelSelection.primary(result))
            assertEquals(enabled && mode != SpeechModelCatalog.SENSEVOICE, SpeechModelSelection.hasCorrection(result))
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
}
