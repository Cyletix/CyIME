package com.kingzcheung.xime.ui.render

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.FLOATING_DRAG_BAR_HEIGHT_DP
import com.kingzcheung.xime.ui.keyboard.FLOATING_ROTATE_HANDLE_GAP_DP
import com.kingzcheung.xime.ui.keyboard.FLOATING_ROTATE_HANDLE_SIZE_DP
import com.kingzcheung.xime.ui.keyboard.FloatingKeyboardContainer
import com.kingzcheung.xime.ui.keyboard.KeyboardResizeOverlay
import com.kingzcheung.xime.ui.keyboard.KeyboardResizePreviewState
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardResizePreviewState
import com.kingzcheung.xime.ui.keyboard.ResizeRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * 悬浮键盘绕中心旋转：平时按保存的角度显示、点到的键；调节界面里按住旋转手柄拖动的角度、确认保存。
 * 设了 CYIME_RENDER_OUT 时另外把几种角度/位置和调节界面拼成一张图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatingRotationRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val hostW = 400
    private val hostH = 600
    private val contentH = 220
    private val cardH = contentH + FLOATING_DRAG_BAR_HEIGHT_DP
    private var rotation by mutableFloatStateOf(0f)
    private var offsetX by mutableIntStateOf(0)
    private var offsetY by mutableIntStateOf(120)
    private var resizing by mutableStateOf(false)
    private val resizeRotation = mutableFloatStateOf(0f)
    private var resizeRect by mutableStateOf(ResizeRect(40f, 600f - 120f - 248f, 360f, 600f - 120f))
    private var tapped = ""
    private var confirmed = Float.NaN

    @Composable
    private fun FakeKeys() {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFFCBD5E1)), Alignment.Center) {
                Text("候选 · 工具栏", fontSize = 14.sp)
            }
            for (row in listOf("QWERT", "ASDFG", "ZXCVB")) Row(Modifier.weight(1f).padding(2.dp)) {
                for (key in row) Box(Modifier.weight(1f).fillMaxSize().padding(2.dp)
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .clickable { tapped = key.toString() }, Alignment.Center) { Text(key.toString()) }
            }
        }
    }

    private fun show() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(hostW.dp, hostH.dp).background(Color(0xFFDDE3EA)).testTag("host")) {
                    val preview = KeyboardResizePreviewState(rect = resizeRect, initialRect = resizeRect,
                        onRectChange = { resizeRect = it }, bounds = ResizeRect(0f, 0f, hostW.toFloat(), hostH.toFloat()),
                        rotation = resizeRotation)
                    FloatingKeyboardContainer(
                        isFloatingMode = true, scaleFactor = 0.8f, contentHeightDp = contentH,
                        offsetX = offsetX, offsetY = offsetY, backgroundColor = Color(0xFFF3F4F6),
                        dragIndicatorColor = Color(0xFF8A8F98),
                        rotationDegrees = rotation,
                        previewRectState = if (resizing) androidx.compose.runtime.rememberUpdatedState(resizeRect) else null,
                        previewRotationState = if (resizing) resizeRotation else null,
                        onDrag = { dx, dy -> offsetX += dx.toInt(); offsetY += dy.toInt() }, onDragEnd = {},
                    ) { FakeKeys() }
                    if (resizing) CompositionLocalProvider(LocalKeyboardResizePreviewState provides preview) {
                        KeyboardResizeOverlay(contentH, contentH, 0, true, initialWidthDp = 320,
                            onHeightChange = {}, onBottomPaddingChange = {}, onOpacityChange = {}, onReset = {},
                            onConfirm = { _, _, _, _ -> }, onCancel = {}, onFloatingModeChange = {},
                            onRotationConfirm = { confirmed = it }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    /** 卡片（未旋转）在宿主中的中心，单位 dp。 */
    private fun cardCenter(): Pair<Float, Float> = (hostW / 2f + offsetX) to (hostH - offsetY - cardH / 2f)

    private fun rotatedPoint(vx: Float, vy: Float, deg: Float): Offset {
        val (cx, cy) = cardCenter()
        val r = Math.toRadians(deg.toDouble())
        return Offset(cx + vx * cos(r).toFloat() - vy * sin(r).toFloat(), cy + vx * sin(r).toFloat() + vy * cos(r).toFloat())
    }

    @Test fun tapOnRotatedKeyHitsThatKey() {
        rotation = 45f
        show()
        val density = rule.activity.resources.displayMetrics.density
        val cardW = hostW * 0.8f
        // 第二行 A 键的中心（未旋转时相对卡片中心），按 45° 转到屏幕上去点。
        val keyW = (cardW - 4f) / 5f
        val rowH = (contentH - 44f) / 3f
        val local = Offset(-cardW / 2f + 2f + keyW / 2f, -cardH / 2f + 44f + rowH * 1.5f)
        val target = rotatedPoint(local.x, local.y, 45f)
        rule.onNodeWithTag("host").performTouchInput { click(Offset(target.x * density, target.y * density)) }
        rule.waitForIdle()
        assertEquals("A", tapped)
    }

    @Test fun normalFloatingKeyboardHasNoRotateHandle() {
        show()
        assertTrue(rule.onNodeWithTag("keyboard-resize-rotate-handle").isNotDisplayedOrMissing())
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.isNotDisplayedOrMissing(): Boolean =
        runCatching { fetchSemanticsNode() }.isFailure

    @Test fun resizeHandleTurnsAroundTheCenterAndConfirmSaves() {
        resizing = true
        show()
        val density = rule.activity.resources.displayMetrics.density
        val cx = resizeRect.centerX
        val cy = resizeRect.centerY
        val radius = cardH / 2f + FLOATING_ROTATE_HANDLE_GAP_DP + FLOATING_ROTATE_HANDLE_SIZE_DP / 2f
        rule.onNodeWithTag("host").performTouchInput {
            fun at(deg: Double) = Offset((cx + radius * sin(Math.toRadians(deg))).toFloat() * density,
                (cy - radius * cos(Math.toRadians(deg))).toFloat() * density)
            down(at(0.0))
            for (step in 1..10) moveTo(at(step * 3.0))
            up()
        }
        rule.waitForIdle()
        assertTrue("rotation ${resizeRotation.floatValue}", resizeRotation.floatValue in 28f..32f)
        rule.onAllNodes(hasContentDescription("确认"))[0].performClick()
        rule.waitForIdle()
        assertEquals(resizeRotation.floatValue, confirmed, 0.01f)
    }

    @Test fun renderSheet() {
        val out = System.getenv("CYIME_RENDER_OUT") ?: return
        show()
        data class Case(val label: String, val deg: Float, val x: Int, val y: Int, val resize: Boolean = false)
        val cases = listOf(
            Case("0°", 0f, 0, 120), Case("30°", 30f, 0, 120), Case("-45°", -45f, 0, 120),
            Case("90°", 90f, 0, 120), Case("20° 拖到最低", 20f, 0, 0), Case("-90° 拖到最左", -90f, -400, 120),
            Case("调节界面 0°", 0f, 0, 120, true), Case("调节界面 30°", 30f, 0, 120, true),
            Case("调节界面 -60°", -60f, 0, 120, true),
        )
        val shots = cases.map { c ->
            rule.runOnIdle {
                rotation = c.deg; offsetX = c.x; offsetY = c.y; resizing = c.resize
                resizeRotation.floatValue = c.deg
                resizeRect = ResizeRect(40f, hostH - c.y - cardH.toFloat(), 360f, hostH - c.y.toFloat())
            }
            rule.waitForIdle()
            val host = rule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val left = host.left.toInt().coerceAtLeast(0)
            val top = host.top.toInt().coerceAtLeast(0)
            c.label to Bitmap.createBitmap(full, left, top, minOf(full.width - left, hostW), minOf(full.height - top, hostH))
        }
        val cellW = hostW + 12
        val cellH = hostH + 40
        val rows = (shots.size + 2) / 3
        val sheet = Bitmap.createBitmap(cellW * 3 + 12, cellH * rows + 12, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        canvas.drawColor(0xFF808080.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 24f }
        shots.forEachIndexed { i, (label, bmp) ->
            val x = 12f + (i % 3) * cellW
            val y = 12f + (i / 3) * cellH
            canvas.drawText(label, x, y + 24f, paint)
            canvas.drawBitmap(bmp, x, y + 34f, null)
        }
        File(out).mkdirs()
        File(out, "${System.getenv("CYIME_RENDER_NAME") ?: "floating-rotation"}.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
