package com.kingzcheung.xime.service

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.Window

/**
 * 触摸到候选画完一帧的分段计时，默认关闭。
 * 打开：`adb shell setprop log.tag.CyIMELatency DEBUG`，再重新点进输入框；关闭：把该属性设回空或 INFO。
 * 快速连按时多个按键同时在途：一次主线程应用的结果覆盖它之前已投递的按键，这些按键都以同一帧为终点。
 * 每次只记阶段耗时、布局、编码长度，不记按键、候选或输入框内容。
 * 终点是本帧交给显示合成（HWUI 帧完成），之后合成上屏通常再加 1～2 个刷新周期，这里不含。
 */
internal object KeyLatencyProbe {
    const val TAG = "CyIMELatency"
    @Volatile var enabled = false
        private set

    private const val FIRED = 1
    private const val STARTED = 2
    private const val POSTED = 3
    private const val HANDED = 4
    private const val APPLIED = 5
    private const val COMPOSED = 6
    private const val DRAWN = 7

    private class Probe(val layout: String, val inflight: Int, val touchUp: Boolean, val touchEvent: Long,
        val touchReceived: Long, val touchDown: Long) {
        var phase = FIRED
        var length = 0
        var engineNanos = 0L
        // 后台阶段在输入线程上的 CPU 时间和缺页：墙钟远大于 CPU、且有主缺页，说明在等内存换页而不是在算。
        var cpuStart = 0L
        var faultStart = LongArray(2)
        var cpuNanos = -1L
        var minorFaults = -1L
        var majorFaults = -1L
        // fired, started, posted, handed, applied, composed, drawn, frame vsync
        val t = LongArray(8)
    }

    private const val MAX_INFLIGHT = 64
    private val lock = Any()
    private val probes = ArrayDeque<Probe>()
    private var dropped = 0
    private var touchUp = false
    private var touchEvent = 0L
    private var touchReceived = 0L
    private var touchDown = 0L

