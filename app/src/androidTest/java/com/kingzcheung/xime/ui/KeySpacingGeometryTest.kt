package com.kingzcheung.xime.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.floor
import java.io.File

class KeySpacingGeometryTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @Before fun before() { KeysConfigHelper.loadConfig(app) }
    @After fun after() { KeysConfigHelper.setActiveKeyboardSchema("rime_ice") }

    @Test fun paintedInsetsScaleEquallyForLettersTallEnterAndWideSpaceWithoutShrinkingTouchBounds() {
        val factor = mutableFloatStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    KeyboardKeySpacingScope(Modifier.size((420 * factor.floatValue).dp,
                        (280 * factor.floatValue).dp).background(Color.Black).testTag("spacing-root")) { body ->
                        Row(body) {
                            Column(Modifier.weight(1f)) {
                                KeyButton("A", {}, Color.Cyan, Color.White, Modifier.weight(1f).testTag("letter"), shadowEnabled = false)
                                KeyButton("B", {}, Color.Cyan, Color.White, Modifier.weight(1f), shadowEnabled = false)
                            }
                            ActionKeyButton("回车", {}, Color.Cyan, Color.White,
                                Modifier.weight(1f).testTag("tall-enter"), shadowEnabled = false)
                            SpaceKeyButton({}, Color.Cyan, Color.White, "", modifier = Modifier.weight(2f).testTag("wide-space"), shadowEnabled = false)
                        }
                    }
                }
            }
        }
        var phoneInsets: Map<String, Pair<Float, Float>> = emptyMap()
        for (size in listOf(1f, 2f)) {
            rule.runOnIdle { factor.floatValue = size }
            rule.waitForIdle()
            val root = rule.onNodeWithTag("spacing-root", useUnmergedTree = true)
            val bitmap = root.captureToImage().asAndroidBitmap()
            val rootBounds = root.fetchSemanticsNode().boundsInRoot
            saveSpacingImage("spacing-insets-${size.toInt()}x", bitmap)
            val insets = listOf("letter", "tall-enter", "wide-space").associateWith { tag ->
                val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val expectedWidth = (if (tag == "wide-space") 210 else 105) * size
                assertEquals("$tag full touch width", expectedWidth, bounds.width, 1f)
                assertEquals("$tag full touch height", (if (tag == "letter") 140 else 280) * size, bounds.height, 1f)
                val relative = bounds.translate(-rootBounds.left, -rootBounds.top)
                val measured = paintedInsets(bitmap, relative)
                android.util.Log.i("KeySpacingGeometry", "size=$size tag=$tag root=$rootBounds key=$bounds relative=$relative " +
                    "bitmap=${bitmap.width}x${bitmap.height} measured=$measured " + edgePixels(bitmap, relative))
                measured
            }
            insets.forEach { (tag, inset) ->
                assertEquals("$tag horizontal inset", 2f * size, inset.first, 1f)
                assertEquals("$tag vertical inset", 4.25f * size, inset.second, 1f)
                if (size == 2f) {
                    assertEquals("$tag horizontal ratio", phoneInsets.getValue(tag).first * 2f, inset.first, 1f)
                    assertEquals("$tag vertical ratio", phoneInsets.getValue(tag).second * 2f, inset.second, 1.1f)
                }
            }
            if (size == 1f) phoneInsets = insets
        }
    }

    @Test fun realChineseJapaneseAndSplitLayoutsScaleNeighbouringKeycapGaps() {
        data class Mode(val schema: String, val ascii: Boolean = false, val split: Boolean = false)
        val modes = listOf(Mode("rime_ice", true), Mode("rime_ice"), Mode("pinyin_14jian"),
            Mode("t9_pinyin"), Mode("japanese"), Mode("japanese_kana"), Mode("rime_ice", true, true))
        val mode = mutableStateOf(modes.first())
        val factor = mutableFloatStateOf(1f)
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 1400; screenHeightDp = 1000; orientation = Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(1f),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(splitKeyboardEnabled = mode.value.split)) {
                MaterialTheme {
                    key(mode.value) {
                        val vm = remember { KeyboardViewModel(app) }
                        val ui = KeyboardUiState(currentSchemaId = mode.value.schema, isAsciiMode = mode.value.ascii)
                        val body = Modifier.size((420 * factor.floatValue).dp, (280 * factor.floatValue).dp)
                        val callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {})
                        when (mode.value.schema) {
                            "japanese_kana" -> JapaneseKanaKeyboardLayout({}, {}, Color.Cyan, Color.White, Color.Blue,
                                modifier = body, shadowEnabled = false)
                            "t9_pinyin" -> T9KeyboardLayout({}, callbacks, ui, remember { T9InputController() },
                                Color.Cyan, Color.White, Color.Blue, modifier = body, shadowEnabled = false, isFloatingMode = true)
                            else -> KeyboardLayout({}, vm, callbacks, ui, mode.value.ascii, body)
                        }
                    }
                }
            }
        }
        for (current in modes) {
            rule.runOnIdle {
                KeysConfigHelper.setActiveKeyboardSchema(current.schema)
                mode.value = current
                factor.floatValue = 1f
            }
            rule.waitForIdle()
            val labels = when (current.schema) {
                "japanese_kana" -> "あ" to "か"
                "t9_pinyin" -> "ABC" to "DEF"
                else -> KeysConfigHelper.getKeyRows(current.ascii).first().take(2).map {
                    KeysConfigHelper.getKeyDisplayLabel(it, current.ascii, false)
                }.let { it[0] to it[1] }
            }
            fun gap(): Float {
                val first = rule.onNodeWithText(labels.first, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val second = rule.onNodeWithText(labels.second, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                return second.left - first.right
            }
            val phoneGap = gap()
            assertTrue("${current.schema} phone gap", phoneGap > 0f)
            rule.runOnIdle { factor.floatValue = 2f }
            rule.waitForIdle()
            assertEquals("${current.schema} split=${current.split} gap must grow with body", phoneGap * 2f, gap(), 1.5f)
        }
    }

    @Test fun handwritingAndEditingAuxiliaryKeysUseTheSameBodyScale() {
        val mode = mutableStateOf("edit")
        val factor = mutableFloatStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    val body = Modifier.size((420 * factor.floatValue).dp, (280 * factor.floatValue).dp)
                        .background(Color.Black).testTag("aux-spacing-root")
                    if (mode.value == "edit") EditKeyboardLayout({}, {}, Color.Black, Color.White, Color.Blue,
                        Color.Cyan, modifier = body, shadowEnabled = false)
                    else HandwritingKeyboardLayout(keyBackgroundColor = Color.Cyan,
                        specialKeyBackgroundColor = Color.Cyan, keyTextColor = Color.White,
                        modifier = body, bottomPaddingDp = 0)
                }
            }
        }
        for (panel in listOf("edit", "handwriting")) for (size in listOf(1f, 2f)) {
            rule.runOnIdle { mode.value = panel; factor.floatValue = size }
            rule.waitForIdle()
            val root = rule.onNodeWithTag("aux-spacing-root", useUnmergedTree = true)
            val bitmap = root.captureToImage().asAndroidBitmap()
            val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
            val key = if (panel == "edit") rule.onNodeWithContentDescription("删除", useUnmergedTree = true)
                else rule.onNodeWithTag("handwriting-key:delete", useUnmergedTree = true)
            val insets = paintedInsets(bitmap, key.fetchSemanticsNode().boundsInRoot.translate(-origin.x, -origin.y))
            assertEquals("$panel horizontal gap", 2 * size, insets.first, 1f)
            assertEquals("$panel vertical gap", 2 * size, insets.second, 1f)
        }
    }

    @Test fun smallNestedCardUsesItsOwnBoundsAndNeverMultipliesTheParentScale() {
        var measured = KeyboardKeySpacingScale(0f, 0f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KeyboardKeySpacingScope(Modifier.size(840.dp, 560.dp)) {
                    KeyboardKeySpacingScope(Modifier.size(320.dp, 240.dp)) { _ ->
                        val scale = LocalKeyboardKeySpacingScale.current
                        SideEffect { measured = scale }
                    }
                }
            }
        }
        rule.runOnIdle { assertEquals(KeyboardKeySpacingScale(), measured) }
    }

    private fun saveSpacingImage(name: String, bitmap: Bitmap) {
        val directory = File(app.getExternalFilesDir(null), "cyime").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun edgePixels(bitmap: Bitmap, rect: Rect): String {
        val x = rect.left.roundToInt().coerceAtLeast(0)
        val y = rect.top.roundToInt().coerceAtLeast(0)
        val cx = rect.center.x.roundToInt().coerceIn(0, bitmap.width - 1)
        val cy = rect.center.y.roundToInt().coerceIn(0, bitmap.height - 1)
        val left = (x until minOf(x + 12, bitmap.width)).joinToString { Integer.toHexString(bitmap.getPixel(it, cy)) }
        val top = (y until minOf(y + 16, bitmap.height)).joinToString { Integer.toHexString(bitmap.getPixel(cx, it)) }
        return "left=[$left] top=[$top]"
    }

    private fun paintedInsets(bitmap: Bitmap, rect: Rect): Pair<Float, Float> {
        val left = floor(rect.left).toInt().coerceAtLeast(0)
        val top = floor(rect.top).toInt().coerceAtLeast(0)
        val centerX = rect.center.x.roundToInt().coerceIn(0, bitmap.width - 1)
        val centerY = rect.center.y.roundToInt().coerceIn(0, bitmap.height - 1)
        // Fixtures paint cyan over black. G/B therefore measure the key's pixel coverage.
        // Summing uncovered fractions includes antialiased boundary pixels instead of rounding
        // the phone's first fully opaque pixel up and then doubling that rounding error.
        fun coverage(pixel: Int): Float = minOf(android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel)) / 255f
        val firstX = (left until rect.right.roundToInt()).first { coverage(bitmap.getPixel(it, centerY)) == 1f }
        val firstY = (top until rect.bottom.roundToInt()).first { coverage(bitmap.getPixel(centerX, it)) == 1f }
        val horizontal = (left until firstX).sumOf { (1f - coverage(bitmap.getPixel(it, centerY))).toDouble() }.toFloat()
        val vertical = (top until firstY).sumOf { (1f - coverage(bitmap.getPixel(centerX, it))).toDouble() }.toFloat()
        return (horizontal - (rect.left - left)) to (vertical - (rect.top - top))
    }
}
