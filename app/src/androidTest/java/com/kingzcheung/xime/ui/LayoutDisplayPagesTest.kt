package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.settings.LayoutDisplayPages
import com.kingzcheung.xime.ui.settings.LayoutDisplaySettingsContent
import com.kingzcheung.xime.ui.settings.LayoutInputCategory
import com.kingzcheung.xime.ui.settings.LayoutInputSettingsContent
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 布局与显示 is a short index; every group opens on its own page and back returns to the index.
 *  14 键、九键、假名提示和拂动圆盘合在「九键与 14 键」一页。 */
class LayoutDisplayPagesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun indexOpensEachGroupAndFlickHintsLiveUnderNineKey() {
        rule.setContent { MaterialTheme { LayoutDisplaySettingsContent(onBack = {}) } }
        rule.onNodeWithText("通用设置").assertExists()
        rule.onNodeWithText("九键与 14 键").assertExists()
        rule.onNodeWithText("14 键").assertDoesNotExist()
        save("index")
        for ((_, title, _) in LayoutDisplayPages) {
            rule.onNodeWithText(title).performScrollTo().performClick()
            rule.waitForIdle()
            save("page-$title")
            rule.onNodeWithTag("kana-direction-hints").assertDoesNotExist()
            rule.onNodeWithTag("flick-direction-disc").assertDoesNotExist()
            rule.onNodeWithContentDescription("返回").performClick()
            rule.onNodeWithText("通用设置").assertExists()
        }
    }

    @Test fun nineKeyPageHoldsFourteenKeyFlickAndKanaSettings() {
        rule.setContent { MaterialTheme { LayoutInputSettingsContent(LayoutInputCategory.Nine, onBack = {}) } }
        rule.onNodeWithText("拂动选字母").assertExists()
        rule.onNodeWithText("左右滑动选字母").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("fourteen-letter-distance").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("flick-direction-disc").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("kana-direction-hints").performScrollTo().assertIsDisplayed()
        save("page-nine-fourteen")
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "layout-display-pages").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
