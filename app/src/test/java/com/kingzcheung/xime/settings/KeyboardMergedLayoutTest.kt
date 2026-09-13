package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 合并键布局（14/17/18 键）测试：
 * 1. xime.yaml 嵌套 rows 解析（子数组 → 拼接 ID）
 * 2. 前端键位分组与 Rime 方案 xlit 映射串的一致性
 */
class KeyboardMergedLayoutTest {

    // ── 嵌套 rows 解析 ──

    @Test
    fun `扁平 rows 解析不受影响`() {
        val yaml = """
            keyboard:
              qwerty:
                layout:
                  rows:
                    - [q, w, e, r, t, y, u, i, o, p]
                    - [a, s, d, f, g, h, j, k, l]
                    - [z, x, c, v, b, n, m]
        """.trimIndent()
        val rows = KeysConfigHelper.parseKeyboardLayoutYamlText(yaml, "qwerty")
        assertEquals(
            listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m"),
            ),
            rows,
        )
    }

    @Test
    fun `嵌套子数组解析为拼接 ID`() {
        val yaml = """
            keyboard:
              qwerty_14:
                layout:
                  rows:
                    - [[q, w], [e, r], [t, y], [u, i], [o, p]]
                    - [[a, s], [d, f], [g, h], [j, k], [l]]
                    - [[z, x], [c, v], [b, n], [m]]
        """.trimIndent()
        val rows = KeysConfigHelper.parseKeyboardLayoutYamlText(yaml, "qwerty_14")
        assertEquals(
            listOf(
                listOf("qw", "er", "ty", "ui", "op"),
                listOf("as", "df", "gh", "jk", "l"),
                listOf("zx", "cv", "bn", "m"),
            ),
            rows,
        )
    }

    @Test
    fun `单字母与子数组混排行解析`() {
        val yaml = """
            keyboard:
              qwerty_17:
                layout:
                  rows:
                    - [[q, w], [e, r], t, y, [u, i], [o, p]]
                    - z, [x, c], v, [b, n], m
        """.trimIndent()
        val rows = KeysConfigHelper.parseKeyboardLayoutYamlText(yaml, "qwerty_17")
        assertEquals(
            listOf(
                listOf("qw", "er", "t", "y", "ui", "op"),
                listOf("z", "xc", "v", "bn", "m"),
            ),
            rows,
        )
    }

    @Test
    fun `section 缺失返回 null`() {
        assertNull(KeysConfigHelper.parseKeyboardLayoutYamlText("keyboard:\n  t9:\n    side_symbols: []", "qwerty_14"))
    }

    // ── 方案 → section 映射 ──

    @Test
    fun `合并键方案映射到对应 section`() {
        assertEquals("qwerty_14", KeysConfigHelper.mergedSectionForSchema("pinyin_14jian"))
        assertEquals("qwerty_17", KeysConfigHelper.mergedSectionForSchema("pinyin_17jian"))
        assertEquals("qwerty_18", KeysConfigHelper.mergedSectionForSchema("pinyin_18jian"))
    }

    @Test
    fun `非合并键方案返回 null`() {
        assertNull(KeysConfigHelper.mergedSectionForSchema("pinyin_simp"))
        assertNull(KeysConfigHelper.mergedSectionForSchema("t9_pinyin"))
        assertNull(KeysConfigHelper.mergedSectionForSchema("wubi86"))
        assertNull(KeysConfigHelper.mergedSectionForSchema(""))
    }

    // ── 前端分组 ↔ Rime xlit 映射一致性 ──

    /** 定位模块内 assets 文件（单测 workingDir 通常为模块目录，兜底仓库根目录）。 */
    private fun assetFile(rel: String): File =
        listOf(File(rel), File("app/$rel")).firstOrNull { it.exists() }
            ?: error("asset file not found: $rel")

    private fun ximeYamlText(): String =
        assetFile("src/main/assets/xime.yaml").readText()

    /** 从合并键方案 schema 提取 xlit 映射：字母 → 代表字母。 */
    private fun xlitMap(schemaText: String): Map<Char, Char> {
        val xlit = Regex("""xlit/([A-Z]{26})/([a-z]{26})/""").find(schemaText)
            ?: error("xlit missing in schema")
        val (source, target) = xlit.destructured
        assertEquals("xlit 源串应为 26 个大写字母", "QWERTYUIOPASDFGHJKLZXCVBNM", source)
        assertEquals("xlit 目标串长度应为 26", 26, target.length)
        return source.zip(target).toMap().mapKeys { it.key.lowercaseChar() }
    }

    /** 从行布局推导合并分组：每个字母 → 所在组 ID 的首字母（代表字母）。 */
    private fun representativeMap(rows: List<List<String>>): Map<Char, Char> =
        rows.flatten().flatMap { id -> id.map { it to id.first() } }.toMap()

    @Test
    fun `14键前端分组与 Rime xlit 映射一致`() {
        val rows = KeysConfigHelper.parseKeyboardLayoutYamlText(ximeYamlText(), "qwerty_14")
            ?: error("xime.yaml 缺少 qwerty_14 rows")
        assertEquals(14, rows.flatten().size)
        val schemaText = assetFile("src/main/assets/rime/pinyin_14jian.schema.yaml").readText()
        assertEquals(representativeMap(rows), xlitMap(schemaText))
    }

    @Test
    fun `17键前端分组与 Rime xlit 映射一致`() {
        val rows = KeysConfigHelper.parseKeyboardLayoutYamlText(ximeYamlText(), "qwerty_17")
            ?: error("xime.yaml 缺少 qwerty_17 rows")
        assertEquals(17, rows.flatten().size)
        val schemaText = assetFile("src/main/assets/rime/pinyin_17jian.schema.yaml").readText()
        assertEquals(representativeMap(rows), xlitMap(schemaText))
    }

    @Test
    fun `18键前端分组与 Rime xlit 映射一致`() {
        val rows = KeysConfigHelper.parseKeyboardLayoutYamlText(ximeYamlText(), "qwerty_18")
            ?: error("xime.yaml 缺少 qwerty_18 rows")
        assertEquals(18, rows.flatten().size)
        val schemaText = assetFile("src/main/assets/rime/pinyin_18jian.schema.yaml").readText()
        assertEquals(representativeMap(rows), xlitMap(schemaText))
    }

    @Test
    fun `三个方案的 xlit 映射串与万象参考一致`() {
        assertTrue(
            assetFile("src/main/assets/rime/pinyin_14jian.schema.yaml").readText()
                .contains("xlit/QWERTYUIOPASDFGHJKLZXCVBNM/qqeettuuooaaddggjjlzzccbbm"),
        )
        assertTrue(
            assetFile("src/main/assets/rime/pinyin_17jian.schema.yaml").readText()
                .contains("xlit/QWERTYUIOPASDFGHJKLZXCVBNM/qwwrryyiooassffhjjlzxxvbbm"),
        )
        assertTrue(
            assetFile("src/main/assets/rime/pinyin_18jian.schema.yaml").readText()
                .contains("xlit/QWERTYUIOPASDFGHJKLZXCVBNM/qwwrryuiipassffhjjlzxxvbbm"),
        )
    }

    // ── 手势配置完整性 ──

    @Test
    fun `三个布局每个字母键都有 swipe_up`() {
        for (section in listOf("qwerty_14", "qwerty_17", "qwerty_18")) {
            val yaml = ximeYamlText()
            val rows = KeysConfigHelper.parseKeyboardLayoutYamlText(yaml, section)
                ?: error("xime.yaml 缺少 $section rows")
            val gestures = KeysConfigHelper.parseKeyboardYamlSection(yaml, section)
                ?: error("xime.yaml 缺少 $section keys")
            for (key in rows.flatten()) {
                val swipeUp = gestures[key.lowercase()]?.swipeUp
                assertTrue(
                    "$section 的按键 $key 缺少 swipe_up 配置",
                    swipeUp != null && (swipeUp.label.isNotEmpty() || swipeUp.value.isNotEmpty()),
                )
            }
        }
    }
}
