package com.kingzcheung.xime.ui.keyboard

/**
 * 移动光标结束后的保护间隔：这段时间内按下的空格不输入空格，免得断触或抬手再点把空格插在刚调好的位置。
 * 从最后一次光标移动或抬手算起；按住不放仍照常进入移动光标。时长由用户定为 0.2 秒（2026-10-22）。
 */
internal object SpaceAfterCursorGuard {
    const val GUARD_MS = 200L

    @Volatile private var lastCursorAt = Long.MIN_VALUE

    /** [uptimeMs] 用 SystemClock.uptimeMillis 时基，与触摸事件时间一致。 */
    fun mark(uptimeMs: Long) { lastCursorAt = uptimeMs }

    fun blocksSpace(downUptimeMs: Long): Boolean {
        val last = lastCursorAt
        return last != Long.MIN_VALUE && downUptimeMs >= last && downUptimeMs - last < GUARD_MS
    }

    internal fun resetForTest() { lastCursorAt = Long.MIN_VALUE }
}
