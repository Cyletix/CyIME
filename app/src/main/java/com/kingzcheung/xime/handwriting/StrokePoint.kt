package com.kingzcheung.xime.handwriting

data class StrokePoint(
    val x: Float,
    val y: Float,
    val timeMs: Long = System.currentTimeMillis(),
    /** 由手写笔（含橡皮端）写出；只影响笔迹颜色，不参与识别。 */
    val pen: Boolean = false,
    /** 笔的压感（0..1），只用于笔迹粗细；手指为 1。 */
    val pressure: Float = 1f,
)
