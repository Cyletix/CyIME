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

class QwjrtkLayoutTest {
    @get:Rule val tmp = TemporaryFolder()
    private val base = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").map { row -> row.map { it.toString() } }

    @Test fun onlySixLettersMove() {
        val rows = QwjrtkLayout.rows(base)
        assertEquals(listOf("qwjrtkuiop", "asdfghenl", "zxcybvm"), rows.map { it.joinToString("") })
        assertEquals(base.flatten().toSet(), rows.flatten().toSet())
        assertEquals(20, base.flatten().zip(rows.flatten()).count { (a, b) -> a == b })
        assertEquals("qwertyuiop", base[0].joinToString(""))
    }

    @Test fun explicitlyEnabledSampleRespectsChosenOrder() {
        val existing = listOf("t9_pinyin", "rime_ice", "double_pinyin_flypy")
        val modes = (listOf(QwjrtkLayout.ID) + existing).map { SchemaInfo(it, it, "", "", "") }
        val chosenOrder = listOf(QwjrtkLayout.ID) + existing
        val userSorted = InputModes.available(modes, chosenOrder)
        assertEquals(chosenOrder, InputModes.inCurrentLanguage(userSorted, "rime_ice").map { it.schemaId })
        val ordered = InputModes.available(modes, existing.reversed())
        assertEquals(existing.reversed() + QwjrtkLayout.ID,
            InputModes.inCurrentLanguage(ordered, "rime_ice").map { it.schemaId })
    }

    @Test fun switchingAndReloadingKeepFunctionKeysAndDigitsAndRestoreQwerty() {
        val defaults = requireNotNull(javaClass.classLoader?.getResource("xime.yaml")).readText()
        val assets = mock<AssetManager>()
        whenever(assets.open("xime.yaml")).thenAnswer { ByteArrayInputStream(defaults.toByteArray()) }
        whenever(assets.open("xime.custom.yaml")).thenThrow(IOException("no override"))
        val context = mock<Context>()
        whenever(context.assets).thenReturn(assets)
        whenever(context.filesDir).thenReturn(tmp.newFolder("files"))
        whenever(context.getSharedPreferences("kime_settings", Context.MODE_PRIVATE))
            .thenReturn(mock<android.content.SharedPreferences>())
        try {
            KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
            KeysConfigHelper.loadConfig(context)
            val english = KeysConfigHelper.getKeyRows(true)
            val earth = KeysConfigHelper.getKeyGesture("earth", false)
            val letter = KeysConfigHelper.getKeyGesture("j", false)!!
            val digit = KeysConfigHelper.getKeyGesture("e", false)!!.swipeUp
            KeysConfigHelper.setActiveKeyboardSchema(QwjrtkLayout.ID)
            assertEquals(QwjrtkLayout.rows(base), KeysConfigHelper.getKeyRows(false))
            assertEquals(english, KeysConfigHelper.getKeyRows(true))
            assertEquals(earth, KeysConfigHelper.getKeyGesture("earth", false))
            assertEquals(letter.tap, KeysConfigHelper.getKeyGesture("j", false)!!.tap)
            assertEquals(letter.longPress, KeysConfigHelper.getKeyGesture("j", false)!!.longPress)
            assertEquals(digit, KeysConfigHelper.getKeyGesture("j", false)!!.swipeUp)
            KeysConfigHelper.loadConfig(context)
            assertEquals(QwjrtkLayout.rows(base), KeysConfigHelper.getKeyRows(false))
            KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
            assertEquals(base, KeysConfigHelper.getKeyRows(false))
            assertEquals(letter, KeysConfigHelper.getKeyGesture("j", false))
        } finally {
            KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
        }
    }

    @Test fun engineConfigurationDiffersOnlyInIdentity() {
        val root = File("build/generated/chinese-assets/rime_ice")
        val source = File(root, "rime_ice.schema.yaml").readText()
        val preset = File(root, "pinyin_qwjrtk.schema.yaml").readText()
        assertEquals(source.replace("schema_id: rime_ice", "schema_id: pinyin_qwjrtk")
            .replace("name: 雾凇拼音", "name: QWJRTK（双拇指）"), preset)
    }
}
