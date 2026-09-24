package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.data.EmojiCategory
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.ui.keyboard.EmojiKeyboardLayout
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test

class EmojiPanelNavigationTest {
    @get:Rule val rule = createComposeRule()
    @Suppress("UNCHECKED_CAST")
    private val categories = ExtensionManager::class.java.getDeclaredField("_emojiCategoriesFlow").apply { isAccessible = true }
        .get(ExtensionManager) as MutableStateFlow<List<EmojiCategory>>
    private val saved = categories.value
    @After fun restoreCategories() { categories.value = saved }

    private fun show(items: List<EmojiCategory>) {
        categories.value = items
        rule.setContent { MaterialTheme {
            EmojiKeyboardLayout(onEmojiSelect = {}, onBack = {}, backgroundColor = Color(0xFF211E29),
                textColor = Color.White, accentColor = Color(0xFFCEB7F7), modifier = Modifier.size(400.dp, 184.dp).testTag("emoji-panel"))
        } }
    }

    @Test fun builtinEmojiStartsAtTheTopWithoutASingleGroupTab() {
        show(com.kingzcheung.xime.data.EmojiData.categories)
        rule.onNodeWithTag("emoji-groups").assertDoesNotExist()
        val panel = rule.onNodeWithTag("emoji-panel").fetchSemanticsNode().boundsInRoot
        val pages = rule.onNodeWithTag("emoji-pages").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(panel.top, pages.top, 1f)
        rule.onNodeWithText("😀").performClick()
        rule.waitForIdle()
        val bitmap = rule.onNodeWithTag("emoji-panel").captureToImage().asAndroidBitmap()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "emoji-preview.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun swipingFromBuiltinsEntersTheFirstPluginCategoryWithoutSkippingIt() {
        show(listOf(EmojiCategory("内置", "内", emojis = listOf("内置项")),
            EmojiCategory("第一页", "插", emojis = listOf("甲"), isPlugin = true, pluginId = "test"),
            EmojiCategory("第二页", "插", emojis = listOf("乙"), isPlugin = true, pluginId = "test")))
        rule.onNodeWithText("内").performClick()
        rule.onNodeWithText("内置项").assertIsDisplayed()
        rule.onNodeWithTag("emoji-pages").performTouchInput { swipeLeft() }
        rule.onNodeWithText("甲").assertIsDisplayed()
        rule.onNodeWithTag("emoji-pages").performTouchInput { swipeLeft() }
        rule.onNodeWithText("乙").assertIsDisplayed()
        rule.onNodeWithTag("emoji-pages").performTouchInput { swipeRight() }
        rule.onNodeWithText("甲").assertIsDisplayed()
    }

    @Test fun pluginTabsRemainReachableWhenThereAreMoreThanThePanelCanFit() {
        show((0..11).map { i -> EmojiCategory("分类$i", "P$i", emojis = listOf("结果$i"),
            isPlugin = true, pluginId = "plugin$i") })
        rule.onNodeWithText("P11").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("结果11").assertIsDisplayed()
    }
}
