package com.kingzcheung.xime.settings

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

class ChineseAssetInstallTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(base.cacheDir, "chinese-asset-install-tests")
    private val context = object : ContextWrapper(base) {
        override fun getFilesDir() = directory
    }
    private val target get() = File(directory, "rime")
    @Before fun before() { target.mkdirs() }
    @After fun after() { directory.deleteRecursively() }
    private fun old14() = context.assets.open("rime_chinese/pinyin_14jian.schema.yaml").bufferedReader().use { it.readText() }
        .replace("dictionary: rime_ice", "dictionary: pinyin_simp")

    @Test fun unmodifiedLegacy14MovesToSharedDictionaryWhileMissingResourcesInstall() {
        val schema = File(target, "pinyin_14jian.schema.yaml")
        schema.writeText(old14())
        ChineseSchemas.installAssets(context, target)
        assertTrue(schema.readText().contains("dictionary: rime_ice"))
        assertTrue(File(target, "cn_dicts/tencent.dict.yaml").length() > 10_000_000)
    }

    @Test fun marketOwnershipProtectsEvenAnExactCopyOfTheOldBuiltinSchema() {
        val schema = File(target, "pinyin_14jian.schema.yaml")
        val content = old14()
        schema.writeText(content)
        SchemaManifestManager.getRegistryFile(context).writeText(
            """{"version":1,"files":{"pinyin_14jian.schema.yaml":{"claimedBy":["market-14"]}}}"""
        )
        ChineseSchemas.installAssets(context, target)
        assertEquals(content, schema.readText())
    }

    @Test fun repeatedInstallRetainsMarketDictionaryAndRepairsOnlyMissingResources() {
        ChineseSchemas.installAssets(context, target)
        val dictionary = File(target, "cn_dicts/tencent.dict.yaml")
        val market = "# personal dictionary replacement\n"
        dictionary.writeText(market)
        val schema = File(target, "pinyin_14jian.schema.yaml")
        assertTrue(schema.delete())
        ChineseSchemas.installAssets(context, target)
        assertEquals(market, dictionary.readText())
        assertTrue(schema.readText().contains("dictionary: rime_ice"))
    }
}
