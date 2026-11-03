package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-11-02 用户手机：中文14键、自然码 14 键没编译出来，26 键能用，键盘却一直卡在「正在加载词库」。
 * 一个方案坏了只提示它；当前方案坏了换到同语言能用的方案，都坏了才算部署失败。
 */
class DeploymentFallbackTest {
    private val enabled = listOf("rime_ice", "t9_pinyin", "pinyin_14jian", "cyime_k14_english",
        "double_pinyin_ziranma", "double_pinyin_ziranma_14jian", "japanese")

    @Test
    fun `替代方案不选坏的和依赖方案`() {
        val broken = setOf("pinyin_14jian", "double_pinyin_ziranma_14jian")
        assertEquals("rime_ice", RimeConfigHelper.fallbackSchema("pinyin_14jian", enabled, broken))
    }

    @Test
    fun `同语言优先`() {
        val broken = setOf("rime_ice", "pinyin_14jian", "double_pinyin_ziranma_14jian")
        assertEquals("t9_pinyin", RimeConfigHelper.fallbackSchema("double_pinyin_ziranma_14jian", enabled, broken))
        assertEquals("t9_pinyin", RimeConfigHelper.fallbackSchema("double_pinyin_ziranma_14jian",
            listOf("japanese") + enabled.filter { it != "japanese" }, broken))
    }

    @Test
    fun `没有同语言的就用别的语言`() {
        val broken = setOf("rime_ice", "t9_pinyin", "pinyin_14jian", "double_pinyin_ziranma", "double_pinyin_ziranma_14jian")
        assertEquals("japanese", RimeConfigHelper.fallbackSchema("pinyin_14jian", enabled, broken))
    }

    @Test
    fun `只剩依赖方案时算部署失败`() {
        assertNull(RimeConfigHelper.fallbackSchema("pinyin_14jian", listOf("pinyin_14jian", "cyime_k14_english"), setOf("pinyin_14jian")))
    }

    /** 导入的方案常带自己的 symbols.yaml（没有 punctuator 段时引用它的方案整份编译失败）；内置方案只引用自带的标点表。 */
    @Test
    fun `内置方案不引用可被替换的 symbols_yaml`() {
        val dir = File("src/main/assets/rime_chinese")
        val schemas = dir.listFiles().orEmpty().filter { it.name.endsWith(".schema.yaml") }
        assertTrue(schemas.any { it.name == "pinyin_14jian.schema.yaml" })
        for (file in schemas) assertFalse(file.name, file.readText().contains("symbols:/"))
        val punctuator = File(dir, "cyime_punctuator.yaml").readText().replace("\r\n", "\n")
        assertTrue(punctuator.contains("\npunctuator:\n  full_shape:\n"))
        assertTrue(punctuator.contains("\n  half_shape:\n"))
        assertTrue(punctuator.contains("\n  symbols:\n"))
    }
}
