package com.kingzcheung.xime.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

class ChineseSchemasTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(base.cacheDir, "chinese-schema-tests")
    private val context = object : ContextWrapper(base) {
        override fun getFilesDir() = directory
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("chinese_test_$name", mode)
    }
    @Before fun before() { File(directory, "rime").mkdirs(); SettingsPreferences.getPrefsPublic(context).edit().clear().commit() }
    @After fun after() { directory.deleteRecursively(); SettingsPreferences.getPrefsPublic(context).edit().clear().commit() }

    @Test fun migrationAddsOnlyMissingChineseModesOnceAndPreservesOrdering() {
        ChineseSchemas.installAssets(context, File(directory, "rime"))
        val old = listOf("my_custom", "japanese_kana")
        val updated = ChineseSchemas.addOnFirstUpgrade(context, old)
        assertEquals(old + ChineseSchemas.ids, updated)
        assertFalse(updated.contains("wubi86"))
        val disabledAgain = updated - "pinyin_14jian"
        assertEquals(disabledAgain, ChineseSchemas.addOnFirstUpgrade(context, disabledAgain))
    }
    @Test fun packagedSchemaHasClearNameAndDoesNotOverwriteMarketReplacement() {
        val target = File(directory, "rime")
        ChineseSchemas.installAssets(context, target)
        val file = File(target, "pinyin_14jian.schema.yaml")
        assertEquals("中文14键", SchemaManager.parseSchemaYaml(file)!!.name)
        val market = file.readText().replace("version: \"1.0\"", "version: \"market\"") + "\n# market replacement\n"
        file.writeText(market)
        ChineseSchemas.installAssets(context, target)
        assertEquals(market, file.readText())
        assertEquals("中文26键", ChineseSchemas.displayName("rime_ice", "雾凇拼音"))
        assertEquals("个人方案", ChineseSchemas.displayName("custom", "个人方案"))
    }
}
