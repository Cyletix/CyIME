package com.kingzcheung.xime.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 在独立目录中以真实 APK 雾凇资源模拟官方 ZIP 下载包，走完整 Android 文件安装链路。
 * 不初始化/切换 RimeEngine，也不读写正在使用的输入法配置或学习数据。
 */
@RunWith(AndroidJUnit4::class)
class MarketPackageCompatibilityTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = "market_compatibility_${UUID.randomUUID()}"
    private val directory = File(base.cacheDir, token)
    private val preferenceNames = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
        override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            val isolatedName = "${token}_$name"
            preferenceNames.add(isolatedName)
            return base.getSharedPreferences(isolatedName, mode)
        }
    }
    private val packageId = "rime-ice"
    private val rime get() = SchemaManager.getRimeDir(context)
    private fun asset(path: String) = base.assets.open(path).use { it.readBytes() }
    private fun write(relative: String, bytes: ByteArray): File = File(rime, relative).apply {
        parentFile!!.mkdirs()
        writeBytes(bytes)
    }
    private fun hash(relative: String) = SchemaManager.fileSha256(File(rime, relative))

    @Before fun prepare() {
        rime.mkdirs()
        // 本测试模拟用户已配置的两种日语模式，关闭一次性迁移，避免测试依赖 UI 或部署。
        SettingsPreferences.setBuiltinSchemasMerged(context, true)
        SettingsPreferences.getPrefsPublic(context).edit()
            .putBoolean("japanese_schemas_added_v1", true)
            .putBoolean("cyime_chinese_defaults_v1", true)
            .commit()
    }

    @After fun cleanUp() {
        preferenceNames.forEach { base.deleteSharedPreferences(it) }
        directory.deleteRecursively()
    }

    @Test fun realRimeIceZipUpdatesOnlyConflictingFilesAndUninstallRestoresBuiltin() = runBlocking {
        val marketFiles = linkedMapOf(
            "rime_ice.schema.yaml" to asset("rime_ice/rime_ice.schema.yaml"),
            "rime_ice.dict.yaml" to asset("rime_ice/rime_ice.dict.yaml"),
            "lua/t9_preedit.lua" to asset("rime_ice/lua/t9_preedit.lua"),
            "cn_dicts/others.dict.yaml" to asset("rime_ice/cn_dicts/others.dict.yaml"),
            "opencc/emoji.json" to asset("rime_ice/opencc/emoji.json"),
            "opencc/emoji.txt" to asset("rime_ice/opencc/emoji.txt"),
        )
        // 旧版仍是合法的真实雾凇配置，仅附加注释以确定产生两个精确的不同内容冲突。
        val oldSchema = marketFiles.getValue("rime_ice.schema.yaml") + "\n# previous builtin revision\n".toByteArray()
        val oldLua = marketFiles.getValue("lua/t9_preedit.lua") + "\n-- previous builtin revision\n".toByteArray()
        write("rime_ice.schema.yaml", oldSchema)
        write("lua/t9_preedit.lua", oldLua)
        write("cn_dicts/others.dict.yaml", marketFiles.getValue("cn_dicts/others.dict.yaml"))
        val japaneseIds = listOf("japanese", "japanese_kana")
        val japaneseBytes = japaneseIds.associateWith { asset("rime_japanese/$it.schema.yaml") }
        japaneseBytes.forEach { (id, bytes) -> write("$id.schema.yaml", bytes) }
        assertTrue(SchemaManifestManager.createManifest(context, "builtin", "系统内置方案", fromMarket = false,
            extractedFiles = listOf("rime_ice.schema.yaml", "lua/t9_preedit.lua", "cn_dicts/others.dict.yaml") +
                japaneseIds.map { "$it.schema.yaml" }))

        val personalPatch = "patch:\n  translator/enable_user_dict: true\n  menu/page_size: 11\n".toByteArray()
        val learningData = byteArrayOf(0, 4, 25, 66, 127, -1, 10)
        val customPhrase = "个人短语\tgeren\t100\n".toByteArray()
        write("rime_ice.custom.yaml", personalPatch)
        write("rime_ice.userdb/000003.log", learningData)
        write("custom_phrase.txt", customPhrase)
        SchemaManager.setEnabledSchemas(context, japaneseIds)
        SettingsPreferences.setCurrentSchema(context, "japanese_kana")
        assertEquals(japaneseIds, SchemaManager.getEnabledSchemas(context))

        val archiveDirectory = SchemaManager.getMarketDir(context, packageId).apply { mkdirs() }
        val archive = File(archiveDirectory, "rime-ice-market-fixture.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            // 与 GitHub 市场源一致，含顶层包目录和嵌套 lua/cn_dicts/opencc 路径。
            val fixture = marketFiles + mapOf(
                "rime_ice.custom.yaml" to "patch: {}\n".toByteArray(),
                "rime_ice.userdb/000003.log" to "not user data".toByteArray(),
                "custom_phrase.txt" to "not personal phrases".toByteArray(),
            )
            fixture.forEach { (relative, bytes) ->
                zip.putNextEntry(ZipEntry("rime-ice-9e66b072/$relative"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }

        val rejected = SchemaManager.installPackageFromMarketDir(context, packageId, "雾凇拼音", fromMarket = true)
        assertFalse(rejected.success)
        assertEquals(setOf("rime_ice.schema.yaml", "lua/t9_preedit.lua"), rejected.conflicts.map { it.fileName }.toSet())
        assertArrayEquals(oldSchema, File(rime, "rime_ice.schema.yaml").readBytes())
        assertEquals(japaneseIds, SchemaManager.getEnabledSchemas(context))
        assertFalse(File(rime, "opencc/emoji.json").exists())

        val installed = SchemaManager.installPackageFromMarketDir(context, packageId, "雾凇拼音", fromMarket = true,
            replaceConflictingFiles = true)
        assertTrue(installed.failureReason, installed.success)
        assertTrue(installed.parseFailures.toString(), installed.parseFailures.isEmpty())
        marketFiles.forEach { (relative, bytes) ->
            assertEquals(relative, SchemaManager.sha256Hex(bytes), hash(relative))
        }
        val registeredFiles = SchemaManifestManager.loadRegistry(context).getJSONObject("files")
        assertEquals(setOf("builtin", packageId), SchemaManifestManager.jsonArrayToList(
            registeredFiles.getJSONObject("cn_dicts/others.dict.yaml").getJSONArray("claimedBy")).toSet())
        assertTrue(SchemaManager.getEnabledSchemas(context).containsAll(japaneseIds))
        assertEquals("japanese_kana", SettingsPreferences.getCurrentSchema(context))
        japaneseBytes.forEach { (id, bytes) -> assertArrayEquals(bytes, File(rime, "$id.schema.yaml").readBytes()) }
        assertArrayEquals(personalPatch, File(rime, "rime_ice.custom.yaml").readBytes())
        assertArrayEquals(learningData, File(rime, "rime_ice.userdb/000003.log").readBytes())
        assertArrayEquals(customPhrase, File(rime, "custom_phrase.txt").readBytes())

        val removed = SchemaManifestManager.uninstallWithManifest(context, packageId)
        assertTrue(removed.message, removed.success)
        assertArrayEquals(oldSchema, File(rime, "rime_ice.schema.yaml").readBytes())
        assertArrayEquals(oldLua, File(rime, "lua/t9_preedit.lua").readBytes())
        assertEquals(SchemaManager.sha256Hex(marketFiles.getValue("cn_dicts/others.dict.yaml")), hash("cn_dicts/others.dict.yaml"))
        assertFalse(File(rime, "opencc/emoji.json").exists())
        assertNotNull(SchemaManifestManager.loadManifest(context, "builtin"))
        assertNull(SchemaManifestManager.loadManifest(context, packageId))
        assertTrue(SchemaManager.getEnabledSchemas(context).containsAll(japaneseIds))
        assertEquals("japanese_kana", SettingsPreferences.getCurrentSchema(context))
        japaneseBytes.forEach { (id, bytes) -> assertArrayEquals(bytes, File(rime, "$id.schema.yaml").readBytes()) }
        assertArrayEquals(personalPatch, File(rime, "rime_ice.custom.yaml").readBytes())
        assertArrayEquals(learningData, File(rime, "rime_ice.userdb/000003.log").readBytes())
        assertArrayEquals(customPhrase, File(rime, "custom_phrase.txt").readBytes())
        assertTrue("下载包保留，便于重装", archive.exists())
    }
    @Test fun explicitEmptyModesRepairOneNativeFallbackAndPreservePersonalPatches() {
        write("japanese.schema.yaml", asset("rime_japanese/japanese.schema.yaml"))
        write("japanese_kana.schema.yaml", asset("rime_japanese/japanese_kana.schema.yaml"))
        val custom = write("default.custom.yaml", "patch:\n  schema_list: []\n  menu/page_size: 11\n".toByteArray())
        val expected = listOf("japanese")
        assertEquals(expected, SchemaManager.getEnabledSchemas(context))
        assertEquals(expected, SchemaManager.getEnabledSchemas(context))
        assertEquals(expected, SchemaManager.readEnabledSchemaList(custom.readText()))
        assertTrue(custom.readText().contains("menu/page_size: 11"))
        assertFalse(custom.readText().contains("t9_pinyin"))
    }

    @Test fun successfulUninstallRemovesOnlyTrulyDeletedModesAndKeepsDownload() = runBlocking {
        write("japanese.schema.yaml", asset("rime_japanese/japanese.schema.yaml"))
        write("market_only.schema.yaml", "schema:\n  schema_id: market_only\n  name: Market only\n".toByteArray())
        assertTrue(SchemaManifestManager.createManifest(context, "market-only", "Market only",
            extractedFiles = listOf("market_only.schema.yaml")))
        val archive = File(SchemaManager.getMarketDir(context, "market-only"), "package.zip").apply {
            parentFile!!.mkdirs(); writeText("keep downloaded archive")
        }
        SchemaManager.setEnabledSchemas(context, listOf("japanese", "market_only"))
        SettingsPreferences.setCurrentSchema(context, "market_only")
        SettingsPreferences.addInstalledMarketId(context, "market-only")
        val result = SchemaManager.uninstallPackage(context, "market-only")
        assertTrue(result.message, result.success)
        assertEquals(listOf("japanese"), SchemaManager.getEnabledSchemas(context))
        assertEquals("japanese", SettingsPreferences.getCurrentSchema(context))
        assertFalse(SettingsPreferences.getInstalledMarketIds(context).contains("market-only"))
        assertTrue(archive.exists())
        assertTrue(File(rime, "japanese.schema.yaml").exists())
    }

}
