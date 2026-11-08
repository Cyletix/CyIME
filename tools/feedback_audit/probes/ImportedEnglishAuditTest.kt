package com.kingzcheung.xime.audit

import android.content.Context
import android.content.res.AssetManager
import com.kingzcheung.xime.association.AssociationService
import com.kingzcheung.xime.service.PredictionManager
import com.kingzcheung.xime.settings.CellDictionaryManager
import com.kingzcheung.xime.settings.CellDictionaryRime
import com.kingzcheung.xime.settings.SchemaMeta
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import java.io.File

class ImportedEnglishAuditTest {
    @get:Rule val temp = TemporaryFolder()
    @After fun release() = AssociationService.release()

    @Test fun activeImportedWordReachesIndependentEnglishCompletion() = runBlocking {
        AssociationService.release()
        val app = mock<Context>()
        val assets = mock<AssetManager>()
        whenever(app.filesDir).thenReturn(temp.newFolder())
        whenever(app.assets).thenReturn(assets)
        val trie = File("src/main/assets/english_trie.bin").readBytes()
        whenever(assets.list("")).thenReturn(arrayOf("english_trie.bin"))
        whenever(assets.open("english_trie.bin")).thenAnswer { trie.inputStream() }
        val prediction = PredictionManager(app, this, {})
        val word = "cyimequokka"
        suspend fun hasWord() = prediction.getEnglishAssociations("cyimequok").any { it.equals(word, true) }
        assertTrue("Bundled completion must work", "hello" in prediction.getEnglishAssociations("hel"))
        assertFalse("Unique word must initially be absent", hasWord())
        val store = CellDictionaryManager.store(app)
        val entry = store.install("你好 ni hao\n$word\n".toByteArray())
        assertEquals(listOf(word), store.englishWords(entry.id))
        val rime = temp.newFolder()
        val schemas = listOf(SchemaMeta("rime_ice", "拼音"))
        File(rime, "rime_ice.schema.yaml").writeText("translator:\n  dictionary: rime_ice\n")
        fun apply(enabled: Boolean) {
            val ids = if (enabled) setOf(entry.id) else emptySet()
            store.select(entry.id, enabled)
            store.selectEnglish(enabled)
            store.activate(ids, enabled) { active, english, _ ->
                CellDictionaryRime.prepare(rime, store, schemas, active, includeEnglish = english)
            }
        }
        apply(true)
        assertTrue(store.read().appliedEnglish)
        assertTrue(File(rime, "user_cyime_cells_en26.dict.yaml").readText().contains(word))
        val withDictionary = hasWord()
        apply(false)
        assertFalse("Disabling the import should remove the word", hasWord())
        assertTrue("Imported and active in Rime, absent from independent English completions", withDictionary)
    }
}
