package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Pointer state is read only while drawing; no animation clock or pointer handler. */
@Composable
internal fun ShiftSlideLine(targets: ShiftSlideTargets, rootOrigin: () -> Offset, color: Color, modifier: Modifier) {
    Canvas(modifier.testTag("shift-slide-line")) {
        targets.drag?.let { drag ->
            val origin = rootOrigin()
            clipRect {
                drawLine(color, drag.origin - origin, drag.pointer - origin,
                    strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

/** 九键 0 键拖到候选时键盘里那段拖线；候选栏那段由候选栏自己画（[CandidateDropTargets.dragLine]）。 */
@Composable
internal fun CandidateDragLine(targets: CandidateDropTargets, rootOrigin: () -> Offset, color: Color, modifier: Modifier) {
    Canvas(modifier.testTag("candidate-drag-line")) {
        targets.dragLine?.let { (from, to) ->
            val origin = rootOrigin()
            clipRect {
                drawLine(color, from - origin, to - origin, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}
