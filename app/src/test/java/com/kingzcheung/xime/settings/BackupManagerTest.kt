package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManagerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    /** 恢复期间收到的 prefs 恢复调用（prefsName -> json 文本）。 */
    private val restoredPrefs = mutableMapOf<String, String>()

    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun zipOfText(entries: Map<String, String>): ByteArray =
        zipOf(entries.mapValues { it.value.toByteArray() })

    private fun restore(filesDir: File, bytes: ByteArray) =
        BackupRestoreTransaction.restore(filesDir, object : BackupPreferenceStore {
            override fun validate(json: ByteArray) { BackupManager.parsePrefs(json) }
            override fun snapshot(name: String) = (restoredPrefs[name] ?: "{}").toByteArray()
            override fun restore(name: String, json: ByteArray, replace: Boolean) {
                restoredPrefs[name] = json.toString(Charsets.UTF_8)
            }
        }, bytes)

    // ---- rime 条目（包根，兼容旧格式） ----

    @Test
    fun `restore unpacks rime entries into rime dir`() {
        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(
            mapOf(
                "default.yaml" to "config:",
                "opencc/ts.txt" to "opencc data"
            )
        )

        val result = restore(filesDir, bytes)

        assertTrue(result.isSuccess)
        assertEquals("config:", File(filesDir, "rime/default.yaml").readText())
        assertEquals("opencc data", File(filesDir, "rime/opencc/ts.txt").readText())
    }

    @Test
    fun `restore overwrites existing rime files`() {
        val filesDir = tempDir.newFolder("files")
        File(filesDir, "rime").mkdirs()
        File(filesDir, "rime/default.yaml").writeText("old:")
        val bytes = zipOfText(mapOf("default.yaml" to "new:"))

        val result = restore(filesDir, bytes)

        assertTrue(result.isSuccess)
        assertEquals("new:", File(filesDir, "rime/default.yaml").readText())
    }

    @Test
    fun `restore rejects path traversal entries`() {
        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(mapOf("../evil.txt" to "boom"))

        val result = restore(filesDir, bytes)

        assertTrue(result.isFailure)
        assertTrue(!File(filesDir.parentFile, "evil.txt").exists())
    }

    @Test
    fun `restore rejects absolute path entries`() {
        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(mapOf("/etc/evil.txt" to "boom"))

        val result = restore(filesDir, bytes)

        assertTrue(result.isFailure)
    }

    // ---- META 元数据条目 ----

    @Test
    fun `restore routes settings json to prefs restorer`() {
        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(
            mapOf("${BackupManager.META_PREFIX}settings.json" to """{"ascii_mode":{"t":"b","v":true}}""")
        )

        val result = restore(filesDir, bytes)

        assertTrue(result.isSuccess)
        assertEquals("kime_settings", restoredPrefs.keys.single())
        assertTrue(restoredPrefs["kime_settings"]!!.contains("ascii_mode"))
    }

    @Test
    fun `restore routes plugin config json to prefs restorer`() {
        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(
            mapOf(
                "${BackupManager.META_PREFIX}plugin_configs/com.example.plugin.json" to """{"url":{"t":"s","v":"secret"}}"""
            )
        )

        val result = restore(filesDir, bytes)

        assertTrue(result.isSuccess)
        assertEquals("plugin_cfg_com.example.plugin", restoredPrefs.keys.single())
    }

    @Test
    fun `restore rejects plugin config id with path separators`() {
        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(
            mapOf("${BackupManager.META_PREFIX}plugin_configs/../evil.json" to "{}")
        )

        val result = restore(filesDir, bytes)

        // Malformed metadata aborts the entire staged archive.
        assertTrue(result.isFailure)
        assertTrue(restoredPrefs.isEmpty())
        assertTrue(!File(filesDir, "evil.json").exists())
    }

    @Test
    fun `restore writes plugins xml and plugin packages`() {
        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(
            mapOf(
                "${BackupManager.META_PREFIX}plugins.xml" to "<plugins/>",
                "${BackupManager.META_PREFIX}plugins/com.example.plugin/main.lua" to "return {}",
                "${BackupManager.META_PREFIX}unknown.json" to "{}"
            )
        )

        val result = restore(filesDir, bytes)

        assertTrue(result.isSuccess)
        assertEquals("<plugins/>", File(filesDir, "plugins.xml").readText())
        assertEquals("return {}", File(filesDir, "plugins/com.example.plugin/main.lua").readText())
        // 未知元数据条目被跳过（向前兼容），且不落入 rime 目录
        assertTrue(!File(filesDir, "rime/unknown.json").exists())
    }

    @Test
    fun `restore rejects plugin package traversal`() {
        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(
            mapOf("${BackupManager.META_PREFIX}plugins/../evil.lua" to "boom")
        )

        val result = restore(filesDir, bytes)

        assertTrue(result.isFailure)
        assertTrue(!File(filesDir, "evil.lua").exists())
    }

    @Test
    fun `restore fails on empty archive`() {
        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(emptyMap())

        val result = restore(filesDir, bytes)

        assertTrue(result.isFailure)
    }

    @Test
    fun `late invalid path or settings never change an earlier existing file`() {
        for (tail in listOf("../escape" to "bad", BackupManager.META_PREFIX + "settings.json" to "{broken")) {
            val files = tempDir.newFolder()
            val original = File(files, "rime/default.yaml").apply { parentFile.mkdirs(); writeText("original") }
            val result = restore(files, zipOfText(linkedMapOf("default.yaml" to "replacement", tail)))
            assertTrue(result.isFailure)
            assertEquals("original", original.readText())
            assertTrue(restoredPrefs.isEmpty())
        }
    }

    @Test
    fun `failed preference commit rolls back earlier files and settings`() {
        val files = tempDir.newFolder()
        val original = File(files, "rime/default.yaml").apply { parentFile.mkdirs(); writeText("original") }
        val settings = mutableMapOf("kime_settings" to "old", "plugin_cfg_demo" to "secret")
        val store = object : BackupPreferenceStore {
            override fun validate(json: ByteArray) { BackupManager.parsePrefs(json) }
            override fun snapshot(name: String) = settings.getValue(name).toByteArray()
            override fun restore(name: String, json: ByteArray, replace: Boolean) {
                settings[name] = json.decodeToString()
                if (!replace && name == "plugin_cfg_demo") error("disk full")
            }
        }
        val result = BackupRestoreTransaction.restore(files, store, zipOfText(linkedMapOf(
            "default.yaml" to "replacement", "new.yaml" to "new",
            BackupManager.META_PREFIX + "settings.json" to "{}",
            BackupManager.META_PREFIX + "plugin_configs/demo.json" to "{}",
        )))
        assertTrue(result.isFailure)
        assertEquals("original", original.readText())
        assertTrue(!File(files, "rime/new.yaml").exists())
        assertEquals(mapOf("kime_settings" to "old", "plugin_cfg_demo" to "secret"), settings)
    }

    @Test
    fun `failed destination write rolls back preceding files`() {
        val files = tempDir.newFolder()
        val original = File(files, "rime/default.yaml").apply { parentFile.mkdirs(); writeText("original") }
        File(files, "rime/blocked").writeText("parent is a file")
        val result = restore(files, zipOfText(linkedMapOf("default.yaml" to "replacement", "blocked/child" to "new")))
        assertTrue(result.isFailure)
        assertEquals("original", original.readText())
        assertEquals("parent is a file", File(files, "rime/blocked").readText())
    }

    // ---- 本地备份（v2） ----

    private fun write(base: File, relative: String, text: String = relative) =
        File(base, relative).apply { parentFile!!.mkdirs(); writeText(text) }

    @Test
    fun `local backup keeps settings files and leaves personal data out by default`() {
        val files = tempDir.newFolder("files")
        listOf(
            "rime/default.custom.yaml", "rime/rime_ice.custom.yaml", "rime/custom_pinyin_1.schema.yaml",
            "rime/themes/bg.png", "rime/rime_ice.schema.yaml", "rime/rime_ice.dict.yaml",
            "rime/build/rime_ice.prism.bin", "rime/installation.yaml",
            "rime/rime_ice.userdb/CURRENT", "rime/rime_ice.userdb/LOCK", "rime/rime_ice.userdb/LOG",
            "rime/t9_digit.userdb/000001.log", "rime/rime_ice.userdb.txt",
            "rime/custom_phrase.txt", "rime/user_rime_ice.dict.yaml", "rime/user_cyime_cells_0123456789abcdef.dict.yaml",
            "custom-keyboard-layouts.json", "user_ngram_cache.json", "learning-profiles/abc.json",
            "plugins.xml", "models/x.bin", "logs/a.log",
            "handwriting_templates/latin.json", "handwriting_templates/kana_och.json",
            "handwriting_templates/kana_default_och_v2.json", "handwriting_training/samples.jsonl"
        ).forEach { write(files, it) }

        val plain = BackupManager.localBackupFiles(files, includePersonal = false).map { it.first }
        assertEquals(
            listOf(
                "${BackupManager.META_PREFIX}files/custom-keyboard-layouts.json",
                "custom_pinyin_1.schema.yaml", "default.custom.yaml", "rime_ice.custom.yaml", "themes/bg.png"
            ),
            plain
        )

        val personal = BackupManager.localBackupFiles(files, includePersonal = true).map { it.first }
        assertEquals(
            (plain + listOf(
                "${BackupManager.META_PREFIX}files/handwriting_templates/kana_och.json",
                "${BackupManager.META_PREFIX}files/handwriting_templates/latin.json",
                "${BackupManager.META_PREFIX}files/handwriting_training/samples.jsonl",
                "${BackupManager.META_PREFIX}files/learning-profiles/abc.json",
                "${BackupManager.META_PREFIX}files/user_ngram_cache.json",
                "custom_phrase.txt", "rime_ice.userdb.txt", "rime_ice.userdb/CURRENT",
                "t9_digit.userdb/000001.log", "user_rime_ice.dict.yaml"
            )).sorted(),
            personal
        )
    }

    @Test
    fun `restore routes extra prefs and files back to their places`() {
        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(
            mapOf(
                "${BackupManager.META_PREFIX}manifest.json" to """{"format":2}""",
                "${BackupManager.META_PREFIX}settings.json" to """{"dark_mode":{"t":"b","v":true}}""",
                "${BackupManager.META_PREFIX}prefs/caption_mode.json" to """{"font":{"t":"i","v":2}}""",
                "${BackupManager.META_PREFIX}files/custom-keyboard-layouts.json" to "[]",
                "${BackupManager.META_PREFIX}files/learning-profiles/abc.json" to "{}",
                "rime.userdb/CURRENT" to "MANIFEST-1"
            )
        )

        val result = restore(filesDir, bytes)

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals(setOf("kime_settings", "caption_mode"), restoredPrefs.keys)
        assertEquals("[]", File(filesDir, "custom-keyboard-layouts.json").readText())
        assertEquals("{}", File(filesDir, "learning-profiles/abc.json").readText())
        assertEquals("MANIFEST-1", File(filesDir, "rime/rime.userdb/CURRENT").readText())
    }

    @Test
    fun `clipboard card lines travel alone, the fixed records never do`() {
        val json = """{"card_max_lines":{"t":"i","v":8},"pins":{"t":"ss","v":["secret"]}}"""
        val kept = org.json.JSONObject(BackupManager.keepKeys(json, BackupManager.PARTIAL_PREFS.getValue("clipboard_board")))
        assertEquals(setOf("card_max_lines"), kept.keys().asSequence().toSet())
        assertEquals(8, kept.getJSONObject("card_max_lines").getInt("v"))

        val filesDir = tempDir.newFolder("files")
        val bytes = zipOfText(mapOf("${BackupManager.META_PREFIX}prefs/clipboard_board.json" to json))
        assertTrue(restore(filesDir, bytes).isSuccess)
        assertEquals(setOf("clipboard_board"), restoredPrefs.keys)
    }

    @Test
    fun `restore ignores unknown prefs and rejects unknown files entries`() {
        val filesDir = tempDir.newFolder("files")
        val unknownPrefs = zipOfText(
            mapOf("${BackupManager.META_PREFIX}prefs/windows_device_pairing.json" to """{"k":{"t":"s","v":"x"}}""")
        )
        assertTrue(restore(filesDir, unknownPrefs).isSuccess)
        assertTrue(restoredPrefs.isEmpty())

        val unknownFile = zipOfText(mapOf("${BackupManager.META_PREFIX}files/plugins.xml" to "<x/>"))
        assertTrue(restore(filesDir, unknownFile).isFailure)
        assertTrue(!File(filesDir, "plugins.xml").exists())
    }

    @Test
    fun `device state keys are stripped and only cyime archives are accepted`() {
        val stripped = org.json.JSONObject(
            BackupManager.stripDeviceState("""{"deployment_done":{"t":"b","v":true},"device_initial_cores":{"t":"i","v":8},"dark_mode":{"t":"b","v":true}}""")
        )
        assertEquals(setOf("dark_mode"), stripped.keys().asSequence().toSet())

        assertTrue(BackupManager.isCyimeBackup(zipOfText(mapOf("${BackupManager.META_PREFIX}settings.json" to "{}"))))
        assertTrue(!BackupManager.isCyimeBackup(zipOfText(mapOf("default.yaml" to "config:"))))
        assertTrue(!BackupManager.isCyimeBackup("not a zip".toByteArray()))
    }
}
