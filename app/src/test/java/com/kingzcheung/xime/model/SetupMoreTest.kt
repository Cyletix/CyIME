package com.kingzcheung.xime.model

import com.kingzcheung.xime.ui.keyboard.SpaceHoldAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「更多配置」：每题从现在的设置开始，只改点过的项；自己调过的字号、只开声音这类不在选项里的值照原样保留。 */
class SetupMoreTest {
    private val now = SetupMoreChoices(SpaceHoldAction.CURSOR, deleteSkipsAssociation = false, clearBeforeCursorOnly = false,
        textSize = TextSizeChoice.STANDARD, feedback = FeedbackChoice.SOUND_AND_VIBRATION, glass = false, autoPunctuation = false)

    @Test fun nothingTappedChangesNothing() {
        assertTrue(SetupMore.changes(now, now).isEmpty())
    }

    @Test fun onlyTappedItemsAreListed() {
        val changes = SetupMore.changes(now, now.copy(spaceHold = SpaceHoldAction.VOICE_TOGGLE, glass = true))
        assertEquals(listOf("长按空格改成「语音开关」。", "打开了「玻璃效果」。"), changes)
    }

    @Test fun defaultsMatchStandardSize() {
        // 新装：候选字 19、按键字号 120% 是「标准」，不会被当成自己调过。
        assertEquals(TextSizeChoice.STANDARD, SetupMore.textSizeOf(19, 1.2f))
        assertEquals(TextSizeChoice.LARGE, SetupMore.textSizeOf(22, 1.4f))
        assertEquals(TextSizeChoice.CUSTOM, SetupMore.textSizeOf(19, 1.0f))
        assertEquals(TextSizeChoice.CUSTOM, SetupMore.textSizeOf(24, 1.2f))
    }

    @Test fun customSizeIsKeptAndListed() {
        assertEquals(listOf(TextSizeChoice.STANDARD, TextSizeChoice.LARGE, TextSizeChoice.CUSTOM),
            SetupMore.textSizeChoices(TextSizeChoice.CUSTOM))
        assertEquals(listOf(TextSizeChoice.STANDARD, TextSizeChoice.LARGE), SetupMore.textSizeChoices(TextSizeChoice.LARGE))
        // 从「大字」改回「保持现在的」不算要写的改动。
        val custom = now.copy(textSize = TextSizeChoice.CUSTOM)
        assertTrue(SetupMore.changes(now.copy(textSize = TextSizeChoice.LARGE), custom).isEmpty())
    }

    @Test fun soundOnlyAppearsOnlyWhenCurrent() {
        assertEquals(FeedbackChoice.SOUND_ONLY, SetupMore.feedbackOf(sound = true, vibration = false))
        assertEquals(FeedbackChoice.NONE, SetupMore.feedbackOf(sound = false, vibration = false))
        assertTrue(FeedbackChoice.SOUND_ONLY !in SetupMore.feedbackChoices(FeedbackChoice.SOUND_AND_VIBRATION))
        assertTrue(FeedbackChoice.SOUND_ONLY in SetupMore.feedbackChoices(FeedbackChoice.SOUND_ONLY))
    }
}
