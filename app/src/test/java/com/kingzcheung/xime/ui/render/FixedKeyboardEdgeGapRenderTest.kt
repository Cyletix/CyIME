package com.kingzcheung.xime.ui.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.FloatingKeyboardContainer
import com.kingzcheung.xime.ui.keyboard.NavigationStripBelowCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 固定键盘铺满整宽时左右都贴边，左边不能留缝（B站多人「左边有一条缝，右边没有」，10-31 修过，11-05 又有人报）。
 * 原因是宽度按整 dp 存、屏幕宽多半不是整 dp（1080px / 2.625 = 411.43dp → 411dp = 1079px），居中把 0.5px 舍到左边。
 * 这里按常见手机、平板、折叠屏的真实像素宽和密度逐个摆真的 [FloatingKeyboardContainer]：
 * 存的宽度是 0（整宽）、向下取整、四舍五入几种，左右挪过几 dp 也一样，卡片左右边必须正好是窗口两边；
 * 卡片底下系统按钮那条（[NavigationStripBelowCard]，跟着卡片宽）也一样。
 * 先用改前的画法（整 dp 宽、居中）摆一次，确认这项检查确实抓得到那 1px。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w3200dp-h600dp-mdpi")
class FixedKeyboardEdgeGapRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private data class Case(val label: String, val density: Float, val hostPx: Int,
                            val fixedWidthDp: Int, val resolvedWidthDp: Int, val offsetDp: Int)

    private val case = mutableStateOf<Case?>(null)
    private val host = mutableStateOf<Rect?>(null)
    private val card = mutableStateOf<Rect?>(null)
    private val strip = mutableStateOf<android.graphics.Rect?>(null)
    private val oldCard = mutableStateOf<Rect?>(null)

    /** 对照：改前的画法（整 dp 宽、居中）在 1080px / 2.625 上左边空 1px。抓不到就说明下一项检查本身失效了。 */
    @Test fun oldWholeDpRuleLeftAGap() {
        val old = Case("对照", 2.625f, 1080, 411, 411, 0)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(old.density, 1f)) {
                val hostWidth = with(LocalDensity.current) { old.hostPx.toDp() }
                Box(Modifier.requiredWidth(hostWidth).height(KB_H.dp).onGloballyPositioned { host.value = it.boundsInWindow() }) {
                    Box(Modifier.align(Alignment.TopCenter).width(old.fixedWidthDp.dp).fillMaxSize()
                        .onGloballyPositioned { oldCard.value = it.boundsInWindow() })
                }
            }
        }
        rule.waitForIdle()
        val oldRect = oldCard.value!!
        val oldHost = host.value!!
        assertTrue("对照应留出 1px：$oldRect / $oldHost", oldRect.left > oldHost.left || oldRect.right < oldHost.right)
    }

    @Test fun currentRuleLeavesNoGapOnRealScreens() {
        rule.setContent {
            val c = case.value ?: return@setContent
            // 每种情况重新建节点：同一节点尺寸位置不变时 onGloballyPositioned 不再回调，卡片位置会留在上一种情况。
            key(c) { CompositionLocalProvider(LocalDensity provides Density(c.density, 1f)) {
                val hostWidth = with(LocalDensity.current) { c.hostPx.toDp() }
                Box(Modifier.requiredWidth(hostWidth).height(KB_H.dp + NAV_H.dp).background(Color.Gray)
                    .testTag("edge-gap-host")) {
                    Box(Modifier.width(hostWidth).height(KB_H.dp)) {
                        FloatingKeyboardContainer(isFloatingMode = false, scaleFactor = 1f, contentHeightDp = KB_H,
                            offsetX = 0, offsetY = 0, onDrag = { _, _ -> }, onDragEnd = {},
                            fixedWidthDp = c.fixedWidthDp, resolvedWidthDp = c.resolvedWidthDp, fixedOffsetX = c.offsetDp,
                            onCardPositioned = { l, t, r, b ->
                                card.value = Rect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
                            }) {
                            Box(Modifier.fillMaxSize().background(Color.Black))
                        }
                    }
                    Box(Modifier.align(Alignment.BottomStart).width(hostWidth).height(NAV_H.dp)) {
                        NavigationStripBelowCard(NAV_H.dp, card.value, followCard = true,
                            onBounds = { strip.value = it }, surface = Modifier.background(Color.Black))
                    }
                }
            } }
        }
        val failures = mutableListOf<String>()
        for (c in cases()) {
            rule.runOnIdle {
                card.value = null
                strip.value = null
                case.value = c
            }
            rule.waitForIdle()
            // 位置直接从节点读（窗口坐标、像素），不靠回调。
            val h = rule.onNodeWithTag("edge-gap-host").fetchSemanticsNode().boundsInWindow
            val k = rule.onNodeWithTag("fixed-keyboard-card").fetchSemanticsNode().boundsInWindow
            val s = rule.onNodeWithTag("navigation-strip").fetchSemanticsNode().boundsInWindow
            if (card.value == null) failures += "${c.label}: 卡片没报位置，底下那条没法跟卡片"
            if (k.left.roundToInt() != h.left.roundToInt() || k.right.roundToInt() != h.right.roundToInt())
                failures += "${c.label}: 卡片 $k，窗口 $h"
            if (s.left.roundToInt() != h.left.roundToInt() || s.right.roundToInt() != h.right.roundToInt())
                failures += "${c.label}: 底下那条 $s，窗口 $h"
        }
        assertEquals("整宽固定键盘没贴满两边:\n" + failures.joinToString("\n"), 0, failures.size)
    }

    /** 真实屏幕像素宽 × 密度（竖屏、横屏、平板、折叠屏内外屏），存的宽度三种来法，挪没挪过。 */
    private fun cases(): List<Case> {
        val screens = listOf(
            "1080@2.625" to (1080 to 2.625f), "1080@2.75" to (1080 to 2.75f), "1080@3.0" to (1080 to 3f),
            "1220@2.8125" to (1220 to 2.8125f), "1264@3.0" to (1264 to 3f), "1440@3.5" to (1440 to 3.5f),
            "1200@2.625" to (1200 to 2.625f), "1260@2.75" to (1260 to 2.75f),
            "2400@2.625 横" to (2400 to 2.625f), "2608@3.0 横" to (2608 to 3f), "2340@2.75 横" to (2340 to 2.75f),
            "2560@2.0 平板横" to (2560 to 2f), "2800@2.0 平板横" to (2800 to 2f), "1752@2.1875 平板竖" to (1752 to 2.1875f),
            "2208@2.5 折叠内屏" to (2208 to 2.5f), "904@2.625 折叠外屏" to (904 to 2.625f),
        )
        return screens.flatMap { (name, screen) ->
            val (px, density) = screen
            val dp = px / density
            val saved = listOf("存0" to (0 to dp.roundToInt()), "存0未解析" to (0 to 0),
                "存向下取整" to (floor(dp).toInt() to floor(dp).toInt()),
                "存四舍五入" to (dp.roundToInt() to dp.roundToInt()))
            saved.flatMap { (how, widths) ->
                listOf(0, -6, 6).map { offset ->
                    Case("$name $how 挪$offset", density, px, widths.first, widths.second, offset)
                }
            }
        }
    }

    private companion object {
        const val KB_H = 120
        const val NAV_H = 24
    }
}
