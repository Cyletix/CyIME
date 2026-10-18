package com.kingzcheung.xime.ui

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import java.io.File
import kotlin.math.ceil
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual key bounds and renders, with the calibrated display and no preference writes. */
class WideQwertyRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactAndWideLayoutsUseTheExpectedCentresAndEqualTouchCells() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        val oldOrientation = rule.activity.requestedOrientation
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        try {
            rule.waitUntil(10_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            val target = requireNotNull(keyboardPhysicalMetrics(rule.activity, rule.activity.display, 1f))
                .dpFromMillimeters(CALIBRATED_QWERTY_PITCH_MM)
            val calibratedWidth = ceil(16f + target * 10f).toInt()
            val dimensions = mutableIntStateOf(360)
            val ascii = mutableStateOf(false)
            rule.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalKeyboardExplicitWidth provides true,
                    LocalKeyboardInputPreferences provides KeyboardInputPreferences(splitKeyboardEnabled = false)) {
                    MaterialTheme {
                        val vm = remember { KeyboardViewModel(app) }
                        KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            KeyboardUiState(currentSchemaId = "rime_ice", isAsciiMode = ascii.value, isDarkTheme = true),
                            ascii.value, Modifier.requiredSize(dimensions.intValue.dp, 389.dp).testTag("wide-qwerty-render"))
                    }
                }
            }
            for (english in listOf(false, true)) for (width in listOf(360, (calibratedWidth * .8f).toInt(), calibratedWidth,
                (calibratedWidth * 1.25f).toInt())) {
                rule.runOnIdle { KeysConfigHelper.setActiveKeyboardSchema("rime_ice"); ascii.value = english; dimensions.intValue = width }
                rule.waitForIdle()
                val frame = rule.onNodeWithTag("wide-qwerty-render", true)
                assertEquals("fixture must not silently clamp", width, frame.fetchSemanticsNode().size.width)
                fun bounds(key: String) = rule.onNodeWithTag("qwerty-key:$key", true).fetchSemanticsNode().boundsInRoot
                val q = bounds("q"); val z = bounds("z")
                // A/L deliberately absorb the outside gutter as a touch target.
                // Their labels stay centred in the visible letter cell, not that enlarged target.
                val aCenter = rule.onNodeWithText("a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.x
                val pitch = bounds("w").center.x - q.center.x
                for (key in "qwertyuiopsdfghjkzxcvbnm") {
                    assertEquals("$width/$key cell width", q.width, bounds(key.toString()).width, 1f)
                }
                val aOffset = (aCenter - q.center.x) / pitch
                assertEquals("A retains its outside touch gutter", (1f + aOffset) * pitch, bounds("a").width, 1f)
                assertEquals("L retains its outside touch gutter", (2f - aOffset) * pitch, bounds("l").width, 1f)
                if (width == 360) {
                    assertEquals(.5f * pitch, aCenter - q.center.x, 1f)
                    assertEquals(1.5f * pitch, z.center.x - q.center.x, 1f)
                } else if (width >= calibratedWidth) {
                    assertEquals(.25f * pitch, aCenter - q.center.x, 1f)
                    assertEquals(.75f * pitch, z.center.x - q.center.x, 1f)
                    assertEquals(.5f * pitch, z.center.x - aCenter, 1f)
                    assertEquals(2.25f * pitch, rule.onNodeWithTag("qwerty-delete-key", true).fetchSemanticsNode().boundsInRoot.width, 1f)
                }
                val folder = File(app.getExternalFilesDir(null), "wide-qwerty-calibration").apply { mkdirs() }
                File(folder, "qwerty-$width-${if (english) "en" else "zh"}.png").outputStream().use {
                    frame.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally {
            KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
            rule.runOnUiThread { rule.activity.requestedOrientation = oldOrientation }
        }
    }
}
