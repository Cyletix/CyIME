package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.keyboardBackgroundBelowCard
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
 * 键盘卡片底边和下面导航区那条在接缝两边必须同色（2026-10-30 用户反馈：换了主题后底下悬空 1 厘米）。
 * 渐变主题逐个检查接缝上下两行像素；同时用旧画法（这条按自身尺寸重新铺）画一份，确认这项检查能抓到原来的断层。
 * 设了 CYIME_RENDER_OUT=<目录> 时另出一张对照图 navigation-strip-seam.png（含图片主题，左旧右新）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w900dp-h2000dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationStripSeamRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class Sample(val label: String, val card: MutableState<Rect?> = mutableStateOf(null),
                         val oldCard: MutableState<Rect?> = mutableStateOf(null))

    @Test fun navigationStripContinuesKeyboardBackground() {
        val out = System.getenv("CYIME_RENDER_OUT")
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val gradients = listOf("sunset_gradient", "aurora_gradient", "transparent_glass")
        val ids = gradients + if (out.isNullOrEmpty()) emptyList() else listOf("olivcelso_desert")
        val cases = ids.flatMap { id -> listOf(false, true).map { dark -> Triple(id, dark, Sample("$id ${if (dark) "深" else "浅"}")) } }
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for ((id, dark, sample) in cases) {
                    val background = KeyboardThemes.getRenderingScheme(id).keyboardBackground
                    val fallback = resolveKeyboardPalette(id, dark).background
                    val solid = FrostedGlassConfig.defaults(dark, enabled = false)
                    Text(sample.label, fontSize = 10.sp, color = Color.White)
                    Row(Modifier.padding(bottom = 6.dp)) {
                        for (fixed in listOf(false, true)) {
                            val card = if (fixed) sample.card else sample.oldCard
                            Column(Modifier.padding(end = 8.dp)) {
                                Spacer(Modifier.width(400.dp).height(160.dp)
                                    .onGloballyPositioned { c ->
                                        val p = c.positionInWindow()
                                        card.value = Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
                                    }
                                    .keyboardBackground(background, dark, fallback, solid))
                                Spacer(Modifier.width(400.dp).height(48.dp).then(
                                    if (fixed) Modifier.keyboardBackgroundBelowCard(background, dark, fallback,
                                        card = { sample.card.value }, frostedGlass = solid)
                                    else Modifier.keyboardBackground(background, dark, fallback, solid)))
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        if (!out.isNullOrEmpty()) {
            // 图片主题在后台解码，多等一会儿再出图。
            Thread.sleep(1500)
            rule.waitForIdle()
        }
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        if (!out.isNullOrEmpty()) {
            File(out).mkdirs()
            File(out, "navigation-strip-seam.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        for ((id, _, sample) in cases) {
            if (id !in gradients) continue
            val newGap = seamDifference(bitmap, sample.card.value!!)
            val oldGap = seamDifference(bitmap, sample.oldCard.value!!)
            assertTrue("${sample.label}: 新画法接缝上下差 $newGap", newGap <= 6)
            // 竖向渐变（落日余晖、极光青蓝）旧画法在卡片底下从头色重新开始；斜向的 iOS 渐变断层小一些，不拿来判。
            if (id != "transparent_glass") assertTrue("${sample.label}: 旧画法本应有断层，差 $oldGap", oldGap >= 12)
        }
    }

    /** 卡片最后一行和下面那条第一行，沿宽度取若干点，RGB 最大差值。 */
    private fun seamDifference(bitmap: Bitmap, card: Rect): Int {
        val above = card.bottom.roundToInt() - 1
        val below = card.bottom.roundToInt()
        var worst = 0
        for (i in 1..9) {
            val x = (card.left + card.width * i / 10f).roundToInt()
            val a = bitmap.getPixel(x, above)
            val b = bitmap.getPixel(x, below)
            for (shift in listOf(16, 8, 0)) worst = maxOf(worst, abs((a shr shift and 255) - (b shr shift and 255)))
        }
        return worst
    }
}
