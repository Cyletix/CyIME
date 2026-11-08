package com.kingzcheung.xime.association

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AssociationReleaseTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun disablingDuringLoadReleasesTheTableAndReenablingKeepsLearning() = runBlocking {
        val assets = mock<AssetManager>()
        val ctx = mock<Context>()
        whenever(ctx.applicationContext).thenReturn(ctx)
        whenever(ctx.filesDir).thenReturn(files.root)
        whenever(ctx.assets).thenReturn(assets)
        val prefs = mock<SharedPreferences>()
        whenever(ctx.getSharedPreferences("kime_settings", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getBoolean(SettingsPreferences.KEY_SMART_PREDICTION_ENABLED, true)).thenReturn(true)
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val bytes = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write("今\t天\t8\t10\n".toByteArray()) }
        }.toByteArray()
        whenever(assets.open(BaseAssociationModel.ASSET)).thenAnswer {
            entered.countDown()
            check(resume.await(10, TimeUnit.SECONDS))
            bytes.inputStream()
        }
        val engine = AssociationManager::class.java.declaredFields.single { it.type == NgramFusionEngine::class.java }
        engine.isAccessible = true
        AssociationManager.release()
        try {
            val loading = async(Dispatchers.Default) { AssociationManager.initialize(ctx) }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                AssociationManager.release()
            } finally {
                resume.countDown()
            }
            assertFalse(loading.await())
            assertNull(engine.get(AssociationManager))
            whenever(prefs.getBoolean(SettingsPreferences.KEY_SMART_PREDICTION_ENABLED, true)).thenReturn(false)
            assertFalse(AssociationManager.initialize(ctx))
            whenever(prefs.getBoolean(SettingsPreferences.KEY_SMART_PREDICTION_ENABLED, true)).thenReturn(true)
            assertTrue(AssociationManager.initialize(ctx))
            assertEquals("天", AssociationManager.predict("今").first().text)
            AssociationManager.recordInput("银河")
            val personal = AssociationManager.learningData(ctx)
            assertTrue(personal.bigrams.any { it.tokens == listOf("银", "河") })
            AssociationManager.release()
            assertNull(engine.get(AssociationManager))
            assertTrue(AssociationManager.predict("今").isEmpty())
            assertTrue(AssociationManager.initialize(ctx))
            assertEquals(personal, AssociationManager.learningData(ctx))
            assertEquals("河", AssociationManager.predict("银").first().text)
        } finally {
            AssociationManager.release()
        }
    }
}
