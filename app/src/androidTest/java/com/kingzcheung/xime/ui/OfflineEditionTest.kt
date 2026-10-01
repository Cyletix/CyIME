package com.kingzcheung.xime.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.handwriting.*
import com.kingzcheung.xime.model.*
import com.kingzcheung.xime.settings.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class OfflineEditionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun languageSettingsFitNarrowScreensAndLargeText() {
        val scale = mutableStateOf(1f)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale.value)) {
            MaterialTheme { Box(Modifier.requiredSize(280.dp, 500.dp)) {
                com.kingzcheung.xime.ui.settings.LanguageSettingsContent {}
            } }
        } }
        for (font in listOf(1f, 1.3f, 2f)) {
            rule.runOnIdle { scale.value = font }
            rule.assertGeometry("language-settings", "language settings font=$font")
        }
    }

    @Test fun freshLanguageSettingsOfferChineseAndRequiredEnglish() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val fresh = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("language-test-$name", mode)
        }
        val prefs = SettingsPreferences.getPrefsPublic(fresh)
        prefs.edit().clear().commit()
        try {
            LanguagePreferences.initialize(fresh)
            assertTrue(SettingsPreferences.isSmartPredictionEnabled(fresh))
            assertTrue(SettingsPreferences.isSingleAssociationMode(fresh))
            assertEquals("predictive-text-base", SettingsPreferences.getPredictionSelectedModel(fresh))
            assertTrue(SettingsPreferences.isSttEnabled(fresh))
            assertTrue(SettingsPreferences.isSttUseLocal(fresh))
            assertFalse(SettingsPreferences.isSttKeepEngineAlive(fresh))
            DeviceDefaults.initialize(fresh)
            assertEquals(DeviceDefaults.supportsRefinement(fresh), prefs.getBoolean("key_glow_enabled", false))
            SettingsPreferences.setSmartPredictionEnabled(fresh, false)
            SettingsPreferences.setAssociationSingleMode(fresh, false)
            SettingsPreferences.setSttEnabled(fresh, false)
            SettingsPreferences.setSttUseLocal(fresh, false)
            prefs.edit().putBoolean("key_glow_enabled", !DeviceDefaults.supportsRefinement(fresh)).commit()
            DeviceDefaults.initialize(fresh)
            assertFalse(SettingsPreferences.isSmartPredictionEnabled(fresh))
            assertFalse(SettingsPreferences.isSingleAssociationMode(fresh))
            assertFalse(SettingsPreferences.isSttEnabled(fresh))
            assertFalse(SettingsPreferences.isSttUseLocal(fresh))
            assertEquals(!DeviceDefaults.supportsRefinement(fresh), prefs.getBoolean("key_glow_enabled", false))
            assertEquals(setOf(InputLanguage.CHINESE, InputLanguage.ENGLISH), LanguagePreferences.enabled(fresh))
            val choices = InputModes.languageChoices(listOf(SchemaInfo("rime_ice", "中文26键", "", "", "")), "rime_ice",
                emptyMap(), InputModes.languageOrder(fresh))
            assertEquals(listOf(InputLanguage.CHINESE, InputLanguage.ENGLISH), choices.map { it.language })
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun whiteKeyLabelsStillProduceVisibleInkOnWhitePaper() {
        val ink = handwritingInk(Color.White, Color.White)
        assertEquals(Color.Black, ink)
        assertEquals(Color.White, handwritingInk(Color.Black, Color.Black))
        rule.setContent {
            Canvas(Modifier.size(160.dp).background(Color.White).testTag("paper")) {
                renderStrokes(listOf(listOf(StrokePoint(size.width * .2f, size.height * .5f),
                    StrokePoint(size.width * .8f, size.height * .5f))), emptyList(), ink)
            }
        }
        val pixels = rule.onNodeWithTag("paper").captureToImage().toPixelMap()
        assertTrue("真实笔迹应明显区别于白纸", pixels[pixels.width / 2, pixels.height / 2].red < .2f)
    }

    @Test fun handwritingOutlineSurvivesATransparentCanvasOverEitherSurface() {
        val background = mutableStateOf(Color.White)
        val ink = mutableStateOf(Color.White)
        rule.setContent {
            Canvas(Modifier.size(160.dp).background(background.value).testTag("outlined-ink")) {
                renderStrokes(listOf(listOf(StrokePoint(size.width / 2, size.height / 2))), emptyList(), ink.value)
            }
        }
        fun pixels() = rule.onNodeWithTag("outlined-ink").captureToImage().toPixelMap()
        val light = pixels()
        assertTrue("白底白色笔画需要黑色外缘", (19..40).any {
            light[light.width / 2, light.height / 2 + it].red < .25f
        })
        rule.runOnIdle { background.value = Color.Black; ink.value = Color.Black }
        val dark = pixels()
        assertTrue("黑底黑色笔画需要白色外缘", (19..40).any {
            dark[dark.width / 2, dark.height / 2 + it].red > .75f
        })
    }

    @Test fun offlineEditionInstallsEveryModelAndDoesNotResetLaterChoices() = runBlocking {
        Assume.assumeTrue(BuildConfig.BUNDLED_MODELS)
        val app = ApplicationProvider.getApplicationContext<Context>()
        withTimeout(180_000) {
            while (!app.getSharedPreferences("bundled_models", 0).getBoolean("defaults_applied", false)) delay(200)
        }
        assertTrue(BundledModelInstaller.modelIds.all { ModelManager.isModelReady(app, it) })
        assertTrue(SettingsPreferences.isSmartPredictionEnabled(app))
        assertTrue(SettingsPreferences.isSttEnabled(app))
        assertTrue(SettingsPreferences.isSttUseLocal(app))
        try {
            SettingsPreferences.setSmartPredictionEnabled(app, false)
            withContext(Dispatchers.IO) { BundledModelInstaller.install(app) }
            assertFalse("完成引导后应尊重用户关闭", SettingsPreferences.isSmartPredictionEnabled(app))
        } finally { SettingsPreferences.setSmartPredictionEnabled(app, true) }
    }

    @Test fun freshInstallDoesNotSeedExperimentalLayouts() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "offline-layout-test-${System.nanoTime()}").apply { mkdirs() }
        val fresh = object : ContextWrapper(app) { override fun getFilesDir() = directory }
        try {
            assertTrue(CustomKeyboardLayouts.load(fresh).isEmpty())
            assertEquals(listOf("rime_ice", "t9_pinyin"), CyimeInputDefaults.recommended)
        } finally { directory.deleteRecursively(); CustomKeyboardLayouts.load(app) }
    }

    @Test fun bundledDefaultsUseBaseAndDeviceProfileWithoutResettingUserChoices() = runBlocking {
        Assume.assumeTrue(BuildConfig.BUNDLED_MODELS)
        val app = ApplicationProvider.getApplicationContext<Context>()
        val used = mutableSetOf<String>()
        val fresh = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val testName = "bundle-profile-test-$name"
                used += testName
                return app.getSharedPreferences(testName, mode)
            }
        }
        try {
            withContext(Dispatchers.IO) { BundledModelInstaller.install(fresh) }
            val high = DeviceDefaults.supportsRefinement(fresh)
            assertEquals("predictive-text-base", SettingsPreferences.getPredictionSelectedModel(fresh))
            val asr = com.kingzcheung.xime.speech.AsrModelManager(fresh)
            assertEquals(com.kingzcheung.xime.speech.SpeechModelCatalog.ZIPFORMER, asr.getFirstPassModelId())
            assertEquals(high, asr.isRefinementEnabled())
            assertEquals(high, SettingsPreferences.getPrefsPublic(fresh).getBoolean("key_glow_enabled", !high))
            asr.setRefinementEnabled(!high)
            SettingsPreferences.getPrefsPublic(fresh).edit().putBoolean("key_glow_enabled", !high).commit()
            withContext(Dispatchers.IO) { BundledModelInstaller.install(fresh) }
            assertEquals(!high, asr.isRefinementEnabled())
            assertEquals(!high, SettingsPreferences.getPrefsPublic(fresh).getBoolean("key_glow_enabled", high))
        } finally { used.forEach { app.getSharedPreferences(it, 0).edit().clear().commit() } }
    }
}
