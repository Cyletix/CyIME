package com.kingzcheung.xime.settings

import android.content.Context
import android.content.res.AssetManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class CyimeResearchPresetTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun asset(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)).readText()
    private val preset get() = asset("layout-presets/ga10-v4-english.custom.yaml")

    @Test fun `preset preserves all historical keys and limits override to English`() {
        val rows = KeysConfigHelper.parseKeyboardLayoutYamlText(preset, "qwerty_en")!!
        assertEquals(listOf("qwyrd;lkup", "asefghjiot", "zxcvbnm,./"), rows.map { it.joinToString("") })
        assertEquals(30, rows.flatten().toSet().size)
        assertNull(KeysConfigHelper.parseKeyboardLayoutYamlText(preset, "qwerty"))
        assertNull(KeysConfigHelper.parseKeyboardLayoutYamlText(preset, "japanese"))
        val keys = KeysConfigHelper.parseKeyboardYamlSection(preset, "qwerty_en")!!
        rows.flatten().forEach { assertEquals(it, keys[it]!!.tap!!.label) }
        rows[0].forEachIndexed { index, key -> assertEquals(((index + 1) % 10).toString(), keys[key]!!.swipeUp!!.label) }
    }

    @Test fun `install and rollback retain default Chinese and function keys`() {
        val filesDir = tmp.newFolder("files")
        val userDir = File(filesDir, "rime").apply { mkdirs() }
        val custom = File(userDir, "xime.custom.yaml")
        custom.writeText(preset)
        val defaults = asset("xime.yaml")
        val assets = mock<AssetManager>()
        whenever(assets.open("xime.yaml")).thenAnswer { ByteArrayInputStream(defaults.toByteArray()) }
        whenever(assets.open("xime.custom.yaml")).thenThrow(IOException("no default override"))
        val context = mock<Context>()
        whenever(context.assets).thenReturn(assets)
        whenever(context.filesDir).thenReturn(filesDir)
        KeysConfigHelper.loadConfig(context)
        assertEquals("qwyrd;lkup", KeysConfigHelper.getKeyRows(true)[0].joinToString(""))
        assertEquals("qwertyuiop", KeysConfigHelper.getKeyRows(false)[0].joinToString(""))
        assertEquals(";", KeysConfigHelper.getKeyGesture(";", true)!!.tap!!.label)
        assertNotNull(KeysConfigHelper.getKeyGesture("earth", true))
        assertTrue(custom.delete())
        KeysConfigHelper.loadConfig(context)
        assertEquals("qwertyuiop", KeysConfigHelper.getKeyRows(true)[0].joinToString(""))
    }
}
