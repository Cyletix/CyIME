package com.kingzcheung.xime.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kingzcheung.xime.ui.settings.HandwritingTrainingScreen
import org.junit.Rule
import org.junit.Test

/** 训练画板：S Pen 侧键 / 笔尾橡皮按笔迹擦整笔，撤销能恢复被擦的笔。 */
class HandwritingTrainingEraserTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun stroke(
        fromX: Float, fromY: Float, toX: Float, toY: Float,
        toolType: Int, buttonState: Int = 0, source: Int = InputDevice.SOURCE_STYLUS,
    ) {
        val down = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType })
        fun send(action: Int, t: Long, x: Float, y: Float) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
            val e = MotionEvent.obtain(down, t, action, 1, props, coords, 0, buttonState, 1f, 1f, 0, 0, source, 0)
            rule.runOnUiThread { rule.activity.window.decorView.dispatchTouchEvent(e) }
            e.recycle()
        }
        send(MotionEvent.ACTION_DOWN, down, fromX, fromY)
        for (i in 1..10) {
            send(MotionEvent.ACTION_MOVE, down + i * 16L, fromX + (toX - fromX) * i / 10, fromY + (toY - fromY) * i / 10)
        }
        send(MotionEvent.ACTION_UP, down + 176L, toX, toY)
        rule.waitForIdle()
    }

    /** 用内置的假名题（外置题库在测试机上没有），等读完“跳过”可用。 */
    private fun waitForPrompt() {
        rule.waitUntil(15_000) { runCatching { rule.onNodeWithText("跳过").assertIsEnabled() }.isSuccess }
    }

    @Test fun sideButtonAndEraserTipEraseWholeStrokesAndUndoRestoresThem() {
        rule.activity.getSharedPreferences("handwriting_training", android.content.Context.MODE_PRIVATE)
            .edit().putString("type", "kana").commit()
        rule.setContent { MaterialTheme { HandwritingTrainingScreen(onBack = {}) } }
        waitForPrompt()
        val b = rule.onNodeWithTag("training-board").fetchSemanticsNode().boundsInWindow
        val y = b.top + b.height / 2
        val next = rule.onNodeWithText("下一题")

        // 手指写一横
        stroke(b.left + 40, y, b.right - 40, y, MotionEvent.TOOL_TYPE_FINGER, source = InputDevice.SOURCE_TOUCHSCREEN)
        next.assertIsEnabled()

        // 按住侧键竖着划过这一横：整笔擦掉，画板空了
        val cx = b.left + b.width / 2
        stroke(cx, y - 80, cx, y + 80, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY)
        next.assertIsNotEnabled()

        // 撤销恢复被擦的笔
        rule.onNodeWithContentDescription("撤销").performClick()
        next.assertIsEnabled()

        // 笔尾橡皮同样擦除
        stroke(cx, y - 80, cx, y + 80, MotionEvent.TOOL_TYPE_ERASER)
        next.assertIsNotEnabled()

        // 不按侧键的笔正常书写；橡皮没碰到的笔保留
        stroke(b.left + 40, y + 200, b.right - 40, y + 200, MotionEvent.TOOL_TYPE_STYLUS)
        next.assertIsEnabled()
        stroke(cx, y - 80, cx, y + 80, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY)
        next.assertIsEnabled()

        // 用过笔之后手指不再出墨迹（等过防手掌的 1 秒，验证的是“用笔时禁用手指”本身）
        rule.onNodeWithText("重写").performClick()
        next.assertIsNotEnabled()
        Thread.sleep(1_200)
        stroke(b.left + 40, y, b.right - 40, y, MotionEvent.TOOL_TYPE_FINGER, source = InputDevice.SOURCE_TOUCHSCREEN)
        next.assertIsNotEnabled()
    }

    @Test fun fastSideButtonSwipeErasesStrokesBetweenSamplesAndButtonMidStrokeSwitchesToErase() {
        rule.activity.getSharedPreferences("handwriting_training", android.content.Context.MODE_PRIVATE)
            .edit().putString("type", "kana").commit()
        rule.setContent { MaterialTheme { HandwritingTrainingScreen(onBack = {}) } }
        waitForPrompt()
        val b = rule.onNodeWithTag("training-board").fetchSemanticsNode().boundsInWindow
        val y = b.top + b.height / 2
        val next = rule.onNodeWithText("下一题")
        val cx = b.left + b.width / 2
        stroke(cx, y - 300, cx, y + 300, MotionEvent.TOOL_TYPE_STYLUS)  // 一竖
        next.assertIsEnabled()
        fun hover(action: Int) {
            val now = SystemClock.uptimeMillis()
            val p = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
            val c = arrayOf(MotionEvent.PointerCoords().apply { x = b.left + 40; this.y = y })
            val e = MotionEvent.obtain(now, now, action, 1, p, c, 0, MotionEvent.BUTTON_STYLUS_PRIMARY, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            rule.runOnUiThread { rule.activity.window.decorView.dispatchGenericMotionEvent(e) }
            e.recycle()
            rule.waitForIdle()
        }
        // 快速横扫：只有两个采样点，分别在竖线左右两侧很远处
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, buttons: Int) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f })
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, props, coords, 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            rule.runOnUiThread { rule.activity.window.decorView.dispatchTouchEvent(e) }
            e.recycle()
            rule.waitForIdle()
        }
        hover(MotionEvent.ACTION_HOVER_ENTER)
        hover(MotionEvent.ACTION_HOVER_MOVE)
        send(MotionEvent.ACTION_DOWN, b.left + 60, MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(MotionEvent.ACTION_MOVE, b.right - 60, MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(MotionEvent.ACTION_UP, b.right - 60, MotionEvent.BUTTON_STYLUS_PRIMARY)
        next.assertIsNotEnabled()
        // 先写字、中途才按下侧键：这一笔作废并转为擦除
        stroke(cx, y - 300, cx, y + 300, MotionEvent.TOOL_TYPE_STYLUS)
        next.assertIsEnabled()
        send(MotionEvent.ACTION_DOWN, b.left + 60, 0)
        send(MotionEvent.ACTION_MOVE, b.left + 80, 0)
        send(MotionEvent.ACTION_MOVE, b.right - 60, MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(MotionEvent.ACTION_UP, b.right - 60, MotionEvent.BUTTON_STYLUS_PRIMARY)
        next.assertIsNotEnabled()
    }

    @Test fun holdSideButtonThenDragErasesEveryStrokeOnTheWay() {
        rule.activity.getSharedPreferences("handwriting_training", android.content.Context.MODE_PRIVATE)
            .edit().putString("type", "kana").commit()
        rule.setContent { MaterialTheme { HandwritingTrainingScreen(onBack = {}) } }
        waitForPrompt()
        val b = rule.onNodeWithTag("training-board").fetchSemanticsNode().boundsInWindow
        val y = b.top + b.height / 2
        val next = rule.onNodeWithText("下一题")
        val xs = (1..4).map { b.left + b.width * it / 5 }
        for (x in xs) stroke(x, y - 200, x, y + 200, MotionEvent.TOOL_TYPE_STYLUS)
        next.assertIsEnabled()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, touch: Boolean) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = if (touch) 1f else 0f })
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, props, coords, 0,
                MotionEvent.BUTTON_STYLUS_PRIMARY, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            rule.runOnUiThread {
                val v = rule.activity.window.decorView
                if (touch) v.dispatchTouchEvent(e) else v.dispatchGenericMotionEvent(e)
            }
            e.recycle()
            rule.waitForIdle()
        }
        // 先按住侧键悬停（圈出现），再落笔，按 20 步一路拖到右边
        send(MotionEvent.ACTION_HOVER_ENTER, b.left + 20, touch = false)
        send(MotionEvent.ACTION_HOVER_MOVE, b.left + 30, touch = false)
        send(MotionEvent.ACTION_HOVER_EXIT, b.left + 30, touch = false)
        send(MotionEvent.ACTION_DOWN, b.left + 30, touch = true)
        for (i in 1..20) send(MotionEvent.ACTION_MOVE, b.left + 30 + (b.width - 60) * i / 20, touch = true)
        send(MotionEvent.ACTION_UP, b.right - 30, touch = true)
        next.assertIsNotEnabled()  // 4 条全擦掉
        rule.onNodeWithContentDescription("撤销").performClick()
        next.assertIsEnabled()     // 一次拖动算一步，撤销后 4 条都回来
    }

    @Test fun pasteUsesClipboardTextAsTheCurrentPrompt() {
        rule.activity.getSharedPreferences("handwriting_training", android.content.Context.MODE_PRIVATE)
            .edit().putString("type", "kana").commit()
        rule.setContent { MaterialTheme { HandwritingTrainingScreen(onBack = {}) } }
        waitForPrompt()
        rule.runOnUiThread {
            val cm = rule.activity.getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("t", "手写测试"))
        }
        rule.onNodeWithText("粘贴").performClick()
        rule.onNodeWithText("手写测试").assertExists()
        rule.onNodeWithText("粘贴的题").assertExists()
        rule.onNodeWithText("跳过").performClick()
        rule.onNodeWithText("粘贴的题").assertDoesNotExist()
    }

    @Test fun erasingShowsRingOfTheEraseRadiusUntilPenLeaves() {
        rule.activity.getSharedPreferences("handwriting_training", android.content.Context.MODE_PRIVATE)
            .edit().putString("type", "kana").commit()
        rule.setContent { MaterialTheme { HandwritingTrainingScreen(onBack = {}) } }
        waitForPrompt()
        val board = rule.onNodeWithTag("training-board")
        val b = board.fetchSemanticsNode().boundsInWindow
        val cx = b.left + b.width / 3
        val cy = b.top + b.height / 3
        val radius = 12f * rule.activity.resources.displayMetrics.density
        val down = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun send(action: Int) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { x = cx; y = cy; pressure = 1f })
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, props, coords, 0,
                MotionEvent.BUTTON_STYLUS_PRIMARY, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            rule.runOnUiThread { rule.activity.window.decorView.dispatchTouchEvent(e) }
            e.recycle()
            rule.waitForIdle()
        }
        fun ringShown(): Boolean {
            val px = board.captureToImage().toPixelMap()
            return px[(cx - b.left + radius * 0.5f).toInt(), (cy - b.top).toInt()] !=
                px[(cx - b.left + radius * 4).toInt(), (cy - b.top).toInt()]
        }
        send(MotionEvent.ACTION_DOWN)
        org.junit.Assert.assertTrue("按住侧键擦除时应在笔尖显示橡皮圈", ringShown())
        send(MotionEvent.ACTION_UP)
        org.junit.Assert.assertFalse("抬笔后先收起橡皮圈", ringShown())
        // 抬笔后仍按着侧键悬停：圈继续跟着笔；笔离开画板（悬停退出）后圈消失
        fun hover(action: Int) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { x = cx; y = cy })
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, props, coords, 0,
                MotionEvent.BUTTON_STYLUS_PRIMARY, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            rule.runOnUiThread { rule.activity.window.decorView.dispatchGenericMotionEvent(e) }
            e.recycle()
            rule.waitForIdle()
        }
        hover(MotionEvent.ACTION_HOVER_ENTER)
        hover(MotionEvent.ACTION_HOVER_MOVE)
        org.junit.Assert.assertTrue("按住侧键悬停时橡皮圈在", ringShown())
        hover(MotionEvent.ACTION_HOVER_EXIT)
        org.junit.Assert.assertFalse("笔离开后橡皮圈消失", ringShown())
    }
}
