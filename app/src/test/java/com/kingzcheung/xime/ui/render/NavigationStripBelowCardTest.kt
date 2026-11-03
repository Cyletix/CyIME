package com.kingzcheung.xime.ui.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.NavigationStripBelowCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 固定卡片比屏幕窄时，底下系统按钮那条只和卡片一样宽（11-03 平板实测：整屏宽的一条颜色和卡片不同，看着像没贴底）。
 * 「填满留空处」开着、或卡片还没量到时整宽。毛玻璃收到的范围和画出来的一样。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h300dp-mdpi")
class NavigationStripBelowCardTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun stripFollowsANarrowCardAndFillsTheWidthOtherwise() {
        val follow = mutableStateOf(true)
        val card = mutableStateOf<Rect?>(null)
        var hostX = 0f
        var glass: android.graphics.Rect? = null
        rule.setContent {
            Box(Modifier.size(400.dp, 200.dp).onGloballyPositioned { hostX = it.positionInWindow().x }) {
                NavigationStripBelowCard(48.dp, card.value, follow.value, onBounds = { glass = it },
                    surface = Modifier.background(Color.Red))
            }
        }
        // 卡片还没量到：整宽。
        assertEquals(400f, rule.onNodeWithTag("navigation-strip").fetchSemanticsNode().size.width.toFloat(), 0.5f)
        // 居中 240 宽的卡片：这条和卡片同宽、同左边。
        rule.runOnIdle { card.value = Rect(hostX + 80f, 0f, hostX + 320f, 152f) }
        rule.waitForIdle()
        val strip = rule.onNodeWithTag("navigation-strip").fetchSemanticsNode()
        assertEquals(240f, strip.size.width.toFloat(), 0.5f)
        assertEquals(80f, strip.boundsInRoot.left, 0.5f)
        assertEquals(48f, strip.size.height.toFloat(), 0.5f)
        assertEquals(240, glass!!.width())
        assertEquals((hostX + 80f).toInt(), glass!!.left)
        // 「填满留空处」开着：整宽。
        rule.runOnIdle { follow.value = false }
        rule.waitForIdle()
        assertEquals(400f, rule.onNodeWithTag("navigation-strip").fetchSemanticsNode().size.width.toFloat(), 0.5f)
        assertEquals(400, glass!!.width())
    }
}
