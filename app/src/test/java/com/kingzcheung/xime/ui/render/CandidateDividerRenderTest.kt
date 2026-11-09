package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.AppFonts
import com.kingzcheung.xime.ui.keyboard.CANDIDATE_ITEM_PADDING
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.FixedCandidateStrip
import com.kingzcheung.xime.ui.keyboard.candidateDividerColor
import com.kingzcheung.xime.ui.keyboard.candidateDividersShown
import com.kingzcheung.xime.ui.keyboard.wideCandidateItemPadding
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 候选之间的浅色竖线（用户 11-09，像 Gboard）：候选之间空得开（「候选词间距加宽」）时，
 * 两个候选之间正中画一条浅色竖线，颜色是候选文字色压淡；默认间距挤，不画。
 * 九键/26 键打字候选（能滑的那条）、不滑的那条（分体、实体键盘）、联想／英文那条都一样。
 * 设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w440dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateDividerRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val words = listOf("测试", "插电式", "不都是", "成都市", "表达式")
    private val visuals = CandidateBarVisuals(Color.White, Color.Black, Color.Gray)

    @Test fun dividersOnlyWhenTheGapIsWide() {
        // 默认：间隔 4dp + 两边各 4dp 留白，挤，不画。
        assertFalse(candidateDividersShown(4.dp, CANDIDATE_ITEM_PADDING))
        // 实体键盘候选条：间隔 8dp，仍不画（它不跟「候选词间距加宽」）。
        assertFalse(candidateDividersShown(8.dp, CANDIDATE_ITEM_PADDING))
        // 「候选词间距加宽」：默认字号和最小常用字号都画。
        for (font in listOf(12, 19, 28)) {
            assertTrue("字号 $font", candidateDividersShown(4.dp, wideCandidateItemPadding(font.dp)))
        }
        // 颜色随主题：文字色本身，压到很淡。
        assertEquals(Color.White.copy(alpha = 0.12f), candidateDividerColor(Color.White))
        assertEquals(Color.Black.copy(alpha = 0.12f), candidateDividerColor(Color.Black))
    }

    @Test fun stripDrawsDividerInTheGapOnlyWhenWide() {
        init()
        var padding by mutableStateOf(CANDIDATE_ITEM_PADDING)
        var scrollable by mutableStateOf(true)
        rule.setContent {
            Box(Modifier.background(Color.White).testTag("sample")) {
                FixedCandidateStrip(words, comments = emptyList(), visuals = visuals,
                    callbacks = CandidateBarCallbacks(onCandidateSelect = {}),
                    fontSize = 19.sp, modifier = Modifier.width(420.dp).height(44.dp),
                    scrollable = scrollable, itemPadding = padding)
            }
        }
        for (scroll in listOf(true, false)) {
            rule.runOnIdle { scrollable = scroll; padding = CANDIDATE_ITEM_PADDING }
            val narrow = capture("strip-${if (scroll) "scroll" else "fixed"}-default")
            assertNoDivider(narrow, "bar-candidate:0", "bar-candidate:1")
            rule.runOnIdle { padding = wideCandidateItemPadding(19.dp) }
            val wide = capture("strip-${if (scroll) "scroll" else "fixed"}-wide")
            for (i in 0 until 3) assertDivider(wide, "bar-candidate:$i", "bar-candidate:${i + 1}")
            // 第一个候选左边不画（栏边没有竖线）。
            val first = bounds("bar-candidate:0")
            assertTrue("首个候选左边不该有竖线", darkest(wide, first.left - 3f, first.left, first.center.y) >= 250)
        }
    }

    /** 跟着设置走：打字候选和联想那条，开「候选词间距加宽」才画，关了就没有。 */
    @Test fun candidateBarFollowsWideSpacingSetting() {
        init()
        val app = rule.activity.application
        var state by mutableStateOf<CandidateBarState>(CandidateBarState.ChineseCandidates(words, inputText = "ceshi"))
        rule.setContent {
            Column(Modifier.background(Color.White).testTag("sample")) {
                CandidateBar(state = state, visuals = visuals,
                    callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onAssociationSelect = {}),
                    showPreeditPreview = false, modifier = Modifier.width(420.dp).height(44.dp))
            }
        }
        for (wide in listOf(false, true)) {
            rule.runOnIdle { SettingsPreferences.setWideCandidateSpacing(app, wide) }
            rule.runOnIdle { state = CandidateBarState.ChineseCandidates(words, inputText = "ceshi") }
            val typing = capture("bar-typing-${if (wide) "wide" else "default"}")
            if (wide) assertDivider(typing, "bar-candidate:0", "bar-candidate:1")
            else assertNoDivider(typing, "bar-candidate:0", "bar-candidate:1")
            rule.runOnIdle { state = CandidateBarState.AssociationOnly(listOf("一下", "一遍", "结果")) }
            val assoc = capture("bar-association-${if (wide) "wide" else "default"}")
            if (wide) assertDivider(assoc, "bar-association:0", "bar-association:1")
            else assertNoDivider(assoc, "bar-association:0", "bar-association:1")
        }
        rule.runOnIdle { SettingsPreferences.setWideCandidateSpacing(app, false) }
    }

    private fun init() {
        val app = rule.activity.application
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow

    /** 两个候选之间那段间隔里最深的一点（红色通道，白底黑字：255 = 空白）。 */
    private fun gapDarkest(bitmap: Bitmap, leftTag: String, rightTag: String): Int {
        val left = bounds(leftTag)
        val right = bounds(rightTag)
        assertTrue("$leftTag 和 $rightTag 之间应有间隔", right.left - left.right >= 2f)
        // 两边各让 1px：选中底色的圆角边不算进来。
        return darkest(bitmap, left.right + 1f, right.left - 1f, left.center.y)
    }

    private fun darkest(bitmap: Bitmap, from: Float, to: Float, y: Float): Int {
        var min = 255
        for (x in ceil(from).toInt().coerceAtLeast(0) until floor(to).toInt().coerceAtMost(bitmap.width))
            for (dy in -2..2) min = minOf(min, android.graphics.Color.red(bitmap.getPixel(x, y.toInt() + dy)))
        return min
    }

    private fun assertDivider(bitmap: Bitmap, leftTag: String, rightTag: String) {
        // 白底上 12% 的黑 ≈ 224；要看得出来，也不能比这深多少。
        val red = gapDarkest(bitmap, leftTag, rightTag)
        assertTrue("$leftTag|$rightTag 之间应有浅色竖线，最深 $red", red in 215..235)
    }

    private fun assertNoDivider(bitmap: Bitmap, leftTag: String, rightTag: String) {
        val red = gapDarkest(bitmap, leftTag, rightTag)
        assertTrue("$leftTag|$rightTag 之间不该有竖线，最深 $red", red >= 250)
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
            File(out, "candidate-divider-$name.png").outputStream().use {
                Bitmap.createBitmap(bitmap, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        return bitmap
    }
}
