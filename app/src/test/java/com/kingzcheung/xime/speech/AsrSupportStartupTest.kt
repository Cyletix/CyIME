package com.kingzcheung.xime.speech

import android.content.Context
import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.*
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.*

/** The shared owner must publish one backend regardless of which startup path arrives first. */
class AsrSupportStartupTest {
    private fun withSupport(test: (Context, MockedConstruction<OfflineAsrBackend>) -> Unit) {
        val context = mock<Context>()
        val application = mock<Context>()
        whenever(context.applicationContext).thenReturn(application)
        AsrSupport.releaseModel()
        mockConstruction(OfflineAsrBackend::class.java) { backend, construction ->
            assertSame("The shared backend must retain the application context", application, construction.arguments().single())
            whenever(backend.initialize(any())).thenReturn(true)
        }.use { backends ->
            try {
                test(context, backends)
            } finally {
                // Clear the singleton before the constructor scope ends, including after a failed assertion.
                AsrSupport.releaseModel()
            }
            backends.constructed().forEach { verify(it).releaseModel() }
        }
    }

    @Test fun createThenWarmupThenCreateSharesOneBackendAndDelegatesRepeatedInitialization() {
        withSupport { context, backends ->
            val first = AsrSupport.create(context)
            val backend = backends.constructed().single()
            assertSame(backend, first)
            verifyNoInteractions(backend)

            AsrSupport.warmup(context, InputLanguage.JAPANESE)
            assertSame("Warmup must use the backend already handed to the recording manager", first, AsrSupport.create(context))
            AsrSupport.warmup(context, InputLanguage.JAPANESE)
            assertSame(first, AsrSupport.create(context))

            assertEquals("All startup paths must share a single IPC owner", 1, backends.constructed().size)
            verify(backend, times(2)).initialize(InputLanguage.JAPANESE)
            verify(backend, never()).initialize()
            verify(backend, times(2)).initialize(any())
        }
    }

    @Test fun warmupThenCreateReusesTheWarmBackendAndKeepsEachRequestedLanguageExplicit() {
        withSupport { context, backends ->
            AsrSupport.warmup(context, InputLanguage.ENGLISH)
            val backend = backends.constructed().single()
            assertSame(backend, AsrSupport.create(context))

            AsrSupport.warmup(context, InputLanguage.JAPANESE)
            assertSame(backend, AsrSupport.create(context))
            assertEquals(1, backends.constructed().size)

            val ordered = inOrder(backend)
            ordered.verify(backend).initialize(InputLanguage.ENGLISH)
            ordered.verify(backend).initialize(InputLanguage.JAPANESE)
            verify(backend, times(2)).initialize(any())
            verify(backend, never()).initialize()
        }
    }
}
