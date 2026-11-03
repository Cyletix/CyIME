package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.AppFonts
import com.kingzcheung.xime.ui.keyboard.CandidateEntry
import com.kingzcheung.xime.ui.keyboard.CandidatePage
import com.kingzcheung.xime.ui.keyboard.CandidatePageCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidatePageState
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.inheritKeyboardBackground
import com.kingzcheung.xime.ui.theme.KeyboardBackdropFrame
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalSharedKeyboardBackdrop
import com.kingzcheung.xime.ui.theme.SharedKeyboardBackdrop
import com.kingzcheung.xime.ui.theme.keyboardBackground
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 九键/26 键展开页开玻璃（用户 11-03 截图）：展开页里面那一列又叠了一层玻璃底色，中间比上面的候选栏和两侧厚一倍，
 * 候选栏看着像单独一条模糊条；纯联想展开时第一行候选上面还多一条分隔线。
 * 展开页和候选栏、两侧应是同一块玻璃；分隔线只在上面有打字候选时出现。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w440dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpandedGlassSurfaceRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private var candidates by mutableStateOf(listOf("这么", "这样", "那么").mapIndexed { i, w -> CandidateEntry(w, globalIndex = i) })
    private var associations by mutableStateOf(emptyList<String>())

    private fun show() {
        val app = rule.activity.application
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        val fallback = Color(0xff302d3a)
        val glass = FrostedGlassConfig.defaults(true, enabled = true).copy(backgroundOpacity = 0.5f)
        rule.setContent {
            val shared = SharedKeyboardBackdrop(KeyboardBackdropFrame(Offset.Zero, IntSize.Zero), null, fallback,
                0.5f, LocalView.current.rootView)
            Column(Modifier.width(400.dp).background(Color.White).testTag("sample")) {
                Spacer(Modifier.height(24.dp))
                CompositionLocalProvider(LocalSharedKeyboardBackdrop provides shared,
                    LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = glass)) {
                    // 和键盘里一样：整块键盘画一层玻璃底色，候选栏透明，展开页沿用键盘底色。
                    Column(Modifier.fillMaxWidth().height(300.dp).testTag("card").keyboardBackground(null, true, fallback, glass)) {
                        Box(Modifier.fillMaxWidth().height(44.dp).testTag("bar"))
                        CandidatePage(
                            state = CandidatePageState(candidates = candidates, associationCandidates = associations,
                                backgroundColor = fallback, textColor = Color(0xffddd4ff), matchT9Geometry = true),
                            callbacks = CandidatePageCallbacks(onCandidateSelect = {}),
                            modifier = Modifier.inheritKeyboardBackground().weight(1f).fillMaxWidth().testTag("panel"),
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun capture(name: String): Bitmap {
        rule.waitForIdle()
        // Robolectric 下 captureToImage 等不到 PixelCopy；直接把整个窗口画到位图上。
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        System.getenv("CYIME_RENDER_OUT")?.let { out ->
            File(out).mkdirs()
            val b = rule.onNodeWithTag("sample").fetchSemanticsNode().boundsInWindow
            File(out, "expanded-glass-$name.png").outputStream().use {
                Bitmap.createBitmap(bitmap, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        return bitmap
    }

    @Test fun expandedBodyIsTheSameGlassAsTheBarAbove() {
        show()
        val bitmap = capture("t9")
        val bar = rule.onNodeWithTag("bar").fetchSemanticsNode().boundsInWindow
        val panel = rule.onNodeWithTag("panel").fetchSemanticsNode().boundsInWindow
        fun red(x: Float, y: Float) = android.graphics.Color.red(bitmap.getPixel(x.toInt(), y.toInt()))
        val barRed = red(bar.center.x, bar.center.y)
        // 一层底色：白底上 50% 的 0x30 ≈ 151；叠两层 ≈ 100。
        assertTrue("bar $barRed", abs(barRed - (255 + 0x30) / 2) <= 3)
        // 候选区空白处（只有一行候选，下面是空的），在左右两栏之间。
        val bodyRed = red(panel.center.x, panel.top + panel.height * 0.8f)
        assertTrue("body $bodyRed vs bar $barRed", abs(bodyRed - barRed) <= 2)
    }

    @Test fun associationDividerOnlyUnderTypedCandidates() {
        associations = listOf("用", "把", "跟")
        show()
        rule.onAllNodesWithTag("expanded-association-divider", true).assertCountEquals(1)
        rule.runOnIdle { candidates = emptyList() }
        capture("associations-only")
        rule.onAllNodesWithTag("expanded-association-divider", true).assertCountEquals(0)
    }
}
