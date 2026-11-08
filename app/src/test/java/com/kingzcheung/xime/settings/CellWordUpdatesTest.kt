package com.kingzcheung.xime.settings

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CellWordUpdatesTest {
    @get:Rule val temp = TemporaryFolder()
    private val ark = CellDictionaryCatalog.builtins.single { it.id == "builtin_arknights" }
    private val bundled = CellWordUpdates.parse(File("src/main/assets", CellWordUpdates.ASSET).readBytes())
    private fun update(revision: Int, word: String = "精一队", code: String = "jing yi dui") =
        """{"format":"cyime.cell-words.v1","revision":$revision,"dictionaries":{"${ark.id}":[{"word":"$word","code":"$code"}]}}""".toByteArray()

    @Test fun `fresh offline Arknights pack contains jing yi dui only when enabled`() {
        val store = CellDictionaryStore(temp.newFolder())
        val base = File("src/main/assets", ark.asset).readText()
        store.installBuiltin(ark, base)
        val dir = temp.newFolder()
        val schema = SchemaMeta("rime_ice", "拼音")
        File(dir, "rime_ice.schema.yaml").writeText("translator:\n  dictionary: rime_ice\n")
        // 明日方舟 11-08 起新装不默认开，这里按用户打开它来测。
        val bindings = CellDictionaryRime.prepare(dir, store, listOf(schema), setOf(ark.id), updates = bundled)
        val table = File(dir, "cyime_cells/cell_${ark.id}.dict.yaml").readText()
        assertTrue(bindings.isNotEmpty())
        assertEquals(1, table.lineSequence().count { it.startsWith("精一队\tjing yi dui\t") })
        assertEquals(table, bundled.extend(ark.id, table))
        assertEquals(base, bundled.extend("builtin_cs2", base))
        assertTrue(CellDictionaryRime.prepare(dir, store, listOf(schema), emptySet(), updates = bundled.copy(main = emptyList())).isEmpty())
        assertFalse(File(dir, "rime_ice.custom.yaml").readText().contains("cyime_cells"))
        assertEquals(base, store.dictionaryFile(ark.id).readText())
    }

    @Test fun `downloaded revisions survive restart and failed deployment restores the active words`() {
        val store = CellDictionaryStore(temp.newFolder())
        store.installBuiltin(ark, File("src/main/assets", ark.asset).readText())
        store.select(ark.id, false)
        store.markApplied(emptySet())
        val next = bundled.revision + 1
        assertTrue(store.stageWordUpdates(update(next), bundled))
        assertTrue(store.read().pending)
        assertEquals(bundled, store.wordUpdates(store.read().appliedWordRevision, bundled))
        store.activate(emptySet(), false) { _, _, rev -> assertEquals(next, rev) }
        val reopened = CellDictionaryStore(store.directory)
        assertTrue(reopened.read().applied.isEmpty())
        assertFalse(reopened.read().pending)
        assertTrue(reopened.stageWordUpdates(update(next + 1, "精二队", "jing er dui"), bundled))
        assertFalse(reopened.stageWordUpdates(update(next), bundled))
        val attempted = mutableListOf<Int>()
        assertThrows(IllegalStateException::class.java) {
            reopened.activate(emptySet(), false) { _, _, rev ->
                attempted += reopened.wordUpdates(rev, bundled).revision
                if (rev == next + 1) error("部署失败")
            }
        }
        assertEquals(listOf(next + 1, next), attempted)
        assertEquals(next, reopened.read().appliedWordRevision)
        assertTrue(reopened.read().pending)
        assertFalse(reopened.stageWordUpdates(update(next + 1, "精二队", "jing er dui"), bundled))
        assertThrows(IllegalArgumentException::class.java) { reopened.stageWordUpdates(update(next + 1), bundled) }
        assertThrows(Exception::class.java) { reopened.stageWordUpdates(update(next + 2, code = "!!"), bundled) }
        assertEquals(next + 1, reopened.read().downloadedWordRevision)
        assertTrue(reopened.read().selected.isEmpty())
    }

    private fun withMain(revision: Int, rows: String = """{"word":"在线词库","code":"zai xian ci ku","weight":5000}""") =
        """{"format":"cyime.cell-words.v1","revision":$revision,"dictionaries":{},"main":[$rows],"later":1}""".toByteArray()

    @Test fun `main dictionary words reach the pack without any cell dictionary`() {
        val update = CellWordUpdates.parse(withMain(7))
        assertEquals(listOf(CellMainWord("在线词库", "zai xian ci ku", 5000)), update.main)
        val store = CellDictionaryStore(temp.newFolder())
        val dir = temp.newFolder()
        File(dir, "rime_ice.schema.yaml").writeText("translator:\n  dictionary: rime_ice\n")
        val bindings = CellDictionaryRime.prepare(dir, store, listOf(SchemaMeta("rime_ice", "拼音")), emptySet(), updates = update)
        assertEquals(1, bindings.size)
        val pack = File(dir, "${bindings.single().pack}.dict.yaml").readText()
        assertTrue(pack.contains("version: \"1.7\""))
        assertTrue(pack.contains("  - ${CellWordUpdates.MAIN_TABLE}\n"))
        assertFalse(pack.contains("cyime_cells/cell_"))
        val table = File(dir, "${CellWordUpdates.MAIN_TABLE}.dict.yaml").readText()
        assertTrue(table.contains("\n...\n在线词库\tzai xian ci ku\t5000\n"))
        assertTrue(File(dir, "rime_ice.custom.yaml").readText().contains(bindings.single().pack))
        // Next revision: the pack file changes, so the deployment hash asks for a rebuild.
        CellDictionaryRime.prepare(dir, store, listOf(SchemaMeta("rime_ice", "拼音")), emptySet(),
            updates = CellWordUpdates.parse(withMain(8)))
        assertTrue(File(dir, "${bindings.single().pack}.dict.yaml").readText().contains("version: \"1.8\""))
    }

    @Test fun `online words skip a schema whose own config cannot take the pack`() {
        val store = CellDictionaryStore(temp.newFolder())
        val dir = temp.newFolder()
        File(dir, "rime_ice.schema.yaml").writeText("translator:\n  dictionary: rime_ice\n")
        File(dir, "t9_pinyin.schema.yaml").writeText("translator:\n  dictionary: rime_ice\n")
        val custom = "patch:\n  \"translator/packs/+\": [\"mine\"]\n"
        File(dir, "t9_pinyin.custom.yaml").writeText(custom)
        val bindings = CellDictionaryRime.prepare(dir, store,
            listOf(SchemaMeta("rime_ice", "拼音"), SchemaMeta("t9_pinyin", "九键")), emptySet(),
            updates = CellWordUpdates.parse(withMain(3)))
        assertEquals(listOf("rime_ice"), bindings.map { it.schemaId })
        assertEquals(custom, File(dir, "t9_pinyin.custom.yaml").readText())
    }

    @Test fun `bad or duplicate main rows are refused`() {
        for (rows in listOf("""{"word":"在线","code":"zai xian ci","weight":1}""",
            """{"word":"在线","code":"zai xian","weight":0}""",
            """{"word":"abc","code":"a b c","weight":1}""",
            """{"word":"在线","code":"zai xian","weight":1},{"word":"在线","code":"zai xian","weight":2}""")) {
            assertThrows(IllegalArgumentException::class.java) { CellWordUpdates.parse(withMain(2, rows)) }
        }
        assertNull(CellWordUpdates.empty.mainTable())
    }

    @Test fun `auto update waits a day and never applies an unconfirmed selection`() {
        assertTrue(CellWordAutoUpdate.due(CellWordAutoUpdate.INTERVAL_MS, 0L))
        assertFalse(CellWordAutoUpdate.due(CellWordAutoUpdate.INTERVAL_MS - 1, 0L))
        assertTrue(CellWordAutoUpdate.due(5L, 10L))
        val ready = CellDictionaryState(downloadedWordRevision = 3, appliedWordRevision = 2)
        assertTrue(CellWordAutoUpdate.shouldApply(ready))
        assertFalse(CellWordAutoUpdate.shouldApply(ready.copy(appliedWordRevision = 3)))
        assertFalse(CellWordAutoUpdate.shouldApply(ready.copy(selected = setOf("builtin_arknights"))))
        assertFalse(CellWordAutoUpdate.shouldApply(ready.copy(selectedEnglish = true)))
    }
}
