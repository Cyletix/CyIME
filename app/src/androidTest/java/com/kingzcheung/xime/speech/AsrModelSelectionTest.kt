package com.kingzcheung.xime.speech

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AsrModelSelectionTest {
    @Test fun selectionPairsPersistAndPreserveCorrectionOnBaseSwitch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = AsrModelManager(context)
        val original = manager.getSelectedModelId()
        try {
            manager.setModel(SpeechModelCatalog.TWO_PASS)
            assertEquals(SpeechModelCatalog.PARAFORMER, manager.getFirstPassModelId())
            assertTrue(manager.isRefinementEnabled())
            manager.setFirstPassModel(SpeechModelCatalog.ZIPFORMER)
            assertEquals(SpeechModelCatalog.ZIPFORMER_TWO_PASS, AsrModelManager(context).getSelectedModelId())
            val pair = manager.selection()
            assertEquals(SpeechModelCatalog.ZIPFORMER, pair.first!!.id)
            assertEquals(SpeechModelCatalog.SENSEVOICE, pair.secondDir!!.name)
            manager.setRefinementEnabled(false)
            assertNull(manager.selection().secondDir)
            manager.setFirstPassModel(SpeechModelCatalog.PARAFORMER)
            assertFalse(manager.isRefinementEnabled())
            manager.setRefinementEnabled(true)
            assertEquals(SpeechModelCatalog.TWO_PASS, manager.getSelectedModelId())
            manager.setModel(SpeechModelCatalog.SENSEVOICE)
            assertTrue(manager.isRefinementEnabled())
            assertNotEquals(SpeechModelCatalog.SENSEVOICE, manager.getFirstPassModelId())
        } finally { manager.setModel(original) }
    }
}
