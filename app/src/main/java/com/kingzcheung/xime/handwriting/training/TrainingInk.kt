package com.kingzcheung.xime.handwriting.training

import com.kingzcheung.xime.handwriting.PenEraser

/** 训练画板的笔迹编辑：按笔迹擦除整笔（与输入法手写板 HandwritingInputSession.erase 同一判定）。 */
object TrainingInk {

    /**
     * 返回擦除后剩下的笔画。[eraser] 是橡皮轨迹（按顺序的点，相邻两点连成线段），
     * 轨迹任一段距某笔折线不超过 [radius] 就擦掉整笔；判定与输入法手写板共用 PenEraser。
     */
    fun erase(strokes: List<TrainingStroke>, eraser: List<Pair<Float, Float>>, radius: Float): List<TrainingStroke> {
        if (strokes.isEmpty() || eraser.isEmpty()) return strokes
        val segments = if (eraser.size == 1) listOf(eraser[0] to eraser[0]) else eraser.zipWithNext()
        return strokes.filterNot { stroke ->
            val p = stroke.points
            segments.any { (a, b) -> PenEraser.hits(p.size, { p[it].x }, { p[it].y }, a.first, a.second, b.first, b.second, radius) }
        }
    }

    /** 把时间平移到第一笔第一个点为 0（前面的笔被擦掉后，保存的样本仍从 0 开始）。 */
    fun rebase(strokes: List<TrainingStroke>): List<TrainingStroke> {
        val t0 = strokes.firstOrNull()?.points?.firstOrNull()?.t ?: return strokes
        if (t0 == 0L) return strokes
        return strokes.map { s -> s.copy(points = s.points.map { it.copy(t = it.t - t0) }) }
    }
}
