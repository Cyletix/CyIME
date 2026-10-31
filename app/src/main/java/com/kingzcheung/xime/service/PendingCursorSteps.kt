package com.kingzcheung.xime.service

import kotlin.math.sign

/**
 * 拖动移光标（长按空格、键盘区滑动）攒下、还没执行的格子。
 *
 * 每一格仍各排一个按键队列任务（保证与前后按键的顺序），但轮到的任务把此刻攒下的格子一次做完，
 * 后面轮到的任务发现已经做完就直接跳过。宿主回话慢时（B 站放视频时一次读字约 130 ms），
 * 原来一格等一格，手指停了光标还在走；现在最多落后正在执行的那一次。
 * 同方向、同是否扩选的格子合成一段；换方向不合并，到头再往回仍按原来逐格的结果。
 * 只在主线程使用。
 */
internal class PendingCursorSteps(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private companion object {
        /** 攒下的格子这么久都没有任务来做（输入法还没就绪等），不再留给下一次拖动。 */
        const val STALE_MS = 1_000L
    }

    data class Run(val steps: Int, val selecting: Boolean)

    private val runs = ArrayList<Run>()
    private var session: Long? = null
    private var lastAddAt = 0L

    fun add(steps: Int, selecting: Boolean, session: Long) {
        if (steps == 0) return
        val now = clock()
        // 换了输入框，或这些格子早就该做却一直没轮到：作废，不能挪到下一次拖动里。
        if (this.session != session || now - lastAddAt > STALE_MS) { runs.clear(); this.session = session }
        lastAddAt = now
        val last = runs.lastOrNull()
        if (last != null && last.selecting == selecting && last.steps.sign == steps.sign) {
            runs[runs.lastIndex] = Run(last.steps + steps, selecting)
        } else runs += Run(steps, selecting)
    }

    /** 取走全部攒下的格子；不是 [session] 这个输入框的丢掉。 */
    fun drain(session: Long): List<Run> {
        val taken = if (this.session == session) runs.toList() else emptyList()
        runs.clear()
        return taken
    }

    /** 组合中改的是编码里的光标：取走全部格子，合成一个净步数；没有可做的返回 null。 */
    fun drainNet(session: Long): Int? = drain(session).sumOf { it.steps }.takeIf { it != 0 }
}
