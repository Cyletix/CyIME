package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Four open corners frame a plus; tint comes from the surrounding theme. */
internal val AddLayoutIcon: ImageVector = ImageVector.Builder("AddLayout", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8f, 3f); lineTo(3f, 3f); lineTo(3f, 8f)
        moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
        moveTo(3f, 16f); lineTo(3f, 21f); lineTo(8f, 21f)
        moveTo(16f, 21f); lineTo(21f, 21f); lineTo(21f, 16f)
        moveTo(8f, 12f); lineTo(16f, 12f)
        moveTo(12f, 8f); lineTo(12f, 16f)
    }
}.build()
