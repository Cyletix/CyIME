package com.kingzcheung.xime.handwriting

import android.content.Context
import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class HandwritingLanguagesTest {
    @Test fun unsupportedLanguageCannotLoadProbeOrInvokeTheChineseClassifier() {
        val context = mock<Context>()
        for (language in InputLanguage.entries.filter { it != InputLanguage.CHINESE }) {
            assertFalse(HandwritingLanguages.supports(language))
            assertFalse(HandwritingEngine.hasModel(context, language))
            assertFalse(HandwritingEngine.initialize(context, language))
            assertTrue(HandwritingEngine.predict(listOf(listOf(1f to 1f, 2f to 2f)), language = language).isEmpty())
        }
        verifyNoInteractions(context)
    }

    @Test fun missingMetadataIsDistinctFromAnUnsupportedKnownLanguage() {
        assertTrue(HandwritingLanguages.supports(InputLanguage.CHINESE))
        assertTrue(HandwritingLanguages.unavailableMessage(InputLanguage.UNSPECIFIED).contains("未标注语言"))
        assertTrue(HandwritingLanguages.unavailableMessage(InputLanguage.JAPANESE).contains("日语手写"))
        assertTrue(HandwritingLanguages.unavailableMessage(InputLanguage.ENGLISH).contains("英文手写"))
    }
}
