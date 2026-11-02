package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingLanguageHintTest {
    /** 平时只写语言名，胶囊小（用户 11-02「日语两个字就行」）；准备中、缺假名模板才多写一句。 */
    @Test fun thePadAlwaysNamesTheLanguageBeingWritten() {
        assertEquals("中文", handwritingLanguageHint(InputLanguage.CHINESE, useTemplates = false, preparing = false, missingKana = false))
        assertEquals("英文", handwritingLanguageHint(InputLanguage.ENGLISH, useTemplates = false, preparing = false, missingKana = false))
        assertEquals("英文", handwritingLanguageHint(InputLanguage.ENGLISH, useTemplates = true, preparing = false, missingKana = false))
        assertEquals("日语", handwritingLanguageHint(InputLanguage.JAPANESE, useTemplates = true, preparing = false, missingKana = false))
        assertTrue(handwritingLanguageHint(InputLanguage.JAPANESE, useTemplates = false, preparing = true, missingKana = true).startsWith("日语 · 正在准备"))
        assertTrue(handwritingLanguageHint(InputLanguage.JAPANESE, useTemplates = false, preparing = false, missingKana = true).startsWith("日语 · 假名模板没读到"))
    }
}
