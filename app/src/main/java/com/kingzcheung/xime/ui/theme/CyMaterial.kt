package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp

/** Static optical layers shared by the icon generator and interface surfaces. */
@Immutable
data class CyMaterial(
    val tint: Color, val highlight: Color, val shadow: Color,
    val body: Float, val topLight: Float, val bottomTint: Float,
    val depth: Float, val depthOffset: Float,
    val rim: Float, val edge: Float, val edgeWidth: Float,
    val bloom: Float, val bloomWidth: Float,
    val reflection: Float, val coverage: Float, val ambient: Float,
)

object CyMaterials {
    private val purple = Color(0xFF9E72FF)
    private val white = Color(0xFFF4E9FF)
    private val ink = Color(0xFF291443)
    private val neon = CyMaterial(purple, white, ink, .10f, .08f, .12f, .35f, 2.4f,
        .30f, .90f, 1.0f, .23f, 5.5f, .06f, .28f, .12f)
    private val glass = CyMaterial(purple, white, ink, .16f, .25f, .18f, .40f, 2.8f,
        .22f, .66f, .8f, .10f, 4f, .22f, .52f, .10f)
    private val facet = CyMaterial(purple, white, ink, .26f, .20f, .12f, .26f, 1.8f,
        .08f, .27f, .7f, .035f, 3f, .10f, .36f, .06f)
    private val frost = CyMaterial(Color(0xFFA983ED), Color.White, Color(0xFF705397),
        .06f, .42f, .09f, .13f, 2f, .48f, .80f, .9f, .045f, 4f, .24f, .65f, .09f)
    fun forStyle(style: VisualStyle): CyMaterial = when (style) {
        VisualStyle.NEON -> neon
        VisualStyle.GLASS -> glass
        VisualStyle.FACET -> facet
        VisualStyle.FROST -> frost
        VisualStyle.ORIGINAL -> error("Original appearance has no generated material")
    }
}

/** Brushes and outlines are prepared on size/style changes, not on each key press. */
internal class MaterialPaint(val spec: CyMaterial, val bounds: Rect, unit: Float, strength: Float = 1f) {
    private val edge = spec.edgeWidth * unit
    private val bloom = spec.bloomWidth * unit
    private val inset = spec.depthOffset * unit
    private val gain = strength
    private val body = Brush.linearGradient(listOf(spec.highlight.copy(alpha = spec.topLight * gain),
        spec.tint.copy(alpha = spec.body * gain), spec.shadow.copy(alpha = spec.bottomTint * gain)),
        bounds.topLeft, bounds.bottomRight)
    private val depth = Brush.verticalGradient(listOf(Color.Transparent, spec.shadow.copy(alpha = spec.depth * gain)),
        bounds.top + bounds.height * .62f, bounds.bottom)
    private val light = Brush.radialGradient(listOf(spec.highlight.copy(alpha = spec.reflection * gain), Color.Transparent),
        Offset(bounds.left + bounds.width * .23f, bounds.top), (bounds.width + bounds.height) * spec.coverage)
    private val rim = Brush.linearGradient(listOf(spec.highlight.copy(alpha = spec.edge * gain),
        spec.tint.copy(alpha = spec.edge * .20f * gain), spec.tint.copy(alpha = spec.edge * .65f * gain)),
        bounds.topLeft, bounds.bottomRight)
    private val bloomOuter = Stroke(bloom)
    private val bloomInner = Stroke(bloom * .4f)
    private val edgeStroke = Stroke(edge)
    private val rimStroke = Stroke(edge * .8f)
    private val depthStroke = Stroke(inset)

    fun DrawScope.paint(path: Path) {
        translate(0f, inset) { drawPath(path, spec.shadow.copy(alpha = spec.depth * gain), style = depthStroke) }
        // Low energy static bloom is separate from the existing press animation.
        drawPath(path, spec.tint.copy(alpha = spec.bloom * .25f * gain), style = bloomOuter)
        drawPath(path, spec.tint.copy(alpha = spec.bloom * gain), style = bloomInner)
        drawPath(path, body)
        clipPath(path) {
            drawRect(depth, bounds.topLeft, bounds.size)
            drawRect(light, bounds.topLeft, bounds.size)
        }
        val sx = (1f - inset * 2 / bounds.width).coerceIn(.1f, 1f)
        val sy = (1f - inset * 2 / bounds.height).coerceIn(.1f, 1f)
        scale(sx, sy, bounds.center) {
            drawPath(path, spec.highlight.copy(alpha = spec.rim * gain), style = rimStroke)
        }
        drawPath(path, rim, style = edgeStroke)
    }
}

internal fun Modifier.materialSurface(style: VisualStyle, radius: Dp, panel: Boolean): Modifier {
    if (style == VisualStyle.ORIGINAL) return this
    return drawWithCache {
        val inset = .7f * density
        val bounds = Rect(inset, inset, (size.width - inset).coerceAtLeast(inset), (size.height - inset).coerceAtLeast(inset))
        val path = Path().apply { addRoundRect(RoundRect(bounds, CornerRadius(radius.toPx().coerceAtMost(size.minDimension / 2)))) }
        val paint = MaterialPaint(CyMaterials.forStyle(style), bounds, density, if (panel) .65f else 1f)
        onDrawWithContent {
            if (bounds.width > 0 && bounds.height > 0) with(paint) { paint(path) }
            drawContent()
        }
    }
}

fun Modifier.visualEnvironment(style: VisualStyle): Modifier {
    if (style == VisualStyle.ORIGINAL) return this
    return drawWithCache {
        val spec = CyMaterials.forStyle(style)
        val a = Brush.radialGradient(listOf(spec.tint.copy(alpha = spec.ambient), Color.Transparent),
            Offset(size.width * .12f, size.height * .12f), size.maxDimension.coerceAtLeast(1f) * .8f)
        val b = Brush.radialGradient(listOf(spec.tint.copy(alpha = spec.ambient * .55f), Color.Transparent),
            Offset(size.width, size.height), size.maxDimension.coerceAtLeast(1f) * .7f)
        onDrawWithContent { drawRect(a); drawRect(b); drawContent() }
    }
}
