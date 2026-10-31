package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 按键上的浮窗是单独窗口，盖住按下的键和旁边的键。浮窗还在时（气泡抬手后留几十毫秒、方向盘按住时一直在），
 * 快速再点同一个键或旁边的键会落进浮窗被吃掉（用户反馈 10-31「同一个键快速连按两下漏字」）。浮窗必须不接触摸。
 *
 * 浮窗窗口的参数挂在 Popup 窗口的根 View（PopupLayout）上：从浮窗里的 Compose 视图往上找到根，读它的 LayoutParams。
 * 窗口类型（Compose 用 SUB_PANEL）不管，只看标志。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class KeyPopupTouchPassThroughTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun windowParams(view: View): WindowManager.LayoutParams? =
        view.rootView.layoutParams as? WindowManager.LayoutParams

    private fun assertDisplayOnly(params: WindowManager.LayoutParams) {
        assertTrue("浮窗仍接触摸", params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue("浮窗会抢焦点", params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue("浮窗被裁在屏幕内", params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
    }

    /**
     * 点按/滑动/长按气泡（26 键等，SwipeBubbleOverlay 两处）和 14 键、九键的方向盘（DirectionDiscPreview）
     * 都用 [DisplayOnlyPopupProperties]，这里直接检查它落到窗口上的标志。
     */
    @Test fun sharedPropertiesAreDisplayOnly() {
        var popupView: View? = null
        rule.setContent {
            Popup(properties = DisplayOnlyPopupProperties) {
                popupView = LocalView.current
                Box(Modifier.size(10.dp))
            }
        }
        rule.waitForIdle()
        val params = windowParams(checkNotNull(popupView) { "浮窗没有打开" })
        checkNotNull(params) { "浮窗根视图没有窗口参数" }
        assertDisplayOnly(params)
    }
}
