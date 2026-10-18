package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/**
 * Fullscreen handwriting reports its control footer here (window coordinates, null when hidden)
 * so the glass blur covers only the footer and leaves the writing area over the app clear.
 */
internal object HandwritingGlassRegion {
    /** Set by the input method service; null in previews and tests. Main thread only. */
    var reporter: ((android.graphics.Rect?) -> Unit)? = null
}

/** An explicit root modifier, not an inherited ambient: nested menus keep their own surface. */
private object InheritedKeyboardBackground : Modifier.Element

internal fun Modifier.inheritKeyboardBackground(inherit: Boolean = true): Modifier =
    if (inherit) then(InheritedKeyboardBackground) else this

internal fun Modifier.inheritsKeyboardBackground(): Boolean = any { it === InheritedKeyboardBackground }

/** Keep hidden pages measured and composed, but never paint their keys through a replacement panel. */
@Composable
internal fun Modifier.excludeRevealedKeyboardPanels(
    enabled: Boolean,
    vararg panels: KeyboardPanelRevealState,
): Modifier {
    if (!enabled) return this
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInWindow() }.drawWithContent {
        fun drawOutside(index: Int) {
            if (index == panels.size) {
                drawContent()
                return
            }
            val panel = panels[index]
            val bounds = panel.boundsInWindow
            val progress = panel.progress.value.coerceIn(0f, 1f)
            if (progress <= 0f || bounds.width <= 0f || bounds.height <= 0f) {
                drawOutside(index + 1)
            } else {
                clipRect(bounds.left - origin.x, bounds.top - origin.y,
                    bounds.right - origin.x, bounds.top + bounds.height * progress - origin.y,
                    ClipOp.Difference) { drawOutside(index + 1) }
            }
        }
        drawOutside(0)
    }
}
