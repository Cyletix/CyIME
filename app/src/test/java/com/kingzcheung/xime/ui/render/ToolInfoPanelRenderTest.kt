package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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
import com.kingzcheung.xime.plugin.core.api.PluginResultItem
import com.kingzcheung.xime.plugin.core.api.TOOL_PANEL_EDIT_ACTION
import com.kingzcheung.xime.plugin.core.config.UiNode
import com.kingzcheung.xime.plugin.core.config.UiNodeType
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.InfoPanel
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.inheritKeyboardBackground
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * AI 改写面板（键盘里的全屏插件面板）：11-03 用户「点一下打开之后的窗口全是错的、重叠在一起」。
 * 平板是玻璃 + 键盘透明度 0%，后面的设置页透上来和面板里的字叠在一起。
 * 这里把面板盖在一张假的设置页上，四个时刻（刚打开、生成中、出结果、没通过）× 浅色/深色 × 玻璃开/关。
 * 设了 CYIME_RENDER_OUT=<目录> 时出图 tool-info-panel.png。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1700dp-h2400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolInfoPanelRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun header(label: String) = UiNode(UiNodeType.SECTION, label = label)
    private fun caption(text: String) = UiNode(UiNodeType.TEXT, value = text, style = "caption")
    private val source = "明天下午三点开会，记得带上周的报表"
    private val modes = listOf("润色", "改错字", "更正式", "更口语", "缩短", "翻译")

    private fun nodes(result: Boolean, rejected: Boolean, again: String? = null) = buildList {
        add(header("原文"))
        add(UiNode(UiNodeType.TEXTAREA, key = TOOL_PANEL_EDIT_ACTION, value = source))
        if (rejected) {
            add(header("结果没用上，原文没动"))
            add(caption("译文太短，像是没翻完。"))
            add(caption("AI 的原话：Meeting tomorrow"))
        }
        if (result) {
            add(header("结果"))
            add(caption("点下面的结果替换原文；不满意可以换一种改法。"))
        }
        add(UiNode(UiNodeType.DIVIDER))
        for (m in modes) add(UiNode(UiNodeType.BUTTON, key = "mode:$m", label = if (m == again) "$m（再来一次）" else m))
        add(UiNode(UiNodeType.BUTTON, key = "host:settings", label = "设置"))
    }

    private data class Moment(val name: String, val nodes: List<UiNode>, val items: List<PluginResultItem>, val loading: Boolean)

    private val moments get() = listOf(
        Moment("刚打开", nodes(false, false), emptyList(), false),
        Moment("生成中", nodes(false, false), emptyList(), true),
        Moment("出结果", nodes(true, false, "翻译"),
            listOf(PluginResultItem("result", "Meeting at 3 p.m. tomorrow. Remember to bring last week's report.")), false),
        Moment("没通过", nodes(false, true, "翻译"), emptyList(), false),
    )

    @Test fun rendersEveryMomentOverABusyPage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val variants = buildList { for (dark in listOf(false, true)) for (glass in listOf(true, false)) add(dark to glass) }
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Column {
                    for ((dark, glass) in variants) Row { for (m in moments) Variant(m, dark, glass) }
                }
            }
        }
        rule.waitForIdle()
        // 生成中：标题旁写「生成中…」，四个变体各一处。
        assertEquals(variants.size, rule.onAllNodesWithText("生成中…").fetchSemanticsNodes().size)
        System.getenv("CYIME_RENDER_OUT")?.takeIf { it.isNotEmpty() }?.let { out ->
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out).mkdirs()
            File(out, "tool-info-panel.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Composable
    private fun Variant(moment: Moment, dark: Boolean, glass: Boolean) {
        val palette = resolveKeyboardPalette("soft_blue", dark)
        val glassConfig = FrostedGlassConfig.defaults(dark, enabled = glass)
        CompositionLocalProvider(LocalKeyboardPalette provides palette,
            LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = glassConfig)) {
            MaterialTheme {
                Box(Modifier.padding(4.dp).size(411.dp, 560.dp).background(if (dark) Color(0xFF202124) else Color(0xFFF1F3F4))) {
                    // 假的设置页：一排排字，透上来就能看出叠字。
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        for (i in 1..18) Text("设置项 $i　候选栏、按键音、透明度、拼音气泡", fontSize = 15.sp,
                            color = if (dark) Color.White else Color.Black, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Text("${moment.name} ${if (dark) "深色" else "浅色"} ${if (glass) "玻璃·透明度0%" else "不透明"}",
                        fontSize = 12.sp, color = Color.Red, modifier = Modifier.padding(4.dp))
                    // 键盘：玻璃开、透明度 0% 时底色几乎全透（真机由系统在后面做模糊）。
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(420.dp)
                        .background(if (glass) palette.background.copy(alpha = 0.08f) else palette.background)) {
                        InfoPanel(
                            title = "AI 改写",
                            nodes = moment.nodes,
                            items = moment.items,
                            isLoading = moment.loading,
                            backgroundColor = palette.background,
                            textColor = palette.text,
                            accentColor = palette.accent,
                            itemBgColor = palette.key,
                            onClose = {}, onAction = {}, onItemClick = {},
                            modifier = Modifier.inheritKeyboardBackground().fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
