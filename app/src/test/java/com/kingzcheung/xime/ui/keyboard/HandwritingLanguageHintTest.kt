package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingLanguageHintTest {
    @Test fun thePadAlwaysNamesTheLanguageBeingWritten() {
        assertEquals("中文手写", handwritingLanguageHint(InputLanguage.CHINESE, useTemplates = false, preparing = false, missingKana = false))
        assertEquals("英文手写", handwritingLanguageHint(InputLanguage.ENGLISH, useTemplates = false, preparing = false, missingKana = false))
        assertEquals("英文手写 · 边写边学", handwritingLanguageHint(InputLanguage.ENGLISH, useTemplates = true, preparing = false, missingKana = false))
        assertEquals("日语手写 · 假名＋漢字", handwritingLanguageHint(InputLanguage.JAPANESE, useTemplates = true, preparing = false, missingKana = false))
        assertTrue(handwritingLanguageHint(InputLanguage.JAPANESE, useTemplates = false, preparing = true, missingKana = true).startsWith("日语手写"))
        assertTrue(handwritingLanguageHint(InputLanguage.JAPANESE, useTemplates = false, preparing = false, missingKana = true).startsWith("日语手写"))
    }
}
