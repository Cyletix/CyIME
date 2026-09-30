package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.semantics.SemanticsPropertyKey

/** Nonrectangular controls expose the same outline they draw, so geometry tests
 * do not mistake overlapping bounding boxes for overlapping visible controls. */
internal data class RenderedControlShape(val outline: Outline, val size: Size)
internal val ControlShape = SemanticsPropertyKey<RenderedControlShape>("RenderedControlShape")
