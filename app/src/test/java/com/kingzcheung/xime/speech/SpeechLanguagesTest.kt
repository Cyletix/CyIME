package com.kingzcheung.xime.speech

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import java.io.File

class SpeechLanguagesTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun primaryAndCorrectionMustBothSupportTheLanguage() {
        assertTrue(SpeechLanguages.supports(SpeechModelCatalog.ZIPFORMER_TWO_PASS, InputLanguage.CHINESE))
        assertFalse(SpeechLanguages.supports(SpeechModelCatalog.ZIPFORMER_TWO_PASS, InputLanguage.ENGLISH))
        assertFalse(SpeechLanguages.supports(SpeechModelCatalog.TWO_PASS, InputLanguage.JAPANESE))
        assertTrue(SpeechLanguages.supports(SpeechModelCatalog.TWO_PASS, InputLanguage.ENGLISH))
        InputLanguage.supported.forEach { assertTrue(SpeechLanguages.supports(SpeechModelCatalog.SENSEVOICE, it)) }
        InputLanguage.entries.forEach { assertFalse(SpeechLanguages.supports("unknown-model", it)) }
        SpeechModelSelection.primaryIds.forEach { assertFalse(SpeechLanguages.supports(it, InputLanguage.UNSPECIFIED)) }
    }

    @Test fun compatibleChoiceIsKeptAndItsMissingFilesAreNotSilentlyReplaced() {
        assertEquals(SpeechModelCatalog.TWO_PASS, SpeechLanguages.select(InputLanguage.ENGLISH, SpeechModelCatalog.TWO_PASS) { true })
        val checked = mutableListOf<String>()
        val error = assertThrows(IllegalStateException::class.java) {
            SpeechLanguages.select(InputLanguage.CHINESE, SpeechModelCatalog.ZIPFORMER) { checked += it; false }
        }
        assertEquals(listOf(SpeechModelCatalog.ZIPFORMER), checked)
        assertTrue(error.message!!.contains("尚未完整下载"))
    }

    @Test fun incompatibleLanguageSelectsAnInstalledRecognizerWithoutUsingAnUnsupportedFirstPass() {
        assertEquals(SpeechModelCatalog.SENSEVOICE,
            SpeechLanguages.select(InputLanguage.JAPANESE, SpeechModelCatalog.ZIPFORMER_TWO_PASS) { it == SpeechModelCatalog.SENSEVOICE })
        assertEquals(SpeechModelCatalog.PARAFORMER,
            SpeechLanguages.select(InputLanguage.ENGLISH, SpeechModelCatalog.ZIPFORMER) { it == SpeechModelCatalog.PARAFORMER })
        assertEquals(SpeechModelCatalog.SENSEVOICE,
            SpeechLanguages.select(InputLanguage.ENGLISH, SpeechModelCatalog.ZIPFORMER) { it == SpeechModelCatalog.SENSEVOICE })
        assertEquals(SpeechModelCatalog.SENSEVOICE,
            SpeechLanguages.select(InputLanguage.ENGLISH, SpeechModelCatalog.ZIPFORMER_TWO_PASS) { true })
    }

    @Test fun unknownLanguageAndMissingResourcesProduceSpecificErrors() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeechLanguages.select(InputLanguage.UNSPECIFIED, SpeechModelCatalog.SENSEVOICE) { error("must not inspect resources") }
        }
        val error = assertThrows(IllegalStateException::class.java) {
            SpeechLanguages.select(InputLanguage.JAPANESE, SpeechModelCatalog.TWO_PASS) { false }
        }
        assertTrue(error.message!!.contains("日语"))
        assertTrue(error.message!!.contains("SenseVoice"))
    }

    @Test fun realResourceSelectionPreservesPreferencesAndSeparatesRecognizerCachesByLanguage() {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        whenever(context.filesDir).thenReturn(files.root)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString(any(), anyOrNull())).thenAnswer {
            if (it.getArgument<String>(0) == "selected_model") SpeechModelCatalog.ZIPFORMER_TWO_PASS else it.getArgument<String?>(1)
        }
        val sense = File(files.root, "models/${SpeechModelCatalog.SENSEVOICE}").apply { mkdirs() }
        File(sense, "model.int8.onnx").writeBytes(byteArrayOf(1))
        File(sense, "tokens.txt").writeText("test fixture")
        val manager = AsrModelManager(context)
        val japanese = manager.selectionForLanguage(InputLanguage.JAPANESE)
        val english = manager.selectionForLanguage(InputLanguage.ENGLISH)
        assertEquals(SpeechModelCatalog.SENSEVOICE, japanese.mode)
        assertNull(japanese.first)
        assertEquals("ja", japanese.recognitionLanguage)
        assertEquals("en", english.recognitionLanguage)
        assertNotEquals(japanese.key, english.key)
        assertEquals(SpeechModelCatalog.ZIPFORMER_TWO_PASS, manager.getSelectedModelId())
        verify(prefs, never()).edit()
        File(sense, "tokens.txt").writeText("")
        assertThrows(IllegalStateException::class.java) { manager.selectionForLanguage(InputLanguage.JAPANESE) }
    }

    @Test fun incompatibleExplicitSelectionIsRejectedBeforeFileAccess() {
        val context = mock<Context>()
        assertThrows(IllegalArgumentException::class.java) {
            AsrModelManager(context).selection(SpeechModelCatalog.ZIPFORMER_TWO_PASS, InputLanguage.JAPANESE)
        }
        verifyNoInteractions(context)
    }
}
