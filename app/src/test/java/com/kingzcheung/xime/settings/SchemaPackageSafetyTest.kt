package com.kingzcheung.xime.settings

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

class SchemaPackageSafetyTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var rime: File

    @Before fun prepare() {
        val directory = temporary.newFolder("files")
        val assets: AssetManager = mock()
        val cache = temporary.newFolder("cache")
        context = mock { on { filesDir } doReturn directory; on { cacheDir } doReturn cache; on { getAssets() } doReturn assets }
        rime = File(directory, "rime").apply { mkdirs() }
    }
    private fun write(name: String, text: String): File = File(rime, name).apply { parentFile!!.mkdirs(); writeText(text) }
    private fun own(id: String, vararg names: String) = runBlocking {
        assertTrue(SchemaManifestManager.createManifest(context, id, id, extractedFiles = names.toList()))
    }
    private fun install(id: String, vararg files: Pair<String, String>) = runBlocking {
        val staged = temporary.newFolder()
        files.forEach { (name, text) -> File(staged, name).apply { parentFile!!.mkdirs(); writeText(text) } }
        SchemaManifestManager.installStagedFiles(context, id, id, "1", true, staged, files.map { it.first })
    }
    private fun uninstall(id: String) = runBlocking { SchemaManifestManager.uninstallWithManifest(context, id) }
    private fun entry(name: String) = runBlocking { SchemaManifestManager.loadRegistry(context).getJSONObject("files").getJSONObject(name) }

    @Test fun identicalFilesShareWithoutConflictAndSurviveEitherPackage() = runBlocking {
        write("shared.yaml", "same"); own("builtin", "shared.yaml")
        assertTrue(SchemaManifestManager.detectConflicts(context, "rime-ice", listOf("shared.yaml"),
            mapOf("shared.yaml" to SchemaManager.sha256Hex("same".toByteArray()))).isEmpty())
        install("rime-ice", "shared.yaml" to "same")
        assertEquals(listOf("builtin", "rime-ice"), SchemaManifestManager.jsonArrayToList(entry("shared.yaml").getJSONArray("claimedBy")))
        assertTrue(uninstall("rime-ice").success)
        assertEquals("same", File(rime, "shared.yaml").readText())
    }

    @Test fun replacingOneFileKeepsJapaneseAndRestoresBuiltinOnUninstall() = runBlocking {
        write("shared.dict.yaml", "old"); write("japanese_kana.schema.yaml", "Japanese")
        own("builtin", "shared.dict.yaml", "japanese_kana.schema.yaml")
        val conflicts = SchemaManifestManager.detectConflicts(context, "rime-ice", listOf("shared.dict.yaml", "new.schema.yaml"),
            mapOf("shared.dict.yaml" to SchemaManager.sha256Hex("new".toByteArray())))
        assertEquals(listOf("shared.dict.yaml"), conflicts.map { it.fileName })
        install("rime-ice", "shared.dict.yaml" to "new", "new.schema.yaml" to "new schema")
        assertEquals("Japanese", File(rime, "japanese_kana.schema.yaml").readText())
        assertTrue(uninstall("rime-ice").success)
        assertEquals("old", File(rime, "shared.dict.yaml").readText())
        assertTrue(File(rime, "japanese_kana.schema.yaml").exists())
        assertFalse(File(rime, "new.schema.yaml").exists())
    }

    @Test fun removingMiddlePackageDoesNotResurrectIt() {
        write("shared.yaml", "builtin"); own("builtin", "shared.yaml")
        install("A", "shared.yaml" to "A")
        install("B", "shared.yaml" to "B")
        assertTrue(uninstall("A").success)
        assertEquals("B", File(rime, "shared.yaml").readText())
        assertTrue(uninstall("B").success)
        assertEquals("builtin", File(rime, "shared.yaml").readText())
    }

    @Test fun upgradingSamePackageDoesNotRestoreItsOlderVersionAfterRemoval() {
        write("shared.yaml", "builtin"); own("builtin", "shared.yaml")
        install("A", "shared.yaml" to "A1")
        install("A", "shared.yaml" to "A2")
        assertTrue(uninstall("A").success)
        assertEquals("builtin", File(rime, "shared.yaml").readText())
    }

    @Test fun userModifiedInstalledFileIsNotDeletedOnUninstall() {
        install("A", "custom.dict.yaml" to "package")
        write("custom.dict.yaml", "my edits")
        assertTrue(uninstall("A").success)
        assertEquals("my edits", File(rime, "custom.dict.yaml").readText())
    }

    @Test fun userPatchesAndLearnedDataAreNotReplacedOrRemoved() {
        write("test.custom.yaml", "my patch"); write("custom_phrase.txt", "my phrase")
        write("test.userdb/00001.log", "learned")
        install("A", "test.custom.yaml" to "package", "custom_phrase.txt" to "bad", "test.userdb/00001.log" to "bad")
        assertEquals("my patch", File(rime, "test.custom.yaml").readText())
        assertEquals("my phrase", File(rime, "custom_phrase.txt").readText())
        assertEquals("learned", File(rime, "test.userdb/00001.log").readText())
        assertTrue(uninstall("A").success)
        assertEquals("my patch", File(rime, "test.custom.yaml").readText())
    }

    @Test fun unknownExistingFileIsBackedUpAndRestored() {
        write("local.dict.yaml", "personal")
        install("A", "local.dict.yaml" to "market")
        assertTrue(uninstall("A").success)
        assertEquals("personal", File(rime, "local.dict.yaml").readText())
        assertFalse(SchemaManifestManager.jsonArrayToList(entry("local.dict.yaml").getJSONArray("claimedBy")).contains("builtin"))
    }

    @Test fun failedInstallRollsBackEarlierCopiedFilesAndOwnership() = runBlocking {
        write("shared.yaml", "old"); own("builtin", "shared.yaml")
        val registryBefore = SchemaManifestManager.loadRegistry(context).toString()
        val staged = temporary.newFolder()
        File(staged, "shared.yaml").writeText("new")
        try {
            SchemaManifestManager.installStagedFiles(context, "A", "A", "1", true, staged,
                listOf("shared.yaml", "../outside.yaml"))
            fail("unsafe path must fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals("old", File(rime, "shared.yaml").readText())
        assertEquals(registryBefore, SchemaManifestManager.loadRegistry(context).toString())
        assertNull(SchemaManifestManager.loadManifest(context, "A"))
    }

    @Test fun missingRecoveryVersionAbortsUninstallBeforeAnyDeletion() = runBlocking {
        write("shared.yaml", "old"); own("builtin", "shared.yaml")
        install("A", "new.schema.yaml" to "new", "shared.yaml" to "market")
        File(context.filesDir, ".schema-file-versions").deleteRecursively()
        assertFalse(uninstall("A").success)
        assertTrue(File(rime, "new.schema.yaml").exists())
        assertEquals("market", File(rime, "shared.yaml").readText())
        assertNotNull(SchemaManifestManager.loadManifest(context, "A"))
    }

    @Test fun builtinCannotBeUninstalled() {
        write("japanese_kana.schema.yaml", "Japanese"); own("builtin", "japanese_kana.schema.yaml")
        assertFalse(uninstall("builtin").success)
        assertTrue(File(rime, "japanese_kana.schema.yaml").exists())
    }

    @Test fun assetUpdateCannotClobberMarketOwnerOrLocalEdits() {
        val entry = JSONObject().put("sha256", "original").put("claimedBy", JSONArray(listOf("builtin")))
        assertTrue(SchemaManifestManager.canUpdateBuiltinFile(entry, "original"))
        assertFalse(SchemaManifestManager.canUpdateBuiltinFile(entry, "personal edit"))
        entry.put("claimedBy", JSONArray(listOf("builtin", "rime-ice")))
        assertFalse(SchemaManifestManager.canUpdateBuiltinFile(entry, "original"))
        assertFalse(SchemaManifestManager.canUpdateBuiltinFile(null, "original"))
    }

    @Test fun refreshDoesNotClaimUntrackedPersonalFilesAsBuiltin() = runBlocking {
        write("personal.schema.yaml", "personal scheme")
        SchemaManifestManager.refreshBuiltinManifest(context)
        assertFalse(SchemaManifestManager.loadRegistry(context).getJSONObject("files").has("personal.schema.yaml"))
    }
    @Test fun publicInstallerRequiresOnlyActualFileConflictsAndRestoresOnUninstall() = runBlocking {
        write("dictionary.dict.yaml", "builtin dictionary")
        write("japanese.schema.yaml", "schema:\n  schema_id: japanese\n  name: Japanese")
        own("builtin", "dictionary.dict.yaml", "japanese.schema.yaml")
        val market = SchemaManager.getMarketDir(context, "marketA").apply { mkdirs() }
        File(market, "dictionary.dict.yaml").writeText("market dictionary")
        val rejected = SchemaManager.installPackageFromMarketDir(context, "marketA", "Market A", switchEnabled = false)
        assertFalse(rejected.success)
        assertEquals(listOf("dictionary.dict.yaml"), rejected.conflicts.map { it.fileName })
        assertEquals("builtin dictionary", File(rime, "dictionary.dict.yaml").readText())
        val installed = SchemaManager.installPackageFromMarketDir(context, "marketA", "Market A",
            switchEnabled = false, replaceConflictingFiles = true)
        assertTrue(installed.failureReason, installed.success)
        assertEquals("market dictionary", File(rime, "dictionary.dict.yaml").readText())
        assertTrue(File(rime, "japanese.schema.yaml").exists())
        assertTrue(uninstall("marketA").success)
        assertEquals("builtin dictionary", File(rime, "dictionary.dict.yaml").readText())
    }

    @Test fun publicInstallerDoesNotTreatAnUnrelatedInstalledSchemaAsConflict() = runBlocking {
        write("japanese.schema.yaml", "schema:\n  schema_id: japanese\n  name: Japanese")
        own("builtin", "japanese.schema.yaml")
        val market = SchemaManager.getMarketDir(context, "marketA").apply { mkdirs() }
        File(market, "another.dict.yaml").writeText("new dictionary")
        val result = SchemaManager.installPackageFromMarketDir(context, "marketA", "Market A", switchEnabled = false)
        assertTrue(result.failureReason, result.success)
        assertTrue(result.conflicts.isEmpty())
        assertTrue(File(rime, "japanese.schema.yaml").exists())
        assertTrue(File(rime, "another.dict.yaml").exists())
    }

    @Test fun controlledBuiltinMigrationUpdatesOwnershipAndBackup() = runBlocking {
        write("t9_pinyin.schema.yaml", "old"); own("builtin", "t9_pinyin.schema.yaml")
        val updated = write("t9_pinyin.schema.yaml", "new built-in")
        SchemaManifestManager.recordBuiltinReplacement(context, "t9_pinyin.schema.yaml", updated)
        val expected = SchemaManifestManager.fileSha256(updated)
        assertEquals(expected, entry("t9_pinyin.schema.yaml").getString("sha256"))
        assertEquals(expected, SchemaManifestManager.loadManifest(context, "builtin")!!.getJSONObject("files")
            .getJSONObject("t9_pinyin.schema.yaml").getString("sha256"))
        assertEquals("new built-in", File(SchemaManager.getMarketDir(context, "builtin"), "t9_pinyin.schema.yaml").readText())
    }

    @Test fun controlledBuiltinMigrationCannotClaimMarketOwnership() {
        write("t9_pinyin.schema.yaml", "market"); own("rime-ice", "t9_pinyin.schema.yaml")
        SchemaManifestManager.recordBuiltinReplacement(context, "t9_pinyin.schema.yaml", File(rime, "t9_pinyin.schema.yaml"))
        assertEquals(listOf("rime-ice"), SchemaManifestManager.jsonArrayToList(entry("t9_pinyin.schema.yaml").getJSONArray("claimedBy")))
    }

    @Test fun failedPublicUninstallDoesNotChangeMetadataOrDeleteDownload(): Unit = runBlocking {
        write("shared.yaml", "old"); own("builtin", "shared.yaml")
        install("A", "new.schema.yaml" to "new", "shared.yaml" to "market")
        val downloaded = File(SchemaManager.getMarketDir(context, "A"), "package.zip").apply {
            parentFile!!.mkdirs(); writeText("downloaded archive")
        }
        File(context.filesDir, ".schema-file-versions").deleteRecursively()
        val before = SchemaManifestManager.loadRegistry(context).toString()
        val result = SchemaManager.uninstallPackage(context, "A")
        assertFalse(result.success)
        assertTrue(result.message.contains("恢复文件缺失"))
        assertEquals(before, SchemaManifestManager.loadRegistry(context).toString())
        assertNotNull(SchemaManifestManager.loadManifest(context, "A"))
        assertTrue(downloaded.exists())
        org.mockito.kotlin.verify(context, org.mockito.kotlin.never()).getSharedPreferences(
            org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test fun legacyDeleteApiReportsBuiltinUninstallFailure() = runBlocking {
        write("japanese.schema.yaml", "japanese"); own("builtin", "japanese.schema.yaml")
        assertFalse(SchemaManager.deleteSchemaFiles(context, "builtin"))
        assertTrue(File(rime, "japanese.schema.yaml").exists())
        assertNotNull(SchemaManifestManager.loadManifest(context, "builtin"))
    }

}
