package com.kingzcheung.xime.model

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.settings.MarketVersionStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*

class CandidateModelRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Stores(private val root: File) {
        private val preferences = mutableMapOf<String, SharedPreferences>()
        fun context(): Context = mock<Context>().also { context ->
            whenever(context.filesDir).thenReturn(root)
            whenever(context.applicationContext).thenReturn(context)
            whenever(context.getSharedPreferences(any(), any())).thenAnswer { call ->
                preferences.getOrPut(call.getArgument(0)) {
                    val values = mutableMapOf<String, Any>()
                    mock<SharedPreferences>().also { prefs ->
                        whenever(prefs.getString(any(), anyOrNull())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<String?>(1) }
                        whenever(prefs.getBoolean(any(), any())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1) }
                        whenever(prefs.edit()).thenAnswer {
                            val pending = mutableMapOf<String, Any?>()
                            val editor = mock<SharedPreferences.Editor>()
                            fun change(call: org.mockito.invocation.InvocationOnMock): SharedPreferences.Editor {
                                pending[call.getArgument(0)] = call.getArgument(1)
                                return editor
                            }
                            whenever(editor.putString(any(), anyOrNull())).thenAnswer(::change)
                            whenever(editor.putBoolean(any(), any())).thenAnswer(::change)
                            whenever(editor.remove(any())).thenAnswer { pending[it.getArgument(0)] = null; editor }
                            fun save() { pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value } }
                            whenever(editor.commit()).thenAnswer { save(); true }
                            doAnswer { save(); null }.whenever(editor).apply()
                            editor
                        }
                    }
                }
            }
        }
    }

    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    @Test fun runtimeConflictSurvivesReopenThenRetriesVerifiedCacheWithoutNetworkAndStaysDeletedOnStartup() = runBlocking {
        val stores = Stores(temporary.newFolder())
        val context = stores.context()
        // An invalid URL makes any accidental attempt to redownload fail immediately.
        val version = ModelVersion("fixture", files = listOf(
            ModelFile("t9_sentence.onnx", "not-a-download-url", hash("model"), 5),
            ModelFile("t9_sentence.vocab", "not-a-download-url", hash("words"), 5),
        ))
        val model = ModelInfo("candidate-recovery-${System.nanoTime()}", "fixture", "", ModelCategory.CANDIDATE, listOf(version))
        val cache = ModelStorage.getModelDir(context, model.id)
        val runtime = File(context.filesDir, "rime").apply { mkdirs() }
        val target = File(runtime, "t9_sentence.onnx").apply { writeText("wrong") }
        File(runtime, "t9_sentence.vocab").writeText("words")
        val staging = temporary.newFolder().apply {
            File(this, "t9_sentence.onnx").writeText("model")
            File(this, "t9_sentence.vocab").writeText("words")
        }
        val guard = ModelInstallGuard()
        assertThrows(IOException::class.java) {
            guard.install(guard.currentGeneration(), staging, cache) {
                CandidateModelAssets.installDownloaded(context, model, version)
            }
        }
        assertEquals(0L, guard.completionRevision)
        assertTrue(hasDownloadedModelFiles(cache, version))
        assertEquals("wrong", target.readText())
        val reopened = stores.context()
        assertFalse(ModelManager.isModelDownloaded(reopened, model))
        assertNull(ModelManager.getModelStorageDir(reopened, model))

        val failed = mutableListOf<ModelDownloadState>()
        ModelManager.downloadModel(reopened, model, failed::add)
        assertTrue(failed.last() is ModelDownloadState.Error)
        assertTrue((failed.last() as ModelDownloadState.Error).message.contains("未覆盖"))
        assertFalse(ModelManager.isModelDownloaded(stores.context(), model))
        assertNull(MarketVersionStore.getModelVersion(context, model.id))

        assertTrue(target.delete())
        val retried = mutableListOf<ModelDownloadState>()
        ModelManager.downloadModel(reopened, model, retried::add)
        assertEquals(ModelDownloadState.Complete, retried.last())
        assertEquals("model", target.readText())
        assertEquals("words", File(runtime, "t9_sentence.vocab").readText())
        assertTrue(ModelManager.isModelDownloaded(stores.context(), model))
        assertEquals(runtime, ModelManager.getModelStorageDir(context, model))
        assertEquals(version.version, MarketVersionStore.getModelVersion(context, model.id))

        assertTrue(ModelManager.deleteModel(context, model))
        assertFalse(target.exists())
        assertFalse(hasDownloadedModelFiles(cache, version))
        assertFalse(ModelManager.isModelDownloaded(stores.context(), model))
        // Even a surviving/reintroduced complete cache cannot undo an explicit deletion.
        cache.mkdirs()
        File(cache, "t9_sentence.onnx").writeText("model")
        File(cache, "t9_sentence.vocab").writeText("words")
        CandidateModelAssets.installAvailable(stores.context(), listOf(model))
        assertFalse(target.exists())
        assertFalse(ModelManager.isModelDownloaded(stores.context(), model))
    }

    @Test fun failedCachedApplicationDoesNotAdvanceCompletionAndDeletedGenerationCannotReapply() {
        val guard = ModelInstallGuard()
        val generation = guard.currentGeneration()
        assertThrows(IOException::class.java) {
            guard.applyExisting(generation) { throw IOException("runtime conflict") }
        }
        assertEquals(0L, guard.completionRevision)
        assertTrue(guard.applyExisting(generation) {})
        assertEquals(1L, guard.completionRevision)
        guard.delete { true }
        var applied = false
        assertFalse(guard.applyExisting(generation) { applied = true })
        assertFalse(applied)
        assertEquals(1L, guard.completionRevision)
    }
}
