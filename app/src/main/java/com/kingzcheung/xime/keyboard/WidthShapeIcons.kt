package com.kingzcheung.xime.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 全角/半角图标与桌面版（CyIME-Windows 工具栏）一致：半角是月牙，全角是满圆，
 * 同样的 24 视框路径与描边，不用 Material 的宽度图标。
 */
private fun strokeIcon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw,
    ).build()

/** 半角：M14,3 A9,9 0 1 0 21,16 A9,9 0 0 1 14,3 Z */
internal val HalfWidthIcon: ImageVector by lazy {
    strokeIcon("HalfWidth") {
        moveTo(14f, 3f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 21f, y1 = 16f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 14f, y1 = 3f)
        close()
    }
}

/** 全角：M12,3 A9,9 0 1 0 12,21 A9,9 0 1 0 12,3 */
internal val FullWidthIcon: ImageVector by lazy {
    strokeIcon("FullWidth") {
        moveTo(12f, 3f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 12f, y1 = 21f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 12f, y1 = 3f)
        close()
    }
}
