package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ClosedCaption
import androidx.compose.material.icons.twotone.Dialpad
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.settings.SettingsSection
import com.kingzcheung.xime.ui.settings.SettingsToggleItem
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 「标题＋说明＋开关」整行开关（SettingsToggleItem）：说明写满一行时，文字右边和开关之间至少留 12dp。
 * 用按设备推荐配置里最长的两行说明，手机宽度 360dp。总是检查间距；设置了 CYIME_RENDER_OUT 时另出浅色、深色图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsToggleGapRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val rows = listOf(
        "用九键打字" to "九键按整句常用说法组词，长句更容易一次打对；需要九键整句组词模型",
        "用字幕" to "开会、和听力不好的人说话时，把别人说的话实时显示在屏幕上，并分清是谁说的",
    )

    @Test fun descriptionEndsBeforeTheSwitch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val dark = mutableStateOf(false)
        rule.setContent {
            XimeTheme(darkTheme = dark.value) {
                Column(Modifier.width(360.dp)) {
                    SettingsSection(title = "其他用法") {
                        SettingsToggleItem(icon = Icons.TwoTone.Dialpad, title = rows[0].first, subtitle = rows[0].second,
                            checked = true, onCheckedChange = {})
                        SettingsToggleItem(icon = Icons.TwoTone.ClosedCaption, title = rows[1].first, subtitle = rows[1].second,
                            checked = false, onCheckedChange = {})
                    }
                }
            }
        }
        rule.waitForIdle()
        // 整行可点（clickable）会把文字并进行节点，按合并树找到的是整行；要量文字本身得看未合并的树。
        val switches = rule.onAllNodes(isToggleable(), useUnmergedTree = true)
        rows.forEachIndexed { index, (_, subtitle) ->
            val text = rule.onAllNodesWithText(subtitle, useUnmergedTree = true)[0].getBoundsInRoot()
            // 第 0、1 个可切换节点是两行的开关（行本身是 clickable，不算可切换）。
            val switch = switches[index].getBoundsInRoot()
            val gap = switch.left - text.right
            assertTrue("「$subtitle」离开关只有 $gap", gap >= 12.dp)
        }
        val out = System.getenv("CYIME_RENDER_OUT")
        if (out.isNullOrEmpty()) return
        File(out).mkdirs()
        for (theme in listOf("light", "dark")) {
            rule.runOnIdle { dark.value = theme == "dark" }
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "toggle-gap-$theme.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
