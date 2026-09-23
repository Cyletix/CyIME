package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.SpeechModelCatalog
import com.kingzcheung.xime.ui.settings.OfflineModelCard
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class OfflineSpeechSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun allModelsHaveNamedChoicesAndSelectionPersists() {
        val manager = AsrModelManager(rule.activity)
        val original = manager.getSelectedModelId()
        try {
            rule.setContent {
                XimeTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { OfflineModelCard() }
                    }
                }
            }
            rule.onNodeWithText("Paraformer 中英流式").assertExists()
            rule.onNodeWithText("SenseVoice 多语").assertExists()
            rule.onNodeWithText("Paraformer + SenseVoice 双模型").performScrollTo().assertIsDisplayed()
            if (manager.selection(SpeechModelCatalog.TWO_PASS).ready) {
                rule.onNodeWithText("Paraformer + SenseVoice 双模型").performClick()
                rule.waitForIdle()
                assertEquals(SpeechModelCatalog.TWO_PASS, manager.getSelectedModelId())
            }
            val output = File(rule.activity.getExternalFilesDir(null), "speech-eval/model-settings.png")
            output.parentFile?.mkdirs()
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().useBitmap { bitmap ->
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { manager.setModel(original) }
    }
    private fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
}
