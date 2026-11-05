package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 剪贴板长按「翻译」：只在给了翻译入口时出现，交出的是这条记录的原文；密码记录没有这一项。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w800dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardTranslateMenuTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val records = listOf(
        ClipboardItem(1L, "明天见", timestamp = 3),
        ClipboardItem(2L, "secret-pass", timestamp = 2, sensitive = true),
    )

    private fun show(onTranslate: ((String) -> Unit)?) = rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f),
            LocalKeyboardInputPreferences provides KeyboardInputPreferences()) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.requiredSize(360.dp, 300.dp)) {
                    ClipboardBoardView(records, emptyList(), false, false, emptySet(), false, null,
                        onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                        onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                        onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                        onPullRemote = null, onTranslateText = onTranslate)
                }
            }
        }
    }

    private fun longPress(key: String) {
        rule.onNodeWithTag("clipboard-card:$key").performTouchInput { longClick() }
        rule.waitForIdle()
    }

    @Test fun noTranslateEntryWithoutTranslator() {
        show(null)
        rule.waitForIdle()
        longPress("text:1")
        rule.onNodeWithText("分词").assertExists()
        rule.onAllNodesWithTag("clipboard-item-translate").assertCountEquals(0)
    }

    @Test fun translateHandsOverTheRecordText() {
        val asked = mutableListOf<String>()
        show { asked += it }
        rule.waitForIdle()
        longPress("text:1")
        rule.onNodeWithTag("clipboard-item-translate").performClick()
        rule.waitForIdle()
        assertEquals(listOf("明天见"), asked)
        // The menu closes after the tap, as every other entry does.
        rule.onAllNodesWithTag("clipboard-item-translate").assertCountEquals(0)
    }

    @Test fun keptPasswordHasNoTranslateEntry() {
        show { }
        rule.waitForIdle()
        longPress("text:2")
        rule.onNodeWithText("显示").assertExists()
        rule.onAllNodesWithTag("clipboard-item-translate").assertCountEquals(0)
    }
}
