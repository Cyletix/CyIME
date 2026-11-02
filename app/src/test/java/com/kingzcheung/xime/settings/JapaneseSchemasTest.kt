package com.kingzcheung.xime.settings

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JapaneseSchemasTest {
    @get:Rule val folder = TemporaryFolder()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Same packing and content hash as tasks-japanese.gradle.kts. */
    private fun bundle(files: Map<String, String>): Map<String, ByteArray> {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { output ->
            files.toSortedMap().forEach { (path, text) -> output.putNextEntry(ZipEntry(path)); output.write(text.toByteArray()); output.closeEntry() }
        }
        val version = sha256(files.toSortedMap().entries.joinToString("") { (path, text) -> "$path\u0000${sha256(text.toByteArray())}\n" }.toByteArray())
        return mapOf("azookey/dictionary.zip" to zip.toByteArray(), "azookey/dictionary.version" to version.toByteArray())
    }

    private fun install(rime: File, assets: Map<String, ByteArray>) =
        JapaneseSchemas.installAzooKeyDictionary(rime) { (assets[it] ?: throw java.io.FileNotFoundException(it)).inputStream() }

    @Test fun azooKeyDictionaryIsUnpackedOnceAndLearningIsKept() {
        val rime = folder.newFolder("rime")
        File(rime, "azookey/memory").mkdirs(); File(rime, "azookey/memory/memory.louds").writeText("learned")
        val first = bundle(mapOf("louds/charID.chid" to "ids", "mm.binary" to "matrix"))
        install(rime, first)
        assertEquals("ids", File(rime, "azookey/dictionary/louds/charID.chid").readText())
        File(rime, "azookey/dictionary/mm.binary").writeText("touched")
        install(rime, first)  // same version: not unpacked again
        assertEquals("touched", File(rime, "azookey/dictionary/mm.binary").readText())
        install(rime, bundle(mapOf("louds/charID.chid" to "ids2", "p/pc_0.csv" to "p")))
        assertEquals("ids2", File(rime, "azookey/dictionary/louds/charID.chid").readText())
        assertFalse(File(rime, "azookey/dictionary/mm.binary").exists())
        assertEquals("learned", File(rime, "azookey/memory/memory.louds").readText())
        assertEquals(setOf("dictionary", "memory"), File(rime, "azookey").list()!!.toSet())
    }

    @Test fun damagedAzooKeyDictionaryLeavesTheInstalledOne() {
        val rime = folder.newFolder("rime")
        install(rime, bundle(mapOf("louds/charID.chid" to "ids")))
        val broken = bundle(mapOf("louds/charID.chid" to "new")).toMutableMap()
        broken["azookey/dictionary.version"] = "0".repeat(64).toByteArray()
        assertThrows(IllegalStateException::class.java) { install(rime, broken) }
        assertEquals("ids", File(rime, "azookey/dictionary/louds/charID.chid").readText())
        assertEquals(setOf("dictionary"), File(rime, "azookey").list()!!.toSet())
    }

    @Test fun packageWithoutJapaneseInstallsNothing() {
        val rime = folder.newFolder("rime")
        install(rime, emptyMap())
        assertFalse(File(rime, "azookey").exists())
    }

    @Test fun bundledSchemasUseAzooKeyAndNoJaroomajiDictionary() {
        listOf("japanese", "japanese_kana", "jaroomaji").forEach { schema ->
            val path = "src/main/assets/rime_japanese/$schema.schema.yaml"
            val text = listOf(File(path), File("app/$path")).first { it.isFile }.readText()
            assertTrue(schema, "    - cyime_azookey_translator\n" in text.replace("\r\n", "\n"))
            assertFalse(schema, "script_translator" in text || "dictionary:" in text || "cyime_japanese_completion" in text)
        }
    }

    @Test fun currentSchemaIsNotTreatedAsAnOldBuiltin() {
        // Only old bundled versions are replaced on upgrade; once installed, the current one counts as the user's.
        val path = "src/main/assets/rime_japanese/japanese.schema.yaml"
        val current = listOf(File(path), File("app/$path")).first { it.isFile }.readText()
        assertFalse(JapaneseSchemas.isPreviousBuiltinSchema(current))
    }

    @Test fun upgradingOnlyAddsJapaneseWithoutRestoringOtherDisabledSchemas() {
        assertEquals(listOf("user_schema", "japanese", "japanese_kana"), JapaneseSchemas.addMissing(listOf("user_schema")))
    }

    @Test fun alreadyEnabledJapaneseKeepsOrderAndHasNoDuplicate() {
        assertEquals(listOf("japanese_kana", "user_schema", "japanese"),
            JapaneseSchemas.addMissing(listOf("japanese_kana", "user_schema")))
    }
}
