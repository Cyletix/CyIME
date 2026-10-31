package com.kingzcheung.xime.settings

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** 西班牙语：一种独立语言，字母像英文一样逐字上屏，键盘多一个 ñ，默认不启用，没有语音识别。 */
class SpanishInputTest {
    @get:Rule val temp = TemporaryFolder()

    private fun repoFile(rel: String) = listOf(File(rel), File("app/$rel")).first { it.isFile }
    private val yaml by lazy { repoFile("src/main/assets/xime.yaml").readText() }

    @After fun reset() {
        KeysConfigHelper.setActiveKeyboardSchema("")
        KeysConfigHelper.clearSchemaBindingsForTest()
    }

    @Test fun `spanish is its own language typed directly`() {
        val profile = InputProfiles.describe(SpanishSchemas.ID)
        assertEquals(InputLanguage.SPANISH, profile.language)
        assertEquals(InputScheme.DIRECT, profile.scheme)
        assertEquals(EngineProfile.Rime(SpanishSchemas.ID), profile.engineProfile)
        assertTrue(InputLanguage.SPANISH.typesLettersDirectly)
        assertTrue(InputLanguage.ENGLISH.typesLettersDirectly)
        assertFalse(InputLanguage.CHINESE.typesLettersDirectly)
        assertFalse(InputLanguage.JAPANESE.typesLettersDirectly)
        val layout = InputProfiles.resolveLayout("qwerty_spanish")
        assertEquals(LayoutKind.ALPHABETIC, layout.kind)
        assertEquals("26键", layout.displayName)
    }

    @Test fun `spanish is offered off by default and has no speech recognizer`() {
        assertTrue(InputLanguage.SPANISH in InputLanguage.supported)
        assertFalse(InputLanguage.SPANISH in InputLanguage.speech)
        val fresh = setOf(InputLanguage.CHINESE, InputLanguage.ENGLISH)
        assertEquals(fresh + InputLanguage.SPANISH, LanguagePreferences.withSchemaLanguage(fresh, SpanishSchemas.ID))
        assertFalse(SpanishSchemas.ID in CyimeInputDefaults.recommended)
    }

    @Test fun `spanish schema is bundled and only marks the language`() {
        val schema = repoFile("src/main/assets/rime_spanish/spanish.schema.yaml").readText()
        assertTrue(schema.contains("schema_id: spanish"))
        // 没有词典就不需要编译产物；字母不进 Rime。
        assertFalse(schema.contains("translator:"))
        assertFalse(schema.contains("speller"))
        assertTrue(repoFile("build-logic/tasks-rime-manifest.gradle.kts").readText()
            .contains("\"rime_spanish\" to file(\"src/main/assets/rime_spanish\")"))
        assertTrue("rime_spanish" in SchemaManifestManager.bundledAssetRoots)
    }

    @Test fun `spanish keyboard has an ñ key and accent long presses`() {
        assertEquals("qwerty_spanish", KeysConfigHelper.parseSchemaBindingsYamlText(yaml)[SpanishSchemas.ID])
        val rows = requireNotNull(KeysConfigHelper.parseKeyboardLayoutYamlText(yaml, "qwerty_spanish"))
        assertEquals(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), rows[0])
        assertEquals(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"), rows[1])
        assertEquals(listOf("z", "x", "c", "v", "b", "n", "m"), rows[2])
        val keys = requireNotNull(KeysConfigHelper.parseKeyboardYamlSection(yaml, "qwerty_spanish"))
        assertEquals("ñ", keys["ñ"]?.tap?.value)
        mapOf("a" to "á", "e" to "é", "i" to "í", "o" to "ó", "u" to "ú", "n" to "ñ").forEach { (key, accent) ->
            assertEquals(key, accent, keys[key]?.longPress?.values?.firstOrNull()?.label)
        }
        assertTrue(keys["u"]?.longPress?.values.orEmpty().any { it.label == "ü" })
        assertTrue(keys["'"]?.longPress?.values.orEmpty().map { it.label }.containsAll(listOf("¿", "¡")))
        // 字母键的角标不在 yaml 里配：和英文键盘同一规则。
        for (key in rows.flatten()) assertNull(key, keys[key]?.swipeUp)
    }

    @Test fun `spanish letters carry the english corner symbols and always offer accents`() {
        val assets = mock<android.content.res.AssetManager>()
        whenever(assets.open("xime.yaml")).thenAnswer { yaml.byteInputStream() }
        whenever(assets.open("xime.custom.yaml")).thenThrow(java.io.IOException("No custom override"))
        val context = mock<android.content.Context>()
        whenever(context.assets).thenReturn(assets)
        whenever(context.filesDir).thenReturn(temp.root)
        whenever(context.getSharedPreferences("kime_settings", android.content.Context.MODE_PRIVATE))
            .thenReturn(mock<android.content.SharedPreferences>())
        KeysConfigHelper.loadConfig(context)
        KeysConfigHelper.setActiveKeyboardSchema(SpanishSchemas.ID)
        assertEquals("ñ", KeysConfigHelper.getKeyRows(false)[1].last())
        for (key in QwertySwipeSymbols.keys.map(Char::toString)) {
            val gesture = KeysConfigHelper.getKeyGesture(key, false)
            assertEquals(key, QwertySwipeSymbols.up(true).getValue(key), gesture?.swipeUp?.value)
            assertEquals(key, QwertySwipeSymbols.down(true).getValue(key), gesture?.swipeDown?.value)
        }
        assertNull(KeysConfigHelper.getKeyGesture("ñ", false)?.swipeUp)
        assertTrue(KeysConfigHelper.accentLongPress(isAsciiMode = false))
        // 在西班牙语上切到英文：照英文键盘的规则。
        assertFalse(KeysConfigHelper.accentLongPress(isAsciiMode = true))
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
        assertFalse(KeysConfigHelper.accentLongPress(isAsciiMode = false))
    }
}
