package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.viewmodel.ShiftMode

/**
 * Shift 三态用标准上箭头区分：关闭为空心箭头，单次大写为实心箭头，
 * 大写锁定为实心箭头加底横线。三种状态只看形状就能分清，不依赖配色。
 */
internal object ShiftKeyIcons {
    private fun PathBuilder.arrow(stemBottom: Float) {
        moveTo(12f, 3.5f)
        lineTo(20.5f, 12.5f)
        lineTo(15.5f, 12.5f)
        lineTo(15.5f, stemBottom)
        lineTo(8.5f, stemBottom)
        lineTo(8.5f, 12.5f)
        lineTo(3.5f, 12.5f)
        close()
    }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply(block).build()

    val Off: ImageVector = icon("ShiftOff") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
            arrow(19.5f)
        }
    }

    val Single: ImageVector = icon("ShiftSingle") {
        path(fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) { arrow(19.5f) }
    }

    val Locked: ImageVector = icon("ShiftLocked") {
        path(fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) { arrow(16f) }
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.6f, 18.4f)
            lineTo(16.4f, 18.4f)
            lineTo(16.4f, 21f)
            lineTo(7.6f, 21f)
            close()
        }
    }

    fun forMode(mode: ShiftMode): ImageVector = when (mode) {
        ShiftMode.OFF -> Off
        ShiftMode.CAPS -> Locked
        else -> Single
    }

    fun description(mode: ShiftMode): String = when (mode) {
        ShiftMode.OFF -> "Shift，小写"
        ShiftMode.CAPS -> "Shift，大写锁定"
        else -> "Shift，下一个字母大写"
    }
}
