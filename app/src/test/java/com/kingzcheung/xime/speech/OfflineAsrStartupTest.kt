package com.kingzcheung.xime.speech

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.service.AsrInferenceClient
import com.kingzcheung.xime.settings.InputLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.*

/** Exercises the real backend lifecycle; only model storage and the remote IPC client are replaced. */
class OfflineAsrStartupTest {
    private fun selection(mode: String): AsrModelManager.Selection = mock {
        on { this.mode } doReturn mode
    }

    private fun withBackend(
        select: (InputLanguage) -> AsrModelManager.Selection,
        test: (OfflineAsrBackend, AsrInferenceClient, MockedConstruction<AsrModelManager>) -> Unit,
    ) {
        val context = mock<Context>()
        val preferences = mock<SharedPreferences>()
        whenever(context.getSharedPreferences(any(), any())).thenReturn(preferences)
        whenever(preferences.getBoolean(any(), any())).thenReturn(true)

        mockConstruction(AsrInferenceClient::class.java) { client, _ ->
            runBlocking {
                whenever(client.ensureBound()).thenReturn(true)
                whenever(client.startAsr(any(), any(), anyOrNull())).thenReturn(true)
                whenever(client.stopAsr()).thenReturn("")
            }
        }.use { clients ->
            mockConstruction(AsrModelManager::class.java) { manager, _ ->
                whenever(manager.selectionForLanguage(any())).thenAnswer { select(it.getArgument(0)) }
            }.use { managers ->
                val backend = OfflineAsrBackend(context)
                test(backend, clients.constructed().single(), managers)
            }
        }
    }

    @Test fun japaneseWarmupUsesJapaneseAndRepeatedInitializationOnlyRebinds() {
        withBackend({ language ->
            assertEquals(InputLanguage.JAPANESE, language)
            selection(SpeechModelCatalog.SENSEVOICE)
        }) { backend, client, managers ->
            assertTrue(backend.initialize(InputLanguage.JAPANESE))
            assertTrue(backend.initialize(InputLanguage.JAPANESE))

            assertEquals("An initialized backend must not select or warm a model again", 1, managers.constructed().size)
            verify(managers.constructed().single()).selectionForLanguage(InputLanguage.JAPANESE)
            runBlocking {
                verify(client, times(2)).ensureBound()
                verify(client, times(2)).setKeepModelAlive(true)
                verify(client).startAsr(eq(SpeechModelCatalog.SENSEVOICE), any(), eq(InputLanguage.JAPANESE))
                verify(client).stopAsr()
                verify(client, times(1)).startAsr(any(), any(), anyOrNull())
                verify(client, never()).startAsr(any(), any(), isNull())
            }
        }
    }

    @Test fun defaultWarmupAndActualChineseSessionShareAnExplicitLanguageAndCallback() {
        withBackend({ language ->
            assertEquals(InputLanguage.CHINESE, language)
            selection(SpeechModelCatalog.ZIPFORMER_TWO_PASS)
        }) { backend, client, managers ->
            assertTrue(backend.initialize())
            assertTrue(backend.start(InputLanguage.CHINESE))

            assertEquals(2, managers.constructed().size)
            managers.constructed().forEach { verify(it).selectionForLanguage(InputLanguage.CHINESE) }
            val callbacks = argumentCaptor<AsrInferenceClient.AsrCallback>()
            runBlocking {
                verify(client, times(2)).startAsr(eq(SpeechModelCatalog.ZIPFORMER_TWO_PASS), callbacks.capture(), eq(InputLanguage.CHINESE))
                verify(client, times(2)).startAsr(any(), any(), anyOrNull())
                verify(client, never()).startAsr(any(), any(), isNull())
                verify(client).stopAsr()
            }
            assertSame("The recording start reinstalls the callback cleared by warmup stop", callbacks.firstValue, callbacks.secondValue)
        }
    }

    @Test fun eachSessionReselectsForItsLanguageAndTheCurrentModelPreference() {
        var preferredModel = SpeechModelCatalog.ZIPFORMER_TWO_PASS
        val selectedLanguages = mutableListOf<InputLanguage>()
        withBackend({ language ->
            selectedLanguages += language
            // Retain the real compatibility policy: a Chinese two-pass model cannot recognize Japanese.
            selection(SpeechLanguages.select(language, preferredModel) { true })
        }) { backend, client, managers ->
            assertTrue(backend.initialize(InputLanguage.CHINESE))
            assertTrue(backend.start(InputLanguage.JAPANESE))
            preferredModel = SpeechModelCatalog.PARAFORMER
            assertTrue(backend.start(InputLanguage.CHINESE))

            assertEquals(listOf(InputLanguage.CHINESE, InputLanguage.JAPANESE, InputLanguage.CHINESE), selectedLanguages)
            assertEquals(3, managers.constructed().size)
            managers.constructed().zip(selectedLanguages).forEach { (manager, language) ->
                verify(manager).selectionForLanguage(language)
            }
            val ordered = inOrder(client)
            runBlocking {
                ordered.verify(client).startAsr(eq(SpeechModelCatalog.ZIPFORMER_TWO_PASS), any(), eq(InputLanguage.CHINESE))
                ordered.verify(client).startAsr(eq(SpeechModelCatalog.SENSEVOICE), any(), eq(InputLanguage.JAPANESE))
                ordered.verify(client).startAsr(eq(SpeechModelCatalog.PARAFORMER), any(), eq(InputLanguage.CHINESE))
                verify(client, times(3)).startAsr(any(), any(), anyOrNull())
                verify(client, never()).startAsr(any(), any(), isNull())
                verify(client).stopAsr()
            }
        }
    }

    @Test fun unavailableWarmupResourcesDoNotPreventARealStartFromRetryingSelection() {
        var attempts = 0
        val errors = mutableListOf<String>()
        withBackend({ language ->
            assertEquals(InputLanguage.JAPANESE, language)
            attempts++
            if (attempts == 1) error("日语模型尚未完整下载")
            selection(SpeechModelCatalog.SENSEVOICE)
        }) { backend, client, managers ->
            backend.setCallbacks({}, null, {}, { errors += it })
            assertTrue("Binding succeeds even when optional model warmup is unavailable", backend.initialize(InputLanguage.JAPANESE))
            runBlocking { verify(client, never()).startAsr(any(), any(), anyOrNull()) }
            assertTrue(backend.start(InputLanguage.JAPANESE))

            assertEquals(2, attempts)
            assertEquals(2, managers.constructed().size)
            assertTrue("A successful retry must not report the earlier warmup as a recognition failure", errors.isEmpty())
            runBlocking {
                verify(client).startAsr(eq(SpeechModelCatalog.SENSEVOICE), any(), eq(InputLanguage.JAPANESE))
                verify(client, never()).startAsr(any(), any(), isNull())
            }
        }
    }
}
