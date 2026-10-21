package com.kingzcheung.xime.handwriting

import com.kingzcheung.xime.settings.IncognitoState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 手写“边写边学”与打字同一规则：无痕、密码框、声明不学习的输入框、剪贴板搜索时不学。 */
class HandwritingLearningPrivacyTest {
    @After fun reset() {
        IncognitoState.manual = false; IncognitoState.sensitiveEditor = false; IncognitoState.clipboardSearch = false
    }

    @Test fun handwritingPicksAreNotLearnedInIncognitoOrPasswordFields() {
        assertTrue(HandwritingTemplates.learningAllowed())
        IncognitoState.sensitiveEditor = true // 密码框或 IME_FLAG_NO_PERSONALIZED_LEARNING
        assertFalse(HandwritingTemplates.learningAllowed())
        reset(); IncognitoState.manual = true
        assertFalse(HandwritingTemplates.learningAllowed())
        reset(); IncognitoState.clipboardSearch = true
        assertFalse(HandwritingTemplates.learningAllowed())
    }
}
