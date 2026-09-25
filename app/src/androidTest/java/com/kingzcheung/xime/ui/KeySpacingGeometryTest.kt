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
                        (280 * factor.floatValue).dp).background(Color.Black).testTag("spacing-root"),
                        columns = 4f, rows = 2f, horizontalInset = 0.dp, verticalInset = 0.dp) { body ->
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
            // 新模型：缝 = 布局策略目标值（QWERTY 4.5/5.5dp）按格短边有限缩放（1.0~1.2x），两侧各一半
            val cellWidth = 420f * size / 4f
            val cellHeight = 280f * size / 2f
            val scale = (minOf(cellWidth, cellHeight) / KeyVisualPolicy.ReferenceCell).coerceIn(1f, KeyVisualPolicy.ScaleMax)
            val expectedX = KeyVisualPolicy.Qwerty.gapX * scale / 2f
            val expectedY = KeyVisualPolicy.Qwerty.gapY * scale / 2f
            insets.forEach { (tag, inset) ->
                assertEquals("$tag horizontal inset", expectedX, inset.first, 1f)
                assertEquals("$tag vertical inset", expectedY, inset.second, 1f)
            }
            if (size == 1f) phoneInsets = insets
        }
        // 键帽变大时缝不跟着放大（缩放封顶 1.2），只保留整数化误差
        phoneInsets.forEach { (tag, inset) ->
            assertEquals("$tag capped horizontal ratio", inset.first, 4.5f * 1.2f / 2f, 1f)
            assertEquals("$tag capped vertical ratio", inset.second, 5.5f * 1.2f / 2f, 1f)
        }
    }

    @Test fun realLayoutsUseEqualPaintedGapsAndTheSameKeySizeRatio() {
        data class Mode(val schema: String, val ascii: Boolean = false, val split: Boolean = false)
        val modes = listOf(Mode("rime_ice", true), Mode("rime_ice"), Mode("pinyin_14jian"),
            Mode("t9_pinyin"), Mode("jaroomaji"), Mode("japanese_kana"), Mode("rime_ice", true, true))
        val mode = mutableStateOf(modes.first())
        val dimensions = mutableStateOf(420 to 280)
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
                        val callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {})
                        Box(Modifier.size(dimensions.value.first.dp, dimensions.value.second.dp)
                            .background(Color.Black).testTag("actual-grid")) {
                            val body = Modifier.fillMaxSize()
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
        }
        val report = StringBuilder()
        for (current in modes) for (size in listOf(320 to 240, 420 to 280, 1000 to 360, 840 to 560)) {
            rule.runOnIdle {
                KeysConfigHelper.setActiveKeyboardSchema(current.schema)
                mode.value = current
                dimensions.value = size
            }
            rule.waitForIdle()
            val rows = KeysConfigHelper.getKeyRows(current.ascii)
            val labels = when (current.schema) {
                "japanese_kana" -> listOf("あ", "か", "た")
                "t9_pinyin" -> listOf("ABC", "DEF", "JKL")
                else -> listOf(rows[0][0], rows[0][1], rows[1][0]).map {
                    KeysConfigHelper.getKeyDisplayLabel(it, current.ascii, false)
                }
            }
            val root = rule.onNodeWithTag("actual-grid", true)
            val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
            val bitmap = root.captureToImage().asAndroidBitmap()
            val cellHeight = (size.second - if (current.schema == "japanese_kana") 0 else 8) / 4f
            // Text spans the keycap width. Measure its painted vertical extent from the screenshot,
            // so this catches extra row gaps, not just the formula used by production code.
            fun cap(label: String, row: Int): Rect {
                val text = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    .translate(-origin.x, -origin.y)
                val left = text.left.toInt() + 1
                val right = text.right.toInt() - 1
                val colors = (left until right).map { bitmap.getPixel(it, text.center.y.toInt()) }
                val background = colors.groupingBy { it }.eachCount().maxBy { it.value }.key
                val paintedRows = ((row * cellHeight).roundToInt() until ((row + 1) * cellHeight).roundToInt()).filter { y ->
                    (left until right).count { bitmap.getPixel(it, y) == background } > (right - left) * 0.05f
                }
                return Rect(text.left, paintedRows.first().toFloat(), text.right, paintedRows.last() + 1f)
            }
            val a = cap(labels[0], 0); val b = cap(labels[1], 0); val below = cap(labels[2], 1)
            val gx = b.left - a.right; val gy = below.top - a.bottom
            val cellWidth = a.width + gx
            // 新模型：缝来自布局策略（26键 4.5/5.5、14键 5/6、九宫格 6/6），按格短边有限缩放
            val policy = when (current.schema) {
                "pinyin_14jian" -> KeyVisualPolicy.FourteenKey
                "t9_pinyin", "japanese_kana" -> KeyVisualPolicy.T9
                else -> KeyVisualPolicy.Qwerty
            }
            val scale = (minOf(cellWidth, cellHeight) / KeyVisualPolicy.ReferenceCell)
                .coerceIn(1f, KeyVisualPolicy.ScaleMax)
            val expectedX = (policy.gapX * scale).coerceIn(policy.minGapX, policy.maxGapX)
            val expectedY = (policy.gapY * scale).coerceIn(policy.minGapY, policy.maxGapY)
            val note = "${current.schema} ascii=${current.ascii} split=${current.split} $size gap=($gx,$gy) " +
                "expected=($expectedX,$expectedY) cell=($cellWidth,$cellHeight)"
            report.appendLine(note)
            assertEquals("$note gapX", expectedX, gx, 1.6f)
            assertEquals("$note gapY", expectedY, gy, 1.6f)
            saveSpacingImage("grid-${current.schema}-${current.ascii}-${current.split}-${size.first}", bitmap)
        }
        File(app.getExternalFilesDir(null), "spacing-measurements.txt").writeText(report.toString())
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
            // 新模型：QWERTY 策略 4.5/5.5dp 目标缝，按格短边在 1.0~1.2x 内有限缩放
            val cellWidth = 420f * size / 5f
            val cellHeight = if (panel == "edit") 280f * size / 3f else (280f * size - 8f) / 4f
            val scale = (minOf(cellWidth, cellHeight) / KeyVisualPolicy.ReferenceCell).coerceIn(1f, KeyVisualPolicy.ScaleMax)
            assertEquals("$panel horizontal inset size=$size", KeyVisualPolicy.Qwerty.gapX * scale / 2f, insets.first, 1f)
            assertEquals("$panel vertical inset size=$size", KeyVisualPolicy.Qwerty.gapY * scale / 2f, insets.second, 1f)
            assertTrue("$panel longitudinal gap larger than lateral", insets.second >= insets.first)
        }
    }

    @Test fun smallNestedCardUsesItsOwnBoundsAndNeverMultipliesTheParentScale() {
        var nested = KeyVisualMetrics.Unspecified
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KeyboardKeySpacingScope(Modifier.size(840.dp, 560.dp)) {
                    KeyboardKeySpacingScope(Modifier.size(320.dp, 240.dp)) { _ ->
                        val metrics = LocalKeyboardKeyVisualMetrics.current
                        SideEffect { nested = metrics }
                    }
                }
            }
        }
        // 320×240、默认 10 列 4 行：格 32×58dp → 缩放取 1.0（不缩小），缝用策略目标值
        rule.runOnIdle {
            assertEquals(1f, nested.scale, 0.001f)
            assertEquals(KeyVisualPolicy.Qwerty.gapX / 2f, nested.insetX!!, 0.01f)
            assertEquals(KeyVisualPolicy.Qwerty.gapY / 2f, nested.insetY!!, 0.01f)
        }
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