    private var window: Window? = null
    private var handlerThread: HandlerThread? = null
    private val drawListener = ViewTreeObserver.OnDrawListener { drawn() }
    private val frameListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> frame(metrics) }

    /** 进入输入框时读开关；只在主线程、不在绘制中调用。 */
    fun refresh(target: Window?) {
        val on = Log.isLoggable(TAG, Log.DEBUG)
        if (on == enabled && target === window) return
        detach()
        enabled = on
        if (!on || target == null) return
        val thread = handlerThread ?: HandlerThread("latency-probe").also { it.start(); handlerThread = it }
        runCatching {
            target.decorView.viewTreeObserver.addOnDrawListener(drawListener)
            target.addOnFrameMetricsAvailableListener(frameListener, Handler(thread.looper))
            window = target
        }.onFailure { enabled = false; Log.w(TAG, "probe unavailable", it) }
        Log.d(TAG, "probe on sdk=${Build.VERSION.SDK_INT}")
    }

    private fun detach() {
        val old = window ?: return
        window = null
        runCatching { old.decorView.viewTreeObserver.removeOnDrawListener(drawListener) }
        runCatching { old.removeOnFrameMetricsAvailableListener(frameListener) }
        synchronized(lock) { probes.clear() }
    }

    /** 键盘容器收到触摸，记最后一次按下/松开的事件时间和到达时间。 */
    fun touch(event: MotionEvent) {
        if (!enabled) return
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP) return
        val received = System.nanoTime()
        val eventNanos = if (Build.VERSION.SDK_INT >= 34) event.eventTimeNanos else event.eventTime * 1_000_000
        synchronized(lock) {
            touchUp = action == MotionEvent.ACTION_UP
            touchEvent = eventNanos
            touchReceived = received
            touchDown = event.downTime * 1_000_000
        }
    }

    /** 主线程把这次按键交进输入队列。 */
    fun fired(layoutId: String) {
        if (!enabled) return
        val now = System.nanoTime()
        synchronized(lock) {
            if (probes.size >= MAX_INFLIGHT) { probes.removeFirst(); dropped++ }
            probes.addLast(Probe(layoutId, probes.size, touchUp, touchEvent, touchReceived, touchDown).also { it.t[0] = now })
        }
    }

    /** 最早一个处于 [from] 的按键进入 [to]。 */
    private inline fun advanceFirst(from: Int, to: Int, index: Int, also: (Probe) -> Unit = {}) {
        if (!enabled) return
        val now = System.nanoTime()
        synchronized(lock) {
            val probe = probes.firstOrNull { it.phase == from } ?: return
            probe.phase = to
            probe.t[index] = now
            also(probe)
        }
    }

    /** 所有处于 [from] 的按键一起进入 [to]：后一次状态已包含前面按键的结果。 */
    private fun advanceAll(from: Int, to: Int, index: Int) {
        if (!enabled) return
        val now = System.nanoTime()
        synchronized(lock) {
            for (probe in probes) if (probe.phase == from) { probe.phase = to; probe.t[index] = now }
        }
    }

    /** 队列开始执行这次按键。 */
    fun started() = advanceFirst(FIRED, STARTED, 1) {
        it.cpuStart = android.os.Debug.threadCpuTimeNanos()
        it.faultStart = threadFaults() ?: LongArray(2) { -1L }
    }

    /** 本线程的 [次缺页, 主缺页]（/proc/thread-self/stat 第 10、12 项）；读不到为 null。 */
    private fun threadFaults(): LongArray? = runCatching {
        val stat = java.io.File("/proc/thread-self/stat").readText()
        val fields = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
        longArrayOf(fields[7].toLong(), fields[9].toLong())
    }.getOrNull()

    /** 后台把结果投给主线程；同一按键多次投递取最后一次。 */
    fun posted(compositionLength: Int) {
        if (!enabled) return
        val engine = com.kingzcheung.xime.rime.EngineTimeMeter.current.get()?.get(0) ?: 0L
        val now = System.nanoTime()
        val cpu = android.os.Debug.threadCpuTimeNanos()
        val faults = threadFaults()
        synchronized(lock) {
            val probe = probes.lastOrNull { it.phase == STARTED || it.phase == POSTED } ?: return
            probe.phase = POSTED
            probe.t[2] = now
            probe.length = compositionLength
            probe.engineNanos = engine
            // 同一线程开始、结束才有意义；九键等走别的线程时记 -1。
            if (probe.cpuStart > 0 && cpu >= probe.cpuStart) probe.cpuNanos = cpu - probe.cpuStart
            if (faults != null && probe.faultStart[0] >= 0) {
                probe.minorFaults = faults[0] - probe.faultStart[0]
                probe.majorFaults = faults[1] - probe.faultStart[1]
            }
        }
    }

    /** 主线程开始应用结果。 */
    fun handed() = advanceAll(POSTED, HANDED, 3)

    /** 主线程写完候选状态。 */
    fun applied() = advanceAll(HANDED, APPLIED, 4)

    /** 键盘根组合应用了这次变化。 */
    fun composed() = advanceAll(APPLIED, COMPOSED, 5)

    private fun drawn() = advanceAll(COMPOSED, DRAWN, 6)

    private fun frame(metrics: FrameMetrics) {
        val vsync = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)
        val done = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) + metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val gpu = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else -1_000L
        val lines = mutableListOf<String>()
        synchronized(lock) {
            val iterator = probes.iterator()
            while (iterator.hasNext()) {
                val probe = iterator.next()
                if (probe.phase != DRAWN) continue
                val t = probe.t
                // 绘制时刻落在这一帧的 vsync 和完成之间才是这次按键的帧；更早的帧跳过，更晚说明漏了。
                if (done < t[6]) continue
                iterator.remove()
                if (vsync > t[6]) { dropped++; continue }
                t[7] = vsync
                fun us(nanos: Long) = nanos / 1_000
                val frameStart = maxOf(t[7], t[4])
                lines += "layout=${probe.layout} len=${probe.length} inflight=${probe.inflight} " +
                    "trigger=${if (probe.touchUp) "up" else "down"} at=${probe.touchEvent / 1_000_000} " +
                    "hold=${us(probe.touchEvent - probe.touchDown)} dispatch=${us(probe.touchReceived - probe.touchEvent)} " +
                    "main=${us(t[0] - probe.touchReceived)} queue=${us(t[1] - t[0])} bg=${us(t[2] - t[1])} " +
                    "engine=${us(probe.engineNanos)} bgcpu=${if (probe.cpuNanos < 0) -1 else us(probe.cpuNanos)} " +
                    "minflt=${probe.minorFaults} majflt=${probe.majorFaults} " +
                    "handoff=${us(t[3] - t[2])} apply=${us(t[4] - t[3])} " +
                    "vsyncwait=${us(frameStart - t[4])} recompose=${us(t[5] - frameStart)} " +
                    "layoutdraw=${us(t[6] - t[5])} render=${us(done - t[6])} gpu=${us(gpu)} " +
                    "total=${us(done - probe.touchEvent)} dropped=$dropped"
            }
        }
        lines.forEach { Log.d(TAG, it) }
    }
}
