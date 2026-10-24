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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.FLOATING_DRAG_BAR_HEIGHT_DP
import com.kingzcheung.xime.ui.keyboard.FLOATING_ROTATE_HANDLE_HEIGHT_DP
import com.kingzcheung.xime.ui.keyboard.FloatingKeyboardContainer
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
 * 悬浮键盘绕中心旋转：顶上的旋转手柄、转过后点到的键、按住手柄拖动的角度和限位。
 * 设了 CYIME_RENDER_OUT 时另外把几种角度/位置拼成一张图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatingRotationRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val hostW = 400
    private val hostH = 600
    private val contentH = 220
    private var rotation by mutableFloatStateOf(0f)
    private var offsetX by mutableIntStateOf(0)
    private var offsetY by mutableIntStateOf(120)
    private var tapped = ""
    private var savedRotation = Float.NaN

    private fun show() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(hostW.dp, hostH.dp).background(Color(0xFFDDE3EA)).testTag("host")) {
                    FloatingKeyboardContainer(
                        isFloatingMode = true, scaleFactor = 0.8f, contentHeightDp = contentH,
                        offsetX = offsetX, offsetY = offsetY, backgroundColor = Color(0xFFF3F4F6),
                        dragIndicatorColor = Color(0xFF8A8F98),
                        rotationDegrees = rotation,
                        onRotate = { savedRotation = it; rotation = it },
                        onDrag = { dx, dy -> offsetX += dx.toInt(); offsetY += dy.toInt() }, onDragEnd = {},
                    ) {
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
                }
            }
        }
    }

    /** 卡片（未旋转）在宿主中的中心与尺寸，单位 dp。 */
    private fun cardCenter(): Pair<Float, Float> {
        val cardH = contentH + FLOATING_DRAG_BAR_HEIGHT_DP
        return (hostW / 2f + offsetX) to (hostH - offsetY - cardH / 2f)
    }

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
        val cardH = contentH + FLOATING_DRAG_BAR_HEIGHT_DP
        // 第二行 A 键的中心（未旋转时相对卡片中心），按 45° 转到屏幕上去点。
        val keyW = (cardW - 4f) / 5f
        val rowH = (contentH - 44f) / 3f
        val local = Offset(-cardW / 2f + 2f + keyW / 2f, -cardH / 2f + 44f + rowH * 1.5f)
        val target = rotatedPoint(local.x, local.y, 45f)
        rule.onNodeWithTag("host").performTouchInput { click(Offset(target.x * density, target.y * density)) }
        rule.waitForIdle()
        assertEquals("A", tapped)
    }

    @Test fun draggingTheHandleTurnsAroundTheCenterAndSaves() {
        show()
        val density = rule.activity.resources.displayMetrics.density
        val cardH = contentH + FLOATING_DRAG_BAR_HEIGHT_DP
        val (cx, cy) = cardCenter()
        val radius = cardH / 2f + FLOATING_ROTATE_HANDLE_HEIGHT_DP / 2f
        rule.onNodeWithTag("host").performTouchInput {
            fun at(deg: Double) = Offset((cx + radius * sin(Math.toRadians(deg))).toFloat() * density,
                (cy - radius * cos(Math.toRadians(deg))).toFloat() * density)
            down(at(0.0))
            for (step in 1..10) moveTo(at(step * 3.0))
            up()
        }
        rule.waitForIdle()
        assertTrue("saved $savedRotation", savedRotation in 28f..32f)
    }

    @Test fun renderSheet() {
        val out = System.getenv("CYIME_RENDER_OUT") ?: return
        show()
        val cases = listOf(
            Triple(0f, 0 to 120, "0°"), Triple(30f, 0 to 120, "30°"), Triple(-45f, 0 to 120, "-45°"),
            Triple(90f, 0 to 120, "90°"), Triple(20f, 0 to 0, "20° 拖到最低"), Triple(-90f, -400 to 120, "-90° 拖到最左"),
        )
        val shots = cases.map { (deg, offset, label) ->
            rule.runOnIdle { rotation = deg; offsetX = offset.first; offsetY = offset.second }
            rule.waitForIdle()
            val host = rule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val left = host.left.toInt().coerceAtLeast(0)
            val top = host.top.toInt().coerceAtLeast(0)
            label to Bitmap.createBitmap(full, left, top, minOf(full.width - left, hostW), minOf(full.height - top, hostH))
        }
        val cellW = hostW + 12
        val cellH = hostH + 40
        val sheet = Bitmap.createBitmap(cellW * 3 + 12, cellH * 2 + 12, Bitmap.Config.ARGB_8888)
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
