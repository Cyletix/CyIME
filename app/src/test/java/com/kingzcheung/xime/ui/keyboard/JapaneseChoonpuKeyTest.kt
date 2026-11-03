package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.service.japaneseChoonpuIsCode
import com.kingzcheung.xime.settings.InputProfiles
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.viewmodel.ShiftMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** 日语 26 键长音键：默认 L 键就是长音；可选 L 右侧单独 ー 键或代替 Shift，ー 键送 - 进罗马字转换（ko-hi- → コーヒー）。 */
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

    @Test fun defaultKeyboardHasNoExtraKeyAndTheChoonpuKeyTypesHyphen() {
        KeysConfigHelper.setActiveKeyboardSchema("japanese")
        val rows = KeysConfigHelper.getKeyRows("japanese", false)
        // 默认 L 键就是长音，键盘和原来一样。
        assertEquals(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), rows[0])
        assertEquals(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), rows[1])
        assertEquals(listOf("z", "x", "c", "v", "b", "n", "m"), rows[2])
        assertEquals(JapaneseLongVowelKey.L_KEY, JapaneseLongVowelKey.fromName(null))
        assertEquals(JapaneseLongVowelKey.L_KEY, JapaneseLongVowelKey.fromName("坏值"))
        assertTrue(JapaneseLongVowelKey.L_KEY.lIsLongVowel)
        assertFalse(JapaneseLongVowelKey.SEPARATE.lIsLongVowel)
        assertFalse(JapaneseLongVowelKey.SHIFT.lIsLongVowel)
        // 单独的 ー 键：键面 ー，送出 -（方案字母表里的长音），不是直接上屏的 ー。
        assertEquals("ー", KeysConfigHelper.getKeyDisplayLabel(CHOONPU_KEY, false, false))
        assertEquals(CHOONPU_INPUT, KeysConfigHelper.getKeyCommitValue(CHOONPU_KEY, false))
        // m 键角标的 ー 照旧直接上屏，L 键角标的 - 也不变。
        assertEquals("ー", KeysConfigHelper.getSwipeUpCommitValue("m", false))
        assertEquals("-", KeysConfigHelper.getSwipeUpCommitValue("l", false))
    }

    @Test fun separateKeyGoesRightAfterL() {
        val rows = KeysConfigHelper.getKeyRows("japanese", false)
        val separate = withChoonpuKey(rows)
        assertEquals(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", CHOONPU_KEY), separate[1])
        assertEquals(rows[0], separate[0])
        assertEquals(rows[2], separate[2])
        assertEquals("只加一次", separate, withChoonpuKey(separate))
        // 自选排列里 l 在别处（Colemak 第一排）：跟在 l 后面。
        val colemak = listOf(listOf("q", "w", "f", "p", "g", "j", "l", "u", "y"), listOf("a", "r", "s", "t"), listOf("z"))
        assertEquals(listOf("q", "w", "f", "p", "g", "j", "l", CHOONPU_KEY, "u", "y"), withChoonpuKey(colemak)[0])
    }

    @Test fun choiceOnlyForJapaneseRomaji() {
        val japanese = InputProfiles.describe("japanese")
        for (mode in JapaneseLongVowelKey.entries) {
            assertEquals(mode, japaneseLongVowelKey(mode, false, japanese))
            assertNull(japaneseLongVowelKey(mode, true, japanese))
            assertNull(japaneseLongVowelKey(mode, false, InputProfiles.describe("japanese_kana")))
            assertNull(japaneseLongVowelKey(mode, false, InputProfiles.describe("rime_ice")))
        }
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
