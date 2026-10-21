package com.kingzcheung.xime.handwriting

import android.content.Context
import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class HandwritingLanguagesTest {
    /** 中文、英文、日语都由内置汉字手写模型服务，不需要公式模型；只有未标注语言的方案不能手写。 */
    @Test fun everyKeyboardLanguageHasARecognizerBackedByTheBuiltinModel() {
        for (language in listOf(InputLanguage.CHINESE, InputLanguage.ENGLISH, InputLanguage.JAPANESE)) {
            assertTrue(language.name, HandwritingLanguages.supports(language))
            assertEquals(language, HandwritingLanguages.recognitionLanguageForKeyboard(language))
        }
        assertFalse(HandwritingLanguages.supports(InputLanguage.UNSPECIFIED))
        assertNull(HandwritingLanguages.recognitionLanguageForKeyboard(InputLanguage.UNSPECIFIED))
    }

    @Test fun kanaTemplatesNeedOnlyTheBuiltinModelWhileLetterTemplatesNeedTheFormulaModel() {
        assertEquals(HandwritingTemplates.Script.KANA, HandwritingTemplates.Script.forLanguage(InputLanguage.JAPANESE))
        // 假名模板换成汉字模型特征后存新文件，旧的公式特征文件不再读
        assertEquals("kana_och.json", HandwritingTemplates.Script.KANA.fileName)
    }

    @Test fun englishCandidatesAreTheModelsLettersAndDigitsNeverHanCharacters() {
        val raw = listOf(HandwritingCandidate("丿", .5f), HandwritingCandidate("l", .2f), HandwritingCandidate("1", .1f),
            HandwritingCandidate("I", .15f), HandwritingCandidate("l", .05f), HandwritingCandidate("?", .3f), HandwritingCandidate("ab", .4f))
        assertEquals(listOf("l", "I", "1"), HandwritingLanguages.latinCandidates(raw, 8).map { it.char })
        assertEquals(listOf("l"), HandwritingLanguages.latinCandidates(raw, 1).map { it.char })
    }

    @Test fun unlabeledSchemeCannotLoadProbeOrInvokeTheClassifier() {
        val context = mock<Context>()
        val language = InputLanguage.UNSPECIFIED
        assertFalse(HandwritingEngine.hasModel(context, language))
        assertFalse(HandwritingEngine.initialize(context, language))
        assertTrue(HandwritingEngine.predict(listOf(listOf(1f to 1f, 2f to 2f)), language = language).isEmpty())
        verifyNoInteractions(context)
    }

    @Test fun missingMetadataIsDistinctFromAnUnsupportedKnownLanguage() {
        assertTrue(HandwritingLanguages.unavailableMessage(InputLanguage.UNSPECIFIED).contains("未标注语言"))
    }
}
