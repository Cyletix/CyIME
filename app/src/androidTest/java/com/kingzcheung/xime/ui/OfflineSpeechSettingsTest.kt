package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.SpeechModelCatalog
import com.kingzcheung.xime.ui.settings.OfflineModelCard
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            val zip = rule.onNodeWithTag("speech-choice:${SpeechModelCatalog.ZIPFORMER}")
            val para = rule.onNodeWithTag("speech-choice:${SpeechModelCatalog.PARAFORMER}")
            val sense = rule.onNodeWithTag("speech-choice:${SpeechModelCatalog.SENSEVOICE}")
            assertTrue(rule.onAllNodes(isSelectable()).fetchSemanticsNodes().size == 2)
            assertTrue(rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 1)
            assertTrue(zip.fetchSemanticsNode().boundsInRoot.top < para.fetchSemanticsNode().boundsInRoot.top)
            assertTrue(para.fetchSemanticsNode().boundsInRoot.top < sense.fetchSemanticsNode().boundsInRoot.top)
            if (manager.selection(SpeechModelCatalog.TWO_PASS).ready && manager.selection(SpeechModelCatalog.ZIPFORMER).ready) {
                para.performScrollTo().performClick()
                if (!manager.isRefinementEnabled()) sense.performScrollTo().performClick()
                assertEquals(SpeechModelCatalog.TWO_PASS, manager.getSelectedModelId())
                zip.performScrollTo().performClick()
                assertEquals(SpeechModelCatalog.ZIPFORMER_TWO_PASS, manager.getSelectedModelId())
                sense.performScrollTo().assertIsOn().performClick().assertIsOff()
                assertEquals(SpeechModelCatalog.ZIPFORMER, manager.getSelectedModelId())
                para.performScrollTo().performClick()
                sense.performScrollTo().assertIsOff()
                assertEquals(SpeechModelCatalog.PARAFORMER, AsrModelManager(rule.activity).getSelectedModelId())
            }
            val output = File(rule.activity.getExternalFilesDir(null), "speech-eval/model-settings.png")
            output.parentFile?.mkdirs()
            rule.onRoot().captureToImage().asAndroidBitmap().useBitmap { bitmap ->
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { manager.setModel(original) }
    }
    private fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
}
