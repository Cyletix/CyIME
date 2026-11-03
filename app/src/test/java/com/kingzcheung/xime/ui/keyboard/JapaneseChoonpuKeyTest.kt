package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.service.japaneseChoonpuIsCode
import com.kingzcheung.xime.settings.InputProfiles
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.viewmodel.ShiftMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** 日语 26 键长音键：ー 送 - 进罗马字转换（ko-hi- → コーヒー），默认在 L 右侧，可换到 Shift 键上。 */
class JapaneseChoonpuKeyTest {
    @get:Rule val temp = TemporaryFolder()
    private val defaults get() = requireNotNull(javaClass.classLoader?.getResource("xime.yaml")).readText()

    @Before fun loadActualLayout() {
        val assets = mock<android.content.res.AssetManager>()
        whenever(assets.open("xime.yaml")).thenAnswer { defaults.byteInputStream() }
        whenever(assets.open("xime.custom.yaml")).thenThrow(java.io.IOException("No custom override"))
        val context = mock<android.content.Context>()
        whenever(context.assets).thenReturn(assets)
        whenever(context.filesDir).thenReturn(temp.root)
        whenever(context.getSharedPreferences("kime_settings", android.content.Context.MODE_PRIVATE))
            .thenReturn(mock<android.content.SharedPreferences>())
        KeysConfigHelper.loadConfig(context)
    }
    @After fun reset() { KeysConfigHelper.setActiveKeyboardSchema("") }

    @Test fun choonpuKeySitsRightOfLAndTypesHyphenIntoTheReading() {
        KeysConfigHelper.setActiveKeyboardSchema("japanese")
        val rows = KeysConfigHelper.getKeyRows("japanese", false)
        assertEquals(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", CHOONPU_KEY), rows[1])
        assertEquals(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), rows[0])
        assertEquals(listOf("z", "x", "c", "v", "b", "n", "m"), rows[2])
        // 键面 ー，送出 -（方案字母表里的长音），不是直接上屏的 ー。
        assertEquals("ー", KeysConfigHelper.getKeyDisplayLabel(CHOONPU_KEY, false, false))
        assertEquals(CHOONPU_INPUT, KeysConfigHelper.getKeyCommitValue(CHOONPU_KEY, false))
        // m 键角标的 ー 照旧直接上屏，L 键角标的 - 也不变。
        assertEquals("ー", KeysConfigHelper.getSwipeUpCommitValue("m", false))
        assertEquals("-", KeysConfigHelper.getSwipeUpCommitValue("l", false))
    }

    @Test fun otherKeyboardsDoNotGetTheChoonpuKey() {
        for (schema in listOf("rime_ice", "japanese_kana")) {
            KeysConfigHelper.setActiveKeyboardSchema(schema)
            assertFalse(schema, KeysConfigHelper.getKeyRows(schema, false).flatten().contains(CHOONPU_KEY))
        }
        KeysConfigHelper.setActiveKeyboardSchema("japanese")
        // 英数模式是英文键盘。
        assertFalse(KeysConfigHelper.getKeyRows("japanese", true).flatten().contains(CHOONPU_KEY))
    }

    @Test fun onShiftTheMiddleRowGoesBackToNineKeys() {
        val rows = KeysConfigHelper.getKeyRows("japanese", false)
        val moved = withoutChoonpuKey(rows)
        assertEquals(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), moved[1])
        assertEquals(rows[0], moved[0])
        assertEquals(rows[2], moved[2])
    }

    @Test fun onShiftOnlyForJapaneseRomaji() {
        val japanese = InputProfiles.describe("japanese")
        assertTrue(japaneseChoonpuOnShift(true, false, japanese))
        assertFalse(japaneseChoonpuOnShift(false, false, japanese))
        assertFalse(japaneseChoonpuOnShift(true, true, japanese))
        assertFalse(japaneseChoonpuOnShift(true, false, InputProfiles.describe("japanese_kana")))
        assertFalse(japaneseChoonpuOnShift(true, false, InputProfiles.describe("rime_ice")))
    }

    @Test fun hyphenIsReadingOnlyInJapaneseRomaji() {
        val japanese = InputProfiles.describe("japanese")
        assertTrue(japaneseChoonpuIsCode("-", false, japanese))
        // 英数模式、中文：- 仍是标点，按原来的路径上屏。
        assertFalse(japaneseChoonpuIsCode("-", true, japanese))
        assertFalse(japaneseChoonpuIsCode("-", false, InputProfiles.describe("rime_ice")))
        assertFalse(japaneseChoonpuIsCode("_", false, japanese))
        assertFalse(japaneseChoonpuIsCode("ー", false, japanese))
    }

    @Test fun longPressAloneTogglesLikeShiftTaps() {
        assertEquals("shift_single", shiftTapKeyLongPressAction(ShiftMode.OFF))
        assertEquals("shift_caps", shiftTapKeyLongPressAction(ShiftMode.SINGLE))
        assertEquals("shift_caps", shiftTapKeyLongPressAction(ShiftMode.CAPS))
    }
}
