package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.BackgroundConfig
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.FloatingKeyboardCardCorner
import com.kingzcheung.xime.ui.keyboard.FloatingKeyboardContainer
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardBackdropHost
import com.kingzcheung.xime.ui.theme.KeyboardPalette
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.keyboardBackgroundBesideCard
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
 * 「填满留空处」打开时固定卡片和两侧必须是一整条键盘（2026-10-31 平板截图）：
 * 卡片和两侧同色（玻璃、半透明不能叠两层），卡片和两侧相接处没有圆角台阶，整条外角照顶部圆角设置圆。
 * 「旧」列按改前的画法画（两侧那层铺满整宽、卡片照常圆角），只出图对照、打印差值，不参与判定。
 * 宿主是一块均匀的灰（Robolectric 没有系统模糊，玻璃只看色调那层）。
 * 设了 CYIME_RENDER_OUT=<目录> 时另出对照图 fill-sides.png（主题 × 玻璃 × 新旧，下面一排是卡片四种位置）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1300dp-h2000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FillSidesRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class Case(
        val label: String, val themeId: String, val dark: Boolean, val glass: Boolean, val old: Boolean,
        val cardWidthDp: Int = 240, val offsetDp: Int = 0,
    ) {
        val card = mutableStateOf<Rect?>(null)
        val host = mutableStateOf<Rect?>(null)
    }

    @Test fun filledSidesMatchCard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        SettingsPreferences.setRoundedKeyboardTop(context, true)
        SettingsPreferences.setRoundedKeyboardBottom(context, false)
        val themes = listOf("transparent_glass" to false, "transparent_glass" to true, "pure_black" to true,
            "soft_blue" to false, "sunset_gradient" to false, "olivcelso_desert" to false)
        val rows = themes.map { (id, dark) ->
            "$id ${if (dark) "深" else "浅"}" to listOf(
                Case("玻璃关 新", id, dark, glass = false, old = false),
                Case("玻璃开 旧", id, dark, glass = true, old = true),
                Case("玻璃开 新", id, dark, glass = true, old = false),
            )
        }
        // 卡片位置：平板九键窄卡片居中、26 键宽卡片、卡片挪到左边贴边、铺满整宽（分体填满中缝时也是整宽）。
        val layouts = listOf(240, 400, 240, HOST_W).mapIndexed { i, w ->
            Case(listOf("九键窄卡片", "26 键宽卡片", "贴左边", "整宽/分体").getOrElse(i) { "" }, "transparent_glass",
                dark = true, glass = true, old = false, cardWidthDp = w, offsetDp = if (i == 2) -HOST_W else 0)
        }
        rule.setContent {
            Column(Modifier.background(Color(0xFF303030)).padding(6.dp)) {
                for ((title, cases) in rows) {
                    Text(title, fontSize = 11.sp, color = Color.White)
                    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (case in cases) Column {
                            Text(case.label, fontSize = 10.sp, color = Color.White)
                            FillSidesSample(case)
                        }
                    }
                }
                Text("卡片位置（极光 iOS 深色、玻璃开）", fontSize = 11.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (case in layouts) Column {
                        Text(case.label, fontSize = 10.sp, color = Color.White)
                        FillSidesSample(case)
                    }
                }
            }
        }
        rule.waitForIdle()
        val out = System.getenv("CYIME_RENDER_OUT")
        // 图片主题在后台解码，多等一会儿。
        Thread.sleep(1500)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        if (!out.isNullOrEmpty()) {
            File(out).mkdirs()
            File(out, "fill-sides.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        val density = rule.activity.resources.displayMetrics.density
        // 旧画法那列只打印、不判：它是对照，不是产品代码。叠两层的色差 = 浓度 × (1 - 浓度) × |主题底色 - 宿主|，
        // 主题底色和宿主灰接近时本来就看不出来。
        var oldSeamWorst = 0
        var oldCornerWorst = 0
        for ((title, cases) in rows) for (case in cases) {
            val card = case.card.value!!
            val label = "$title ${case.label}"
            // 接缝左右各取一点：左边第 2 列正好是卡片第 1 列的镜像（图片主题），纯色、玻璃、渐变本来就该同色。
            fun seam(y: Float) = diff(bitmap.px(card.left - 2f, y), bitmap.px(card.left + 1f, y))
            val middle = card.top + 8f * density
            if (case.old) {
                // 旧卡片顶角是 16dp 圆角，取样要低过圆角（键帽左边留 6dp 空，这一列下面都是底色）。
                val below = card.top + 30f * density
                oldSeamWorst = maxOf(oldSeamWorst, seam(below))
                oldCornerWorst = maxOf(oldCornerWorst,
                    diff(bitmap.px(card.left + 1f, card.top + 1f), bitmap.px(card.left + 1f, below)))
            } else {
                assertTrue("$label: 卡片和两侧差 ${seam(middle)}", seam(middle) <= 3)
                assertTrue("$label: 卡片顶角处差 ${seam(card.top + 1f)}", seam(card.top + 1f) <= 3)
                // 整条的外角跟整宽键盘一样圆：最角上那个像素透出宿主。
                val host = case.host.value!!
                assertTrue("$label: 整条左上角应是圆角", diff(bitmap.px(host.left + 1f, card.top + 1f), HOST_ARGB) <= 3)
            }
        }
        println("FillSidesRenderTest 旧画法对照：卡片和两侧最大差 $oldSeamWorst，顶角最大差 $oldCornerWorst")
        for (case in layouts) {
            val card = case.card.value!!
            val host = case.host.value!!
            // 不管卡片在哪，整条只在贴屏幕边的两个外角圆；卡片和两侧相接处没有台阶、同色。
            assertTrue("${case.label}: 左上外角应透出宿主", diff(bitmap.px(host.left + 1f, card.top + 1f), HOST_ARGB) <= 3)
            assertTrue("${case.label}: 右上外角应透出宿主", diff(bitmap.px(host.right - 2f, card.top + 1f), HOST_ARGB) <= 3)
            for (y in listOf(card.top + 1f, card.top + 8f * density)) {
                if (card.left > host.left + 2f) {
                    val d = diff(bitmap.px(card.left - 2f, y), bitmap.px(card.left + 1f, y))
                    assertTrue("${case.label}: 卡片左边接缝差 $d", d <= 3)
                }
                if (card.right < host.right - 2f) {
                    val d = diff(bitmap.px(card.right + 1f, y), bitmap.px(card.right - 2f, y))
                    assertTrue("${case.label}: 卡片右边接缝差 $d", d <= 3)
                }
            }
        }
    }

    /** 一块宿主（均匀的灰，上面一张白纸），底部是固定键盘：服务里同样的三层（玻璃取样宿主、两侧、卡片）。 */
    @Composable
    private fun FillSidesSample(case: Case) {
        val background = KeyboardThemes.getRenderingScheme(case.themeId).keyboardBackground
        val palette = resolveKeyboardPalette(case.themeId, case.dark)
        val glass = FrostedGlassConfig.defaults(case.dark, enabled = case.glass)
        Box(Modifier.width(HOST_W.dp).height(HOST_H.dp).background(Color(HOST_ARGB))
            .onGloballyPositioned { case.host.value = it.boundsInWindow() }) {
            Box(Modifier.fillMaxWidth().height((HOST_H - KB_H - 8).dp).padding(horizontal = 30.dp, vertical = 8.dp)
                .background(Color.White))
            KeyboardBackdropHost(enabled = case.glass, background = background, isDark = case.dark,
                fallback = palette.background, glass = glass, backdropHeight = KB_H.dp,
                modifier = Modifier.fillMaxWidth().height(KB_H.dp).align(Alignment.BottomCenter)) {
                Box(Modifier.fillMaxSize().then(
                    if (case.old) Modifier.oldBand(background, case.dark, palette.background, glass) { case.card.value }
                    else Modifier.keyboardBackgroundBesideCard(background, case.dark, palette.background,
                        card = { case.card.value }, frostedGlass = glass, topCorner = FloatingKeyboardCardCorner)))
                CompositionLocalProvider(LocalKeyboardInputPreferences provides
                    KeyboardInputPreferences(fillBlankAreas = !case.old, frostedGlass = glass)) {
                    FloatingKeyboardContainer(isFloatingMode = false, scaleFactor = 1f, contentHeightDp = KB_H,
                        offsetX = 0, offsetY = 0, onDrag = { _, _ -> }, onDragEnd = {},
                        fixedWidthDp = case.cardWidthDp, resolvedWidthDp = case.cardWidthDp, fixedOffsetX = case.offsetDp,
                        sidesFilled = !case.old,
                        onCardPositioned = { l, t, r, b ->
                            case.card.value = Rect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
                        }) {
                        Box(Modifier.fillMaxSize().keyboardBackground(background, case.dark, palette.background, glass)) {
                            FakeKeys(palette, glass)
                        }
                    }
                }
            }
        }
    }

    /** 改前的两侧：卡片那一段高度里整宽铺一层，卡片底下也铺。 */
    @Composable
    private fun Modifier.oldBand(background: BackgroundConfig?, dark: Boolean, fallback: Color,
                                 glass: FrostedGlassConfig, card: () -> Rect?): Modifier {
        var origin by remember { mutableStateOf<Offset?>(null) }
        return onGloballyPositioned { origin = it.positionInWindow() }.drawWithContent {
            val o = origin ?: return@drawWithContent
            val f = card()?.translate(-o.x, -o.y) ?: return@drawWithContent
            clipRect(0f, f.top, size.width, f.bottom) { this@drawWithContent.drawContent() }
        }.keyboardBackground(background, dark, fallback, glass)
    }

    /** 工具栏一排小圆点，下面三排键；顶上 24dp 内留空给取样。 */
    @Composable
    private fun FakeKeys(palette: KeyboardPalette, glass: FrostedGlassConfig) {
        val key = if (glass.enabled) palette.key.copy(alpha = glass.keyOpacity) else palette.key
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Row(Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                repeat(6) { Box(Modifier.size(6.dp).background(palette.text, CircleShape)) }
            }
            repeat(3) {
                Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) { Box(Modifier.weight(1f).fillMaxHeight().background(key, RoundedCornerShape(5.dp))) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    private fun Bitmap.px(x: Float, y: Float): Int = getPixel(x.roundToInt(), y.roundToInt())

    private fun diff(a: Int, b: Int): Int =
        listOf(16, 8, 0).maxOf { abs((a shr it and 255) - (b shr it and 255)) }

    private companion object {
        const val HOST_W = 400
        const val HOST_H = 200
        const val KB_H = 120
        const val HOST_ARGB = 0xFF8A9290.toInt()
    }
}
