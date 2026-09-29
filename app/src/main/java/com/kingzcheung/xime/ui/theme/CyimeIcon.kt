package com.kingzcheung.xime.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Four open, folded cells. One deterministic geometry, four shared material recipes. */
internal class CyimeIconScene(private val style: VisualStyle, private val background: Boolean) {
    private val material = CyMaterials.forStyle(style)
    private val cell = Path().apply {
        moveTo(23f, 32f); quadraticBezierTo(22f, 28f, 26f, 24f)
        lineTo(39f, 11f); quadraticBezierTo(43f, 7f, 47f, 11f)
        lineTo(63f, 27f); quadraticBezierTo(66f, 31f, 60f, 31f)
        lineTo(44f, 31f); quadraticBezierTo(38f, 31f, 34f, 36f)
        lineTo(30f, 40f); quadraticBezierTo(26f, 44f, 23f, 39f); close()
    }
    private val face = Path().apply {
        moveTo(40f, 11f); quadraticBezierTo(43f, 8f, 47f, 11f)
        lineTo(63f, 27f); quadraticBezierTo(66f, 31f, 60f, 31f)
        lineTo(44f, 31f); quadraticBezierTo(41f, 31f, 39f, 28f)
        lineTo(33f, 21f); quadraticBezierTo(31f, 18f, 35f, 15f); close()
    }
    private val cellPaint = MaterialPaint(material, cell.getBounds(), .8f)
    private val facePaint = MaterialPaint(material.copy(topLight = (material.topLight + .22f).coerceAtMost(.8f),
        body = material.body + .12f), face.getBounds(), .7f)
    private val tile = Path().apply { addRoundRect(RoundRect(4f, 4f, 96f, 96f, CornerRadius(22f))) }
    private val tilePaint = MaterialPaint(material, Rect(4f, 4f, 96f, 96f), .6f, .6f)
    fun DrawScope.render() {
        val side = size.minDimension
        translate((size.width - side) / 2, (size.height - side) / 2) {
            scale(side / 100, side / 100, Offset.Zero) {
                if (background) {
                    drawPath(tile, requireNotNull(VisualStyles.palette(style)).keyboardBgLight)
                    with(tilePaint) { paint(tile) }
                }
                scale(if (background) .82f else 1f, pivot = Offset(50f, 50f)) {
                    repeat(4) { i -> rotate(i * 90f, Offset(50f, 50f)) {
                        drawPath(cell, material.tint.copy(alpha = if (style == VisualStyle.NEON) .12f else .55f))
                        with(cellPaint) { paint(cell) }
                        if (style != VisualStyle.NEON) {
                            drawPath(face, material.highlight.copy(alpha = if (style == VisualStyle.FACET) .32f else .15f))
                            with(facePaint) { paint(face) }
                        }
                    } }
                }
            }
        }
    }
}

@Composable
fun CyimeGeneratedIcon(style: VisualStyle, modifier: Modifier = Modifier, background: Boolean = false) {
    require(style != VisualStyle.ORIGINAL)
    Box(modifier.semantics { contentDescription = "CyIME · ${style.title}" }.drawWithCache {
        val scene = CyimeIconScene(style, background)
        onDrawBehind { with(scene) { render() } }
    })
}

/** Used by the asset exporter too: launcher assets are rendered from the production material. */
object CyimeIconGenerator {
    fun render(style: VisualStyle, pixels: Int = 432, adaptiveForeground: Boolean = false): Bitmap {
        require(pixels in 48..2048)
        require(style != VisualStyle.ORIGINAL)
        val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
        val scene = CyimeIconScene(style, !adaptiveForeground)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(pixels.toFloat(), pixels.toFloat())) {
            if (adaptiveForeground) scale(.65f) { with(scene) { render() } }
            else with(scene) { render() }
        }
        return bitmap
    }
}
