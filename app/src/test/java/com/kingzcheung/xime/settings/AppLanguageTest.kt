package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun systemAndOtherTagsMapToTheFourLanguages() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromTag("zh-CN"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromTag("zh-Hans-SG"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromTag("zh"))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromTag("zh-TW"))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromTag("zh-HK"))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromTag("zh-Hant"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-GB"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromTag("ja-JP"))
        // 没有翻译的语言当作跟随系统，界面显示简体中文（values 默认）。
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("fr-FR"))
    }

    @Test fun eachChoiceRoundTripsThroughItsTag() {
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach { assertEquals(it, AppLanguage.fromTag(it.tag)) }
    }
}
