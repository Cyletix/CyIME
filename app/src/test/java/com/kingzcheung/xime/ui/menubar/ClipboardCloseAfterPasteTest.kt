package com.kingzcheung.xime.ui.menubar

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.ClipboardItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The "close the clipboard after a paste" switch: on by default, toggled from the board's more menu. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w800dp-h700dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardCloseAfterPasteTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("clipboard_board", Context.MODE_PRIVATE)

    @Test fun onByDefaultSoTheClipboardClosesAsBefore() {
        prefs.edit().clear().commit()
        assertTrue(clipboardClosesAfterPaste(prefs))
        prefs.edit().putBoolean(CLIPBOARD_CLOSE_AFTER_PASTE_KEY, false).commit()
        assertFalse(clipboardClosesAfterPaste(ApplicationProvider.getApplicationContext<Context>()))
    }

    @Test fun theMoreMenuTogglesIt() {
        val changes = mutableListOf<Boolean>()
        val items = listOf(ClipboardItem(1, "送货地址改成公司前台", timestamp = 2), ClipboardItem(2, "明天下午三点开会", timestamp = 1))
        rule.setContent {
            Row(Modifier.background(Color(0xFF606060)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (dark in listOf(false, true)) Box(Modifier.width(380.dp).height(420.dp).padding(4.dp)) {
                    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                        var on by remember { mutableStateOf(true) }
                        ClipboardBoardView(items, emptyList(), false, false, emptySet(), false, null,
                            onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {}, onSplit = { _, _ -> },
                            onAddQuick = {}, onPinsChange = {}, onRemove = {}, onPhotoAccess = {}, onPick = {},
                            onSystemPaste = {}, onShare = { _, _ -> }, onPullRemote = null,
                            closeAfterPaste = on, onCloseAfterPasteChange = { on = it; changes += it },
                            modifier = Modifier.testTag(if (dark) "dark" else "light"))
                    }
                }
            }
        }
        rule.onAllNodesWithTag("clipboard-more")[0].performClick()
        rule.onAllNodesWithTag("clipboard-more")[1].performClick()
        rule.waitForIdle()
        System.getenv("CYIME_RENDER_OUT")?.takeIf { it.isNotEmpty() }?.let { out ->
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out).mkdirs()
            File(out, "clipboard-close-after-paste-menu.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.onAllNodesWithTag("clipboard-close-after-paste")[0].performClick()
        rule.waitForIdle()
        assertEquals(listOf(false), changes)
    }
}
