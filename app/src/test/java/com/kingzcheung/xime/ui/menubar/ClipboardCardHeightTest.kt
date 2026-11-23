package com.kingzcheung.xime.ui.menubar

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.ClipboardImage
import com.kingzcheung.xime.clipboard.ClipboardImageHeight
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.clipboard.clipboardImageCardHeight
import com.kingzcheung.xime.ui.keyboard.ClipboardPanelBounds
import com.kingzcheung.xime.ui.keyboard.ClipboardPanelExpansion
import com.kingzcheung.xime.ui.keyboard.LocalClipboardPanelExpansion
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 一行的剪贴板记录和固定行一样高，字在卡片正中；之前 48dp 的最低高度让字贴上边、下面空一条（用户 10-30）。
 * 多行的记录照常按字长高。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardCardHeightTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun oneLineRecordIsAPinnedRowTallWithItsTextCentred() {
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        val short = ClipboardItem(1, "羡慕不用上班的富哥们", timestamp = 1000)
        val long = ClipboardItem(2, "很长的一段剪贴板文字，".repeat(12), timestamp = 999)
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                ClipboardBoardView(listOf(short, long), emptyList(), false, false, emptySet(), false, null,
                    onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                    onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                    onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                    onPullRemote = null,
                    modifier = Modifier.width(400.dp).height(600.dp))
            }
        }
        rule.waitForIdle()
        val card = rule.onNodeWithTag("clipboard-card:text:1").getUnclippedBoundsInRoot()
        val text = rule.onNodeWithText(short.text, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardHeight = card.bottom - card.top
        assertTrue("一行记录高 $cardHeight", cardHeight.value <= ClipboardPinnedRowHeight.value + .5f)
        val cardCentre = (card.top + card.bottom) / 2
        val textCentre = (text.top + text.bottom) / 2
        assertEquals("字的中线偏了", cardCentre.value, textCentre.value, .5f)

        val longCard = rule.onNodeWithTag("clipboard-card:text:2").getUnclippedBoundsInRoot()
        assertTrue("多行记录照常长高", (longCard.bottom - longCard.top).value > 2 * ClipboardPinnedRowHeight.value)
    }

    @Test fun expandedImageCardUsesCollapsedBoardRecordsHeight() {
        KeyboardThemes.initFromConfig(ApplicationProvider.getApplicationContext<Context>())
        val img = ClipboardImage("content://test/pic1", "image/png", 1000L)
        val panel = ClipboardPanelExpansion(
            ClipboardPanelBounds(240, 560),
            CoroutineScope(Dispatchers.Main + BroadcastFrameClock()),
        ) {}
        panel.animateTo(true)
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalClipboardPanelExpansion provides panel,
                    LocalClipboardCandidateChromeDp provides 42f,
                ) {
                    ClipboardBoardView(emptyList(), listOf(img), false, false, emptySet(), false, null,
                        imageHeight = ClipboardImageHeight.LOW,
                        onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                        onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                        onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                        onPullRemote = null,
                        modifier = Modifier.width(400.dp).height((560 - 42).dp))
                }
            }
        }
        rule.waitForIdle()
        val card = rule.onNodeWithTag("clipboard-card:image:content://test/pic1").getUnclippedBoundsInRoot()
        val cardHeight = (card.bottom - card.top).value
        val collapsedCap = clipboardImageCardHeight(
            ClipboardImageHeight.LOW,
            widthDp = (400f - 12f - 6f) / 2f,
            recordsHeightDp = 240f - 42f - 40f,
            aspect = null,
        )
        assertTrue("展开后图片卡片不应拉成展开容器高: card=$cardHeight, collapsedCap=$collapsedCap",
            cardHeight <= collapsedCap + 10f)
        panel.dispose()
    }
}
