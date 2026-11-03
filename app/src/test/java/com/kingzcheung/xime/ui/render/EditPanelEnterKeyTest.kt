package com.kingzcheung.xime.ui.render

import android.app.Application
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.EditKeyboardLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 编辑面板的回车键和数字、符号面板一样跟随输入框动作（搜索框显示搜索），不再写死「回车」。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w900dp-h500dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditPanelEnterKeyTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun enterKeyFollowsFieldAction() {
        KeysConfigHelper.loadConfig(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent {
            EditKeyboardLayout(onAction = {}, onBack = {}, backgroundColor = Color.Black,
                textColor = Color.White, accentColor = Color.Blue, keyBgColor = Color.DarkGray,
                showBackKey = false, modifier = Modifier.requiredSize(360.dp, 250.dp), enterKeyText = "搜索")
        }
        rule.onNodeWithText("搜索").assertExists()
        rule.onAllNodesWithText("回车").assertCountEquals(0)
    }
}
