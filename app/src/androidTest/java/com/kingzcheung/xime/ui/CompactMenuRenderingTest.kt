package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.common.CompactActionMenu
import com.kingzcheung.xime.ui.common.CompactActionMenuEntry
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.QuickSendFormArea
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.common.CompactDropdownMenu
import com.kingzcheung.xime.ui.common.CompactMenuRow
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompactMenuRenderingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun glassActionMenusAndDraftsOccludeUnderlyingContentAtEveryOpacity() {
        val dark = mutableStateOf(false)
        val opacity = mutableStateOf(0f)
        val editor = mutableStateOf(false)
        val beneath = mutableStateOf(Color.Black)
        val lightSurface = Color(0xFFF2F2F2)
        val darkSurface = Color(0xFF191C22)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(
                    frostedGlass = FrostedGlassConfig(true, 10f, opacity.value, .29f))) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme(surface = darkSurface, onSurface = Color.White)
                    else lightColorScheme(surface = lightSurface, onSurface = Color(0xFF202020))) {
                    Box(Modifier.fillMaxSize().background(beneath.value)) {
                        Text(("Q W E R T Y U I O P\n").repeat(30),
                            color = if (beneath.value == Color.Black) Color.White else Color.Black)
                        if (editor.value) {
                            Box(Modifier.align(Alignment.BottomCenter).requiredSize(360.dp, 280.dp)) {
                                QuickSendFormArea(backgroundColor = Color.Transparent, textColor = Color.White,
                                    accentColor = MaterialTheme.colorScheme.primary, isFocused = false,
                                    initialText = "已有草稿", cardBgColor = Color(0xFF9CACCC),
                                    onClose = { _, _ -> }, onCancel = {}, onFocusChange = {}, onCodeFocusChange = {})
                            }
                        } else CompactActionMenu(Rect(180f, 180f, 240f, 220f), listOf(
                            CompactActionMenuEntry(Icons.Default.Image, "添加图片", onClick = {}),
                            CompactActionMenuEntry(Icons.Default.DeleteOutline, "删除", onClick = {})), onDismiss = {})
                    }
                }
            }
        }
        for (appearance in listOf(false, true)) for (alpha in listOf(0f, .2f, .8f)) for (draft in listOf(false, true)) {
            rule.runOnIdle { dark.value = appearance; opacity.value = alpha; editor.value = draft }
            val tag = if (draft) "clipboard-text-editor" else "compact-action-menu"
            for (underlay in listOf(Color.Black, Color.White)) {
                rule.runOnIdle { beneath.value = underlay }
                val image = checkNotNull(savePopupEvidence("$tag-$appearance-$alpha-${underlay.toArgb()}", tag))
                rule.onNodeWithTag(tag).assertIsDisplayed()
                val expected = (if (appearance) darkSurface else lightSurface).toArgb()
                assertTrue("$tag has a measurable reading surface", image.width > 12 && image.height > 40)
                // Stay outside the dp-sized rounded corners even when a Popup has another density.
                for (y in image.height / 2 - 4..image.height / 2 + 4) for (x in 4..6) {
                    assertEquals("$tag reading surface must occlude lower content at opacity=$alpha dark=$appearance x=$x y=$y",
                        expected, image.getPixel(x, y))
                }
                if (draft) rule.runOnIdle {
                    assertEquals("draft text uses the matching theme foreground",
                        (if (appearance) Color.White else Color(0xFF202020)).toArgb(),
                        com.kingzcheung.xime.service.QuickSendFormEditTextHolder.editText?.currentTextColor)
                }
            }
        }
    }

    /** Preserve diagnostics before any display/pixel assertion, including missing-menu failures. */
    private fun savePopupEvidence(name: String, tag: String): android.graphics.Bitmap? {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null),
            "popup-reading-surfaces").apply { mkdirs() }
        val roots = rule.onAllNodes(isRoot(), useUnmergedTree = true)
        val nodes = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        val report = buildString {
            appendLine("physicalDensity=${instrumentation.targetContext.resources.displayMetrics.density}")
            nodes.forEach { appendLine("$tag screen=${it.positionOnScreen} size=${it.size} root=${it.boundsInRoot}") }
            if (nodes.isEmpty()) appendLine("$tag is absent")
            roots.fetchSemanticsNodes().indices.forEach { index -> appendLine(roots[index].printToString()) }
        }
        java.io.File(directory, "$name.txt").writeText(report)
        instrumentation.uiAutomation.takeScreenshot()?.let { image ->
            java.io.File(directory, "$name-screen.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
        }
        if (nodes.isEmpty()) return null
        val image = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        java.io.File(directory, "$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        return image
    }

    @Test fun sharedRowsKeepLabelsAndStatesVisibleInBothThemesAndLargeFonts() {
        val dark = mutableStateOf(false)
        val scale = mutableStateOf(1f)
        val invoked = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale.value)) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.requiredSize(360.dp, 500.dp)) {
                        Box(Modifier.requiredSize(40.dp, 40.dp)) {
                            CompactDropdownMenu(true, {}, Modifier.testTag("shared-menu")) {
                                CompactMenuRow("添加图片", { invoked += "add" }, Modifier.testTag("add"), Icons.Default.Image)
                                CompactMenuRow("同步", { invoked += "sync" }, Modifier.testTag("disabled"),
                                    Icons.Default.Sync, enabled = false)
                                CompactMenuRow("删除", { invoked += "delete" }, Modifier.testTag("delete"),
                                    Icons.Default.DeleteOutline, tint = MaterialTheme.colorScheme.error)
                                CompactMenuRow("小鹤双拼", { invoked += "select" }, Modifier.testTag("selected"),
                                    selected = true, supportingText = "double_pinyin_flypy")
                            }
                        }
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) for (fontScale in listOf(1f, 2f)) {
            rule.runOnIdle { dark.value = isDark; scale.value = fontScale }
            rule.onNodeWithTag("disabled").assertIsNotEnabled()
            rule.onNodeWithTag("selected").assertIsSelected()
            val menu = rule.onNodeWithTag("shared-menu").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            var priorBottom = menu.top
            for ((tag, label) in listOf("add" to "添加图片", "disabled" to "同步", "delete" to "删除", "selected" to "小鹤双拼")) {
                val row = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val text = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("$tag has a compact but usable hit area", row.height >= 36f - 1f)
                assertTrue("$tag stays inside menu", row.left >= menu.left && row.right <= menu.right && row.bottom <= menu.bottom)
                assertTrue("$tag does not overlap previous row", row.top >= priorBottom)
                assertTrue("$label is not clipped at fontScale=$fontScale", text.top >= row.top && text.bottom <= row.bottom)
                priorBottom = row.bottom
            }
            val image = rule.onNodeWithTag("shared-menu").captureToImage().asAndroidBitmap()
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            val error = (if (isDark) darkColorScheme() else lightColorScheme()).error.toArgb()
            assertTrue("destructive icon/text retain the theme's error color", pixels.any { it == error })
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ui-verification").apply { mkdirs() }
            File(directory, "shared-menu-${if (isDark) "dark" else "light"}-$fontScale.png").outputStream().use {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        rule.onNodeWithTag("disabled").performTouchInput { click() }
        rule.onNodeWithTag("add").performClick()
        rule.onNodeWithTag("delete").performClick()
        rule.onNodeWithTag("selected").performClick()
        rule.runOnIdle { assertEquals(listOf("add", "delete", "select"), invoked) }
    }
}
