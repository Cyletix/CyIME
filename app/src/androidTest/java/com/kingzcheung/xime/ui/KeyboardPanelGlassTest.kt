package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.ui.keyboard.CandidateEntry
import com.kingzcheung.xime.ui.keyboard.CandidatePage
import com.kingzcheung.xime.ui.keyboard.CandidatePageCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidatePageState
import com.kingzcheung.xime.ui.keyboard.EditKeyboardLayout
import com.kingzcheung.xime.ui.keyboard.EmojiKeyboardLayout
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.keyboardPanelBackground
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
import com.kingzcheung.xime.ui.menubar.SchemaListView
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises the real keyboard panels, not merely the material modifier. */
class KeyboardPanelGlassTest {
    @get:Rule val rule = createComposeRule()

    @Test fun keyboardPanelsKeepTheKeyboardBackdropAndTheirActions() {
        var panel by mutableIntStateOf(0)
        var dark by mutableStateOf(true)
        var enabled by mutableStateOf(true)
        var selected = ""
        var selectedCandidate: CandidateEntry? = null
        var editAction = ""
        val schemas = listOf("rime_ice", "t9_pinyin").map { SchemaInfo(it, it, "", "", "") }
        val candidates = listOf(
            CandidateEntry("测试", "ce shi", 17),
            CandidateEntry("候选", "hou xuan", 42),
            CandidateEntry("输入", "shu ru", 87),
        )
        rule.setContent {
            val background = if (dark) Color.Black else Color.White
            val foreground = if (dark) Color.White else Color.Black
            val glass = FrostedGlassConfig.defaults(dark, enabled)
            CompositionLocalProvider(LocalDensity provides Density(1f),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = glass)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme(surface = background, onSurface = foreground)
                    else lightColorScheme(surface = background, onSurface = foreground)) {
                    CompositionLocalProvider(LocalContentColor provides foreground) {
                        Column {
                            Box(Modifier.requiredSize(360.dp, 240.dp).testTag("reference-backdrop")
                                .keyboardPanelBackground(background))
                            Box(Modifier.requiredSize(360.dp, 240.dp).background(Color.Red).testTag("actual-panel")) {
                                when (panel) {
                                    0 -> SchemaListView(schemas, "rime_ice", background, Color.Blue, foreground,
                                        if (dark) Color.DarkGray else Color.LightGray,
                                        onSelectSchema = { selected = it }, modifier = Modifier.fillMaxSize())
                                    1 -> EmojiKeyboardLayout(onEmojiSelect = {}, onBack = {}, backgroundColor = background,
                                        textColor = foreground, accentColor = Color.Blue, modifier = Modifier.fillMaxSize())
                                    2 -> ClipboardBoardView(emptyList(), emptyList(), false, false, emptySet(), false, null,
                                        onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                                        onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                                        onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                                        onPullRemote = null, modifier = Modifier.fillMaxSize())
                                    3 -> CandidatePage(
                                        state = CandidatePageState(candidates = candidates, backgroundColor = background,
                                            textColor = foreground, keyBackgroundColor = if (dark) Color.DarkGray else Color.LightGray),
                                        callbacks = CandidatePageCallbacks(onCandidateSelect = { selectedCandidate = it }),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    else -> EditKeyboardLayout(
                                        onAction = { editAction = it }, onBack = {},
                                        backgroundColor = background, textColor = foreground, accentColor = Color.Blue,
                                        keyBgColor = if (dark) Color.DarkGray else Color.LightGray,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        for (appearance in listOf(true, false)) for (kind in 0..4) {
            rule.runOnIdle { dark = appearance; panel = kind; enabled = true }
            val plain = if (appearance) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            val reference = awaitImage("reference-backdrop") { distance(corner(it), plain) > 5 }
            val image = awaitImage("actual-panel") { distance(corner(it), corner(reference)) <= 2 }
            assertEquals(360, image.width)
            assertEquals(240, image.height)
            val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                "keyboard-panel64-${listOf("scheme", "emoji", "clipboard", "expanded-candidates", "editor")[kind]}-${if (appearance) "dark" else "light"}.png")
            output.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            when (kind) {
                0 -> {
                    rule.onNodeWithTag("panel-scheme:rime_ice").assertIsDisplayed().performClick()
                    rule.runOnIdle { assertEquals("rime_ice", selected) }
                }
                1 -> {
                    rule.onNodeWithTag("expression-kaomoji").assertIsDisplayed().performClick()
                    awaitImage("actual-panel") { distance(corner(it), corner(reference)) <= 2 }
                    rule.onNodeWithTag("expression-emoji").assertIsDisplayed().performClick()
                }
                2 -> rule.onNodeWithTag("clipboard-filter:IMAGE").performClick()
                3 -> {
                    rule.onNodeWithTag("expanded-candidate:42").assertIsDisplayed().performClick()
                    rule.runOnIdle {
                        assertEquals("The expanded panel must preserve the engine's global index", 42, selectedCandidate?.globalIndex)
                        assertEquals(candidates[1], selectedCandidate)
                    }
                }
                else -> {
                    rule.onNodeWithContentDescription("复制").assertIsDisplayed().performClick()
                    rule.runOnIdle { assertEquals("copy", editAction) }
                }
            }
            rule.runOnIdle { enabled = false }
            awaitImage("actual-panel") { distance(corner(it), plain) <= 2 }
            if (kind == 3) {
                rule.onNodeWithTag("expanded-candidate:17").assertIsDisplayed().performClick()
                rule.runOnIdle { assertEquals(candidates[0], selectedCandidate) }
            } else if (kind == 4) {
                rule.onNodeWithContentDescription("粘贴").assertIsDisplayed().performClick()
                rule.runOnIdle { assertEquals("paste", editAction) }
            }
        }
    }

    private fun corner(image: Bitmap): Int = image.getPixel(image.width - 2, image.height - 2)
    private fun distance(a: Int, b: Int) = maxOf(
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
        abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)),
    )
    private fun awaitImage(tag: String, condition: (Bitmap) -> Boolean): Bitmap {
        var result: Bitmap? = null
        rule.waitUntil(timeoutMillis = 15_000) {
            val current = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            if (condition(current)) { result = current; true } else false
        }
        return checkNotNull(result)
    }
}
