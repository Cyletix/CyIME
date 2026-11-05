package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.TOOL_PANEL_BAR_HEIGHT
import com.kingzcheung.xime.ui.keyboard.ToolPanelBar
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 翻译栏（插件 display: live，照三星键盘的翻译）：左边圆钮、中间一行输入框、右边关闭，高 48dp。
 * 三个时刻（没填接口、空栏、打了字在译）× 浅色/深色；下面画一行假的候选栏对齐看。
 * 设了 CYIME_RENDER_OUT=<目录> 时出图 tool-panel-bar.png。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1300dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolPanelBarRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private data class Moment(val name: String, val chip: String, val hint: String, val typed: String, val loading: Boolean)

    private val moments = listOf(
        Moment("没填接口", "设置", "先填接口：点左边「设置」", "", false),
        Moment("空栏", "EN", "打字，停一下译成英文", "", false),
        Moment("在译", "EN", "打字，停一下译成英文", "你好，明天见", true),
    )

    @Test fun rendersBarMoments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Column { for (dark in listOf(false, true)) Row { for (m in moments) Variant(m, dark) } }
            }
        }
        rule.waitForIdle()
        // 栏里的字由键盘按键打进 EditText；这里直接写进「在译」那几格。
        val fields = mutableListOf<EditText>()
        fun collect(v: View) {
            if (v is EditText) fields += v
            if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i))
        }
        rule.runOnIdle {
            collect(rule.activity.window.decorView)
            fields.forEachIndexed { i, et -> et.setText(moments[i % moments.size].typed) }
        }
        rule.waitForIdle()
        assertEquals(2 * moments.size, fields.size)
        assertEquals(4, rule.onAllNodesWithText("EN").fetchSemanticsNodes().size)
        System.getenv("CYIME_RENDER_OUT")?.takeIf { it.isNotEmpty() }?.let { out ->
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out).mkdirs()
            File(out, "tool-panel-bar.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Composable
    private fun Variant(moment: Moment, dark: Boolean) {
        val palette = resolveKeyboardPalette("soft_blue", dark)
        MaterialTheme {
            Column(Modifier.padding(4.dp).width(411.dp).background(palette.background)) {
                Text("${moment.name} ${if (dark) "深色" else "浅色"}", fontSize = 12.sp, color = Color.Red,
                    modifier = Modifier.padding(4.dp))
                ToolPanelBar(
                    chipLabel = moment.chip,
                    hint = moment.hint,
                    isFocused = false,
                    isLoading = moment.loading,
                    textColor = palette.text,
                    accentColor = palette.accent,
                    cardBgColor = palette.key,
                    onChipClick = {}, onClose = {}, onFocusChange = {}, onTextChange = {},
                )
                // 假候选栏：看栏和候选栏的左右边距、高度是否协调
                Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp)) {
                    Text("好　像　久　呀　了　啊", fontSize = 20.sp, color = palette.text)
                }
                Box(Modifier.fillMaxWidth().height(TOOL_PANEL_BAR_HEIGHT.dp).background(palette.key.copy(alpha = 0.3f)))
            }
        }
    }
}
