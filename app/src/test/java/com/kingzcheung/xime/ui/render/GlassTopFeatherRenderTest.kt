package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.theme.GLASS_TOP_FEATHER_HEIGHT
import com.kingzcheung.xime.ui.theme.KeyboardBackdropHost
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 玻璃「柔化顶部边缘」的底色那层（B站用户 10-31：磨玻璃在别的应用上割裂感很重）：
 * 开着时卡片上沿 16dp 里玻璃底色从几乎没有渐变到满，一路不回头；这段以下和关着时一模一样；
 * 关着时上沿第一行就是满的（原样）。Robolectric 没有系统模糊，这里只看底色；模糊细条的切法见 GlassTopFeatherTest。
 * 设了 CYIME_RENDER_OUT=<目录> 时另出对照图 glass-top-feather.png（左关右开，深浅两种主题）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w900dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassTopFeatherRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class Case(val label: String, val themeId: String, val dark: Boolean, val feathered: Boolean) {
        val card = mutableStateOf<Rect?>(null)
        val top: MutableState<Float?> = mutableStateOf(null)
    }

    @Test fun softTopEdgeFadesTintOnlyInsideCardTop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val rows = listOf("pure_black" to true, "soft_blue" to false, "sunset_gradient" to false).map { (id, dark) ->
            listOf(Case("关", id, dark, feathered = false), Case("开", id, dark, feathered = true))
        }
        rule.setContent {
            Column(Modifier.background(Color(0xFF303030)).padding(6.dp)) {
                for (cases in rows) {
                    Text("${cases[0].themeId} ${if (cases[0].dark) "深" else "浅"}", fontSize = 11.sp, color = Color.White)
                    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (case in cases) Column {
                            Text("柔化顶部边缘 ${case.label}", fontSize = 10.sp, color = Color.White)
                            Sample(case)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        Thread.sleep(1000)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        System.getenv("CYIME_RENDER_OUT")?.takeIf { it.isNotEmpty() }?.let { out ->
            File(out).mkdirs()
            File(out, "glass-top-feather.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        val feather = GLASS_TOP_FEATHER_HEIGHT.value * rule.activity.resources.displayMetrics.density
        for ((plain, soft) in rows) {
            val p = plain.card.value!!
            val s = soft.card.value!!
            val label = "${soft.themeId} ${if (soft.dark) "深" else "浅"}"
            // 取样列靠左，那里只有宿主的灰和玻璃底色，没有文字和键帽。
            fun at(card: Rect, dy: Float) = bitmap.px(card.left + 3f, card.top + dy)
            val host = bitmap.px(s.left + 3f, s.top - 3f)
            val full = at(p, feather + 4f)
            // 关着：第一行就是满的底色（原样）。
            assertTrue("$label 关：上沿第一行应是满底色", diff(at(p, 1f), full) <= 3)
            // 开着：第一行几乎是宿主，往下一路靠近满底色，不回头；这段以下和关着一样。
            assertTrue("$label 开：上沿第一行应接近宿主（差 ${diff(at(s, 0.5f), host)}）",
                diff(at(s, 0.5f), host) <= diff(full, host) / 6 + 3)
            var last = -1
            for (dy in 0 until feather.roundToInt()) {
                val d = diff(at(s, dy + 0.5f), host)
                assertTrue("$label 开：第 $dy 行回头了（$d < $last）", d + 2 >= last)
                last = d
            }
            val mid = diff(at(s, feather / 2f), host)
            val span = diff(full, host)
            assertTrue("$label 开：中间应在半路（$mid / $span）", mid in span / 4..span * 3 / 4 + 2)
            assertTrue("$label 开：这段以下应和关着一样", diff(at(s, feather + 4f), full) <= 3)
            // 卡片上面的宿主不被染色。
            assertTrue("$label 开：卡片上面应是宿主", diff(bitmap.px(s.left + 3f, s.top - 3f),
                bitmap.px(p.left + 3f, p.top - 3f)) <= 3)
        }
    }

    /** 宿主一块灰，右边几行字；底部固定键盘，和服务里一样由 KeyboardBackdropHost 供玻璃底色。 */
    @Composable
    private fun Sample(case: Case) {
        val background = KeyboardThemes.getRenderingScheme(case.themeId).keyboardBackground
        val palette = resolveKeyboardPalette(case.themeId, case.dark)
        val glass = FrostedGlassConfig.defaults(case.dark, enabled = true)
        Box(Modifier.width(HOST_W.dp).height(HOST_H.dp).background(Color(HOST_ARGB))) {
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = (KB_H - 30).dp)) {
                repeat(4) { Text("输入栏 Input $it", fontSize = 14.sp, color = Color.Black) }
            }
            KeyboardBackdropHost(enabled = true, background = background, isDark = case.dark,
                fallback = palette.background, glass = glass, backdropHeight = KB_H.dp,
                modifier = Modifier.fillMaxWidth().height(KB_H.dp).align(Alignment.BottomCenter),
                topFeather = case.top.takeIf { case.feathered }) {
                Box(Modifier.fillMaxSize()
                    .onGloballyPositioned {
                        val bounds = it.boundsInWindow()
                        case.card.value = bounds
                        case.top.value = bounds.top
                    }
                    .keyboardBackground(background, case.dark, palette.background, glass)) {
                    Text("候选 候选词", Modifier.padding(start = 40.dp, top = 6.dp), fontSize = 16.sp, color = palette.text)
                }
            }
        }
    }

    private fun Bitmap.px(x: Float, y: Float): Int = getPixel(x.roundToInt(), y.toInt())

    private fun diff(a: Int, b: Int): Int =
        listOf(16, 8, 0).maxOf { abs((a shr it and 255) - (b shr it and 255)) }

    private companion object {
        const val HOST_W = 300
        const val HOST_H = 200
        const val KB_H = 120
        const val HOST_ARGB = 0xFF8A9290.toInt()
    }
}
