package com.kingzcheung.xime.settings

import androidx.compose.runtime.MutableState
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardKeyLabelCaseTest {
    @Suppress("UNCHECKED_CAST")
    private fun configState(name: String): MutableState<Map<String, KeyGestureConfig>> =
        KeysConfigHelper::class.java.getDeclaredField(name).let { field ->
            field.isAccessible = true
            field.get(KeysConfigHelper) as MutableState<Map<String, KeyGestureConfig>>
        }

    @Test
    fun `both input modes follow shift while preserving configured labels and merged keys`() {
        val assetText = """
            keyboard:
              qwerty:
                keys:
                  q: { tap: "q" }
                  qw: { tap: { label: "qw", value: "q" } }
                  r: { tap: { label: "拼音r", value: "r" } }
                  s: { tap: { label: "Shift↑", value: "s" } }
                  t: { tap: { label: "中A", value: "t" } }
              qwerty_en:
                keys:
                  q: { tap: "q" }
                  r: { tap: { label: "英r", value: "r" } }
        """.trimIndent()
        // Only replace the two key maps under test; schema/layout caches remain untouched.
        val chinese = configState("_keyGestureConfig")
        val english = configState("_keyGestureConfigEn")
        val previousChinese = chinese.value
        val previousEnglish = english.value
        try {
            chinese.value = requireNotNull(KeysConfigHelper.parseKeyboardYamlSection(assetText, "qwerty"))
            english.value = requireNotNull(KeysConfigHelper.parseKeyboardYamlSection(assetText, "qwerty_en"))
            for (ascii in listOf(false, true)) {
                assertEquals("q", KeysConfigHelper.getKeyDisplayLabel("q", ascii, false))
                assertEquals("Q", KeysConfigHelper.getKeyDisplayLabel("q", ascii, true))
                assertEquals("q", KeysConfigHelper.getKeyDisplayLabel("q", ascii, false))
                // No custom entry: the key identifier still follows the same casing rule.
                assertEquals("x", KeysConfigHelper.getKeyDisplayLabel("x", ascii, false))
                assertEquals("X", KeysConfigHelper.getKeyDisplayLabel("x", ascii, true))
            }
            assertEquals("qw", KeysConfigHelper.getKeyDisplayLabel("qw", false, false))
            assertEquals("QW", KeysConfigHelper.getKeyDisplayLabel("qw", false, true))
            assertEquals("q", KeysConfigHelper.getKeyCommitValue("qw", false))
            for (shifted in listOf(false, true)) {
                assertEquals("拼音r", KeysConfigHelper.getKeyDisplayLabel("r", false, shifted))
                assertEquals("英r", KeysConfigHelper.getKeyDisplayLabel("r", true, shifted))
                assertEquals("Shift↑", KeysConfigHelper.getKeyDisplayLabel("s", false, shifted))
                assertEquals("中A", KeysConfigHelper.getKeyDisplayLabel("t", false, shifted))
            }
        } finally {
            chinese.value = previousChinese
            english.value = previousEnglish
        }
    }
}
