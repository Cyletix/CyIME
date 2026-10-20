package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 折叠的固定内容：分隔线上有带箭头的“展开 N 条”，点线即可展开/收起。深浅色、玻璃开关都截图。 */
class ClipboardPinsFoldArrowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dividerArrowExpandsAndCollapsesPins() {
        val records = (1L..10L).map { ClipboardItem(it, "固定记录 $it", timestamp = 20 - it) }
        val history = records + ClipboardItem(11L, "最近复制", timestamp = 1)
        var dark by mutableStateOf(true)
        var glass by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(
                    frostedGlass = FrostedGlassConfig.defaults(dark, enabled = glass))) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.requiredSize(360.dp, 300.dp).background(if (glass) Brush.horizontalGradient(
                        listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5)))
                        else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)))
                        .testTag("host")) {
                        ClipboardBoardView(history, emptyList(), false, false,
                            records.mapTo(mutableSetOf()) { "text:${it.id}" }, false, null,
                            onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                            onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                            onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                            onPullRemote = null)
                    }
                }
            }
        }
        for (d in listOf(true, false)) for (g in listOf(false, true)) {
            rule.runOnIdle { dark = d; glass = g }
            rule.waitForIdle()
            val name = "${if (d) "dark" else "light"}-${if (g) "glass" else "plain"}"
            rule.onNodeWithText("展开 8 条", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("clipboard-card:text:10").assertDoesNotExist()
            save("$name-collapsed")
            rule.onNodeWithTag("clipboard-pins-divider").performClick()
            rule.waitForIdle()
            rule.onAllNodesWithText("收起", useUnmergedTree = true).assertCountEquals(2)
            rule.onNodeWithTag("clipboard-records").performScrollToKey("pins-end")
            save("$name-expanded")
            rule.onNodeWithTag("clipboard-pins-divider").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("clipboard-card:text:10").assertDoesNotExist()
        }
    }

    private fun save(name: String) {
        val bitmap = rule.onNodeWithTag("host").captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "clipboard-pins-fold").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
