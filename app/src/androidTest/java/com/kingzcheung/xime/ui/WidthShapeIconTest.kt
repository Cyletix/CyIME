package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.ui.keyboard.ToolbarButtonIcon
import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/** 全角/半角图标与桌面版一致：半角月牙、全角满圆，按开关状态切换。 */
class WidthShapeIconTest {
    @get:Rule val rule = createComposeRule()

    @Test fun halfAndFullWidthIconsDiffer() {
        rule.setContent {
            Row(Modifier.background(Color(0xFF303030)).testTag("icons")) {
                val item = ToolbarButtonItem.Builtin(ToolbarButton.FULL_SHAPE)
                ToolbarButtonIcon(item, Color.White, Modifier.size(96.dp).testTag("half"), active = false)
                ToolbarButtonIcon(item, Color.White, Modifier.size(96.dp).testTag("full"), active = true)
            }
        }
        val half = rule.onNodeWithTag("half").captureToImage().asAndroidBitmap()
        val full = rule.onNodeWithTag("full").captureToImage().asAndroidBitmap()
        assertNotEquals("crescent and circle must differ", half.sameAs(full), true)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "width-icons").apply { mkdirs() }
        File(dir, "icons.png").outputStream().use {
            rule.onNodeWithTag("icons").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
