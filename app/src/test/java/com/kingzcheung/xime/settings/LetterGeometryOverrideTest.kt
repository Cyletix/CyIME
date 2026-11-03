package com.kingzcheung.xime.settings

import com.kingzcheung.xime.ui.keyboard.firstRowCellDp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** xime.custom.yaml keyboard.<section>.geometry：第二行缩进、删除键份数、Shift 开关和份数。 */
class LetterGeometryOverrideTest {
    @After fun clear() {
        KeysConfigHelper.setSectionGeometryForTest(emptyMap())
        LetterLayouts.setForTest(InputLanguage.CHINESE, null)
        LetterLayouts.setForTest(InputLanguage.ENGLISH, null)
    }

    private val lxsq17 = """
        keyboard:
          qwerty_lxsq17:
            schemas: [double_pinyin_lxsq17]
            layout:
              rows:
                - [q, [w, e], [r, t], [y, u], i, [o, p]]
                - [a, [s, d], [f, g], h, [j, k], l, 横]
                - [z, [x, c], v, [b, n], m]
            geometry:
              middle_row_inset: 0
              delete_key_weight: 1.6
    """.trimIndent()

    @Test fun `读出两项参数`() {
        assertEquals(LetterGeometryOverride(0f, 1.6f),
            KeysConfigHelper.parseLetterGeometryYamlText(lxsq17, "qwerty_lxsq17"))
    }

    @Test fun `没写 geometry 的 section 照旧`() {
        assertNull(KeysConfigHelper.parseLetterGeometryYamlText(lxsq17, "qwerty"))
        val standard = """
            keyboard:
              qwerty:
                layout:
                  rows:
                    - [q, w, e, r, t, y, u, i, o, p]
                    - [a, s, d, f, g, h, j, k, l]
                    - [z, x, c, v, b, n, m]
        """.trimIndent()
        assertNull(KeysConfigHelper.parseLetterGeometryYamlText(standard, "qwerty"))
    }

    @Test fun `只写一项时另一项不动`() {
        val yaml = """
            keyboard:
              my17:
                geometry:
                  delete_key_weight: 2
        """.trimIndent()
        assertEquals(LetterGeometryOverride(null, 2f), KeysConfigHelper.parseLetterGeometryYamlText(yaml, "my17"))
    }

    @Test fun `超范围夹住，写错的项当没写`() {
        val yaml = """
            keyboard:
              my17:
                geometry:
                  middle_row_inset: 9
                  delete_key_weight: -1
        """.trimIndent()
        assertEquals(LetterGeometryOverride(LetterGeometryOverride.MAX_MIDDLE_ROW_INSET, LetterGeometryOverride.MIN_DELETE_KEY_WEIGHT),
            KeysConfigHelper.parseLetterGeometryYamlText(yaml, "my17"))
        val wrong = """
            keyboard:
              my17:
                geometry:
                  middle_row_inset: half
                  delete_key_weight: .nan
        """.trimIndent()
        assertNull(KeysConfigHelper.parseLetterGeometryYamlText(wrong, "my17"))
    }

    @Test fun `custom 写了的字段覆盖内置`() {
        val builtIn = LetterGeometryOverride(0.5f, 1.4f)
        assertEquals(LetterGeometryOverride(0f, 1.4f),
            KeysConfigHelper.mergeLetterGeometry(LetterGeometryOverride(0f, null), builtIn))
        assertEquals(builtIn, KeysConfigHelper.mergeLetterGeometry(null, builtIn))
        assertNull(KeysConfigHelper.mergeLetterGeometry(null, null))
    }

    @Test fun `缩进以第一行一个键宽为一格`() {
        // 17 键整套字母合法：按 10 列分格，第一行 6 个键各占一个键宽。
        assertEquals(100f / 6f, firstRowCellDp(10f, 10f, listOf("q", "we", "rt", "yu", "i", "op"), false), 1e-4f)
        // 加了笔画键、不是整套字母：列数就是第一行键数，一格等于格宽。
        assertEquals(40f, firstRowCellDp(40f, 6f, listOf("q", "we", "rt", "yu", "i", "op"), false), 1e-4f)
        // 14 键按字母数占格：一格是一个字母宽。
        assertEquals(30f, firstRowCellDp(30f, 10f, listOf("qw", "er", "ty", "ui", "op"), true), 1e-4f)
        assertEquals(30f, firstRowCellDp(30f, 10f, emptyList(), false), 0f)
    }

    @Test fun `Shift 可以关掉或调窄`() {
        val yaml = """
            keyboard:
              my17:
                geometry:
                  shift_key:
                    enabled: false
                    weight: 0.8
        """.trimIndent()
        assertEquals(LetterGeometryOverride(shiftKeyEnabled = false, shiftKeyWeight = 0.8f),
            KeysConfigHelper.parseLetterGeometryYamlText(yaml, "my17"))
        val short = """
            keyboard:
              my17:
                geometry:
                  shift_key: false
        """.trimIndent()
        assertEquals(LetterGeometryOverride(shiftKeyEnabled = false),
            KeysConfigHelper.parseLetterGeometryYamlText(short, "my17"))
        val narrow = """
            keyboard:
              my17:
                geometry:
                  shift_key: { weight: 0 }
        """.trimIndent()
        assertEquals(LetterGeometryOverride(shiftKeyWeight = LetterGeometryOverride.MIN_DELETE_KEY_WEIGHT),
            KeysConfigHelper.parseLetterGeometryYamlText(narrow, "my17"))
        val wrong = """
            keyboard:
              my17:
                geometry:
                  shift_key: { enabled: maybe }
        """.trimIndent()
        assertNull(KeysConfigHelper.parseLetterGeometryYamlText(wrong, "my17"))
    }

    @Test fun `Shift 两项也按字段合并`() {
        assertEquals(LetterGeometryOverride(0f, null, false, 1f), KeysConfigHelper.mergeLetterGeometry(
            LetterGeometryOverride(shiftKeyEnabled = false), LetterGeometryOverride(middleRowInset = 0f, shiftKeyWeight = 1f)))
    }

    @Test fun `用了应用内字母排列也保留参数`() {
        val zh = LetterGeometryOverride(middleRowInset = 0f, shiftKeyEnabled = false)
        val en = LetterGeometryOverride(deleteKeyWeight = 1.5f)
        KeysConfigHelper.setSectionGeometryForTest(mapOf("qwerty" to zh, "qwerty_en" to en))
        LetterLayouts.setForTest(InputLanguage.CHINESE, ColemakLayout.ID)
        assertNotNull(KeysConfigHelper.letterLayout("double_pinyin_flypy", false))
        assertEquals(zh, KeysConfigHelper.letterGeometryOverride("double_pinyin_flypy", false))
        LetterLayouts.setForTest(InputLanguage.ENGLISH, Cyletix10Layout.ID)
        assertNotNull(KeysConfigHelper.letterLayout("rime_ice", true))
        assertEquals(en, KeysConfigHelper.letterGeometryOverride("rime_ice", true))
    }

    @Test fun `没写参数时一律为空`() {
        KeysConfigHelper.setSectionGeometryForTest(emptyMap())
        assertNull(KeysConfigHelper.letterGeometryOverride("rime_ice", false))
        assertNull(KeysConfigHelper.letterGeometryOverride("rime_ice", true))
    }
}
