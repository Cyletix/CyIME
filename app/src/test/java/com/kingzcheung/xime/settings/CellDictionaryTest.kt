package com.kingzcheung.xime.settings

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CellDictionaryTest {
    @get:Rule val temp = TemporaryFolder()
    private fun store() = CellDictionaryStore(temp.newFolder())
    private val pack = CellDictionaryRime.packName("rime_ice")

    @Test fun `selection is persistent but activation is explicit and downloads start disabled`() {
        val store = store()
        val entry = store.install(scelFixture())
        assertTrue(store.read().selected.isEmpty())
        store.select(entry.id, true)
        val reopened = CellDictionaryStore(store.directory)
        assertTrue(reopened.read().pending)
        assertTrue(reopened.read().applied.isEmpty())
        reopened.markApplied(setOf(entry.id))
        assertFalse(reopened.read().pending)
        reopened.select(entry.id, false)
        assertTrue(reopened.read().pending)
        assertEquals(setOf(entry.id), reopened.read().applied)
    }

    @Test fun `duplicate imports preserve selection and invalid downloads cannot damage installed words`() {
        val store = store()
        val offer = CellDictionaryCatalog.offers.first()
        val entry = store.install(scelFixture(), offer)
        store.select(entry.id, true)
        assertEquals(entry, store.install(scelFixture(), offer))
        assertThrows(Exception::class.java) { store.install(byteArrayOf(1, 2)) }
        assertEquals(listOf(entry), store.read().installed)
        assertEquals(setOf(entry.id), store.read().selected)
        assertTrue(store.dictionaryFile(entry.id).readText().contains("你好\tni hao"))
        assertThrows(IllegalArgumentException::class.java) { store.dictionaryFile("../../unsafe") }
    }

    @Test fun `unavailable dictionaries cannot be selected or partially applied`() {
        val store = store()
        assertThrows(IllegalArgumentException::class.java) { store.select("sogou_999", true) }
        val rime = temp.newFolder()
        val custom = File(rime, "t9_pinyin.custom.yaml").apply { writeText("patch:\n  x: y\n") }
        assertThrows(IllegalArgumentException::class.java) {
            CellDictionaryRime.prepare(rime, store, listOf(SchemaMeta("t9_pinyin", "九键")), setOf("sogou_999"))
        }
        assertEquals("patch:\n  x: y\n", custom.readText())
    }

    @Test fun `managed append preserves personal dictionaries and unrelated user patches`() {
        for (source in listOf("# header\npatch:\n  \"translator/packs\": [user_simp, extra]\n  other: 42\n",
            "patch:\r\n    translator/packs:\r\n      - user_simp\r\n    other: 42\r\n",
            "patch:\n  translator:\n    dictionary: rime_ice\n    packs: [user_simp]\n")) {
            val patched = CellDictionaryRime.patch(source, pack)
            assertTrue(patched.contains(pack))
            assertEquals(patched, CellDictionaryRime.patch(patched, pack))
            assertEquals(source, CellDictionaryRime.patch(patched, null))
        }
    }

    @Test fun `new patch works without a patch block and unsupported yaml is left alone`() {
        for (source in listOf("", "# comment\n", "other: 42\n...\n", "patch:")) {
            assertTrue(CellDictionaryRime.patch(source, pack).contains(pack))
        }
        for (source in listOf("patch: {other: 42}", "patch:\n  translator/packs/+: [someone_else]\n",
            "patch:\n  translator/packs/@next: someone_else\n")) {
            assertThrows(Exception::class.java) { CellDictionaryRime.patch(source, pack) }
        }
    }

    @Test fun `packs follow language scheme and primary dictionary rather than layout`() {
        val store = store()
        val entry = store.install(scelFixture())
        val rime = temp.newFolder()
        val schemas = listOf(SchemaMeta("t9_pinyin", "九键"), SchemaMeta("pinyin_simp", "拼音"),
            SchemaMeta("double_pinyin_flypy", "双拼"), SchemaMeta("luna_pinyin", "明月"),
            SchemaMeta("japanese", "日语"), SchemaMeta("wubi86", "五笔"), SchemaMeta("custom_unknown", "未知"))
        schemas.forEach { schema ->
            File(rime, "${schema.schemaId}.schema.yaml").writeText("translator:\n  dictionary: ${if (schema.schemaId == "luna_pinyin") "luna_pinyin" else "rime_ice"}\n  packs: [user_simp]\n")
        }
        File(rime, "pinyin_simp.custom.yaml").writeText("# user notes only\n")
        val bindings = CellDictionaryRime.prepare(rime, store, schemas, setOf(entry.id))
        assertEquals(4, bindings.size)
        assertEquals(2, bindings.map { it.pack }.distinct().size)
        assertFalse(File(rime, "japanese.custom.yaml").exists())
        assertFalse(File(rime, "wubi86.custom.yaml").exists())
        assertTrue(File(rime, "$pack.dict.yaml").readText().contains("cyime_cells/cell_${entry.id}"))
        assertTrue(File(rime, "cyime_cells/cell_${entry.id}.dict.yaml").readText().contains("你好\tni hao"))
        val before = File(rime, "$pack.dict.yaml").lastModified()
        CellDictionaryRime.prepare(rime, store, schemas, setOf(entry.id))
        assertEquals(before, File(rime, "$pack.dict.yaml").lastModified())
        CellDictionaryRime.prepare(rime, store, schemas, emptySet())
        assertFalse(File(rime, "t9_pinyin.custom.yaml").readText().contains(pack))
        assertTrue(File(rime, "pinyin_simp.custom.yaml").readText().startsWith("# user notes only\n"))
    }

    @Test fun `english words get a table per keyboard beside the bundled english`() {
        val store = store()
        val entry = store.install("你好 ni hao\nAme\nN0tail\n".toByteArray())
        assertEquals(2, entry.english)
        val rime = temp.newFolder()
        val schemas = listOf(SchemaMeta("t9_pinyin", "九键"), SchemaMeta("pinyin_14jian", "14键"), SchemaMeta("rime_ice", "拼音"))
        schemas.forEach { File(rime, "${it.schemaId}.schema.yaml").writeText("translator:\n  dictionary: rime_ice\n") }
        CellDictionaryRime.prepare(rime, store, schemas, setOf(entry.id))
        for ((schema, kind) in listOf("t9_pinyin" to CellDictionaryEnglish.Kind.T9,
                "pinyin_14jian" to CellDictionaryEnglish.Kind.MERGED, "rime_ice" to CellDictionaryEnglish.Kind.ALPHABETIC)) {
            val custom = File(rime, "$schema.custom.yaml").readText()
            assertTrue(custom.contains("${kind.translator}@cyime_cells_en"))
            assertTrue(custom.contains("\"schema/dependencies/+\": [\"${kind.dictionary}\"]"))
            assertTrue(File(rime, "${kind.dictionary}.schema.yaml").readText().contains("dictionary: ${kind.dictionary}"))
        }
        assertTrue(File(rime, "user_cyime_cells_en9.dict.yaml").readText().contains("Ame\t263\t"))
        assertTrue(File(rime, "user_cyime_cells_en9.dict.yaml").readText().contains("N0tail\t608245\t"))
        val k14 = File(rime, "user_cyime_cells_en14.dict.yaml").readText()
        assertTrue(k14.contains("Ame\tAmE\t") && !k14.contains("N0tail"))  // 14 keys have no digits
        assertTrue(File(rime, "user_cyime_cells_en26.dict.yaml").readText().contains("Ame\tame\t"))
        CellDictionaryRime.prepare(rime, store, schemas, emptySet())
        assertFalse(File(rime, "rime_ice.custom.yaml").readText().contains("cyime_cells_en"))
        store.remove(entry.id)
        assertTrue(store.englishWords(entry.id).isEmpty())
    }

    @Test fun `unsupported later schema does not leave earlier schema partially edited`() {
        val rime = temp.newFolder()
        val store = store()
        val entry = store.install(scelFixture())
        val schemas = listOf(SchemaMeta("t9_pinyin", "九键"), SchemaMeta("pinyin_simp", "拼音"))
        schemas.forEach { File(rime, "${it.schemaId}.schema.yaml").writeText("translator:\n  dictionary: rime_ice\n") }
        File(rime, "pinyin_simp.custom.yaml").writeText("patch: {translator/packs: [custom]}\n")
        assertThrows(Exception::class.java) { CellDictionaryRime.prepare(rime, store, schemas, setOf(entry.id)) }
        assertFalse(File(rime, "t9_pinyin.custom.yaml").exists())
        assertFalse(File(rime, "$pack.dict.yaml").exists())
    }

    @Test fun `failed engine activation restores old packs and retains pending switches for retry`() {
        val store = store()
        val entry = store.install(scelFixture())
        store.select(entry.id, true)
        val engineCalls = mutableListOf<Set<String>>()
        assertThrows(IllegalStateException::class.java) {
            store.activate(setOf(entry.id)) { ids ->
                engineCalls += ids
                if (ids.isNotEmpty()) error("native pack compilation failed")
            }
        }
        assertEquals(listOf(setOf(entry.id), emptySet<String>()), engineCalls)
        assertTrue(store.read().applied.isEmpty())
        assertTrue(store.read().pending)
        store.activate(setOf(entry.id)) { }
        assertFalse(store.read().pending)
        store.select(entry.id, false)
        store.activate(emptySet()) { }
        assertTrue(store.read().applied.isEmpty())
    }

    @Test fun `missing cached file can be disabled or repaired without losing its enabled state`() {
        val store = store()
        val bytes = scelFixture()
        val entry = store.install(bytes)
        store.select(entry.id, true)
        store.markApplied(setOf(entry.id))
        assertTrue(store.dictionaryFile(entry.id).delete())
        store.select(entry.id, false)
        assertTrue(store.read().selected.isEmpty())
        assertEquals(entry, store.install(bytes))
        assertTrue(store.dictionaryFile(entry.id).isFile)
        assertEquals(1, store.read().installed.size)
        assertEquals(setOf(entry.id), store.read().applied)
    }

    @Test fun `built-in dictionary is on by default once and a user switch-off survives app updates`() {
        val store = store()
        val builtin = CellDictionaryCatalog.builtins.single { it.id == "builtin_arknights" }
        val v1 = "---\nname: cell_builtin_arknights\n...\n解手流\tjie shou liu\t1200\n"
        store.installBuiltin(builtin, v1)
        assertEquals(setOf(builtin.id), store.read().selected)
        assertEquals(setOf(builtin.id), store.read().applied)
        assertEquals(1, store.read().installed.single().count)
        store.select(builtin.id, false)
        store.markApplied(emptySet())
        val v2 = v1 + "四星队\tsi xing dui\t1200\n"
        store.installBuiltin(builtin, v2)
        assertTrue(store.read().selected.isEmpty())
        assertTrue(store.read().applied.isEmpty())
        assertEquals(2, store.read().installed.single().count)
        assertEquals(v2, store.dictionaryFile(builtin.id).readText())
    }

    @Test fun `bundled arknights dictionary is a valid pinyin table with the core community terms`() {
        val text = File("src/main/assets/cell_dictionaries/cell_builtin_arknights.dict.yaml").readText()
        assertTrue(text.contains("\nname: cell_builtin_arknights\n"))
        val rows = text.substringAfter("\n...\n").lines().filter { it.isNotBlank() }.map { it.split('\t') }
        assertTrue(rows.size > 1000)
        val syllable = Regex("[a-z]{1,6}")
        rows.forEach { row ->
            assertEquals(row.toString(), 3, row.size)
            val codes = row[1].split(' ')
            assertEquals(row.toString(), row[0].length, codes.size)
            assertTrue(row.toString(), codes.all(syllable::matches) && row[2].toInt() > 0)
        }
        assertEquals(rows.size, rows.map { it[0] to it[1] }.distinct().size)
        val words = rows.groupBy({ it[0] }, { it[1] })
        for ((word, code) in listOf("无精二" to "wu jing er", "精一1级" to "jing yi yi ji", "四星队" to "si xing dui",
            "解手流" to "jie shou liu", "精二90级" to "jing er jiu shi ji", "无A组" to "wu a zu",
            "卫戍协议" to "wei shu xie yi", "重装" to "zhong zhuang")) {
            assertEquals(word, code, words[word]?.first())
        }
        assertEquals(listOf("wei shi dai er", "wei shen dai er"), words["维什戴尔"])
    }
}
