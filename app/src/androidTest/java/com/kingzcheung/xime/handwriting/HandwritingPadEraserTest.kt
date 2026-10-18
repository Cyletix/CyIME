package com.kingzcheung.xime.handwriting

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.HandwritingKeyboardLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 输入法手写板：按住侧键一次拖过去，路过的每一笔都擦掉（与训练画板共用 PenEraser）。 */
class HandwritingPadEraserTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun holdSideButtonDragErasesEveryStrokeOnTheWay() {
        rule.setContent {
            MaterialTheme {
                HandwritingKeyboardLayout(onKeyPress = {}, onRecognition = {}, bottomPaddingDp = 0,
                    keyTextColor = Color.Black,
                    modifier = Modifier.fillMaxWidth().height(320.dp).background(Color.White))
            }
        }
        rule.waitForIdle()
        // 冻结时钟：停顿识别不会在测试中途把笔迹提交掉
        rule.mainClock.autoAdvance = false
        val canvas = rule.onNodeWithTag("handwriting-canvas")
        val b = canvas.fetchSemanticsNode().boundsInWindow
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })

        fun send(down: Long, action: Int, x: Float, y: Float, buttons: Int) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f })
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, props, coords, 0, buttons,
                1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            rule.runOnUiThread { rule.activity.window.decorView.dispatchTouchEvent(e) }
            e.recycle()
            rule.mainClock.advanceTimeByFrame()
        }
        fun blank(): Boolean {
            rule.mainClock.advanceTimeByFrame()
            val px = canvas.captureToImage().toPixelMap()
            return (10 until px.width - 10 step 3).all { x -> (10 until px.height - 10 step 3).all { y -> px[x, y].red > 0.9f } }
        }

        val top = b.top + b.height * 0.2f
        val bottom = b.top + b.height * 0.7f
        val xs = (1..3).map { b.left + b.width * it / 4 }
        for (x in xs) {
            val d = SystemClock.uptimeMillis()
            send(d, MotionEvent.ACTION_DOWN, x, top, 0)
            for (i in 1..6) send(d, MotionEvent.ACTION_MOVE, x, top + (bottom - top) * i / 6, 0)
            send(d, MotionEvent.ACTION_UP, x, bottom, 0)
        }
        assertFalse("三笔竖线应已画出", blank())

        val y = (top + bottom) / 2
        val d = SystemClock.uptimeMillis()
        send(d, MotionEvent.ACTION_DOWN, b.left + 10, y, MotionEvent.BUTTON_STYLUS_PRIMARY)
        for (i in 1..12) send(d, MotionEvent.ACTION_MOVE, b.left + 10 + (b.width - 20) * i / 12, y, MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(d, MotionEvent.ACTION_UP, b.right - 10, y, MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertTrue("一次拖动应擦掉路过的三笔", blank())
    }
}
