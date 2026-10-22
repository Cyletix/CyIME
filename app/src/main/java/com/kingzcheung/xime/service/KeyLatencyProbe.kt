package com.kingzcheung.xime.service

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.Window

/**
 * 触摸到候选画完一帧的分段计时，默认关闭。
 * 打开：`adb shell setprop log.tag.CyIMELatency DEBUG`，再重新点进输入框；关闭：把该属性设回空或 INFO。
 * 同一时刻只跟一次按键；上一次还没画完就来了新按键，上一次作废并计入 dropped。
 * 每次只记阶段耗时、布局、编码长度，不记按键、候选或输入框内容。
 * 终点是本帧交给显示合成（HWUI 帧完成），之后合成上屏通常再加 1～2 个刷新周期，这里不含。
 */
internal object KeyLatencyProbe {
    const val TAG = "CyIMELatency"
    @Volatile var enabled = false
        private set

    private const val IDLE = 0
    private const val FIRED = 1
    private const val STARTED = 2
    private const val POSTED = 3
    private const val HANDED = 4
    private const val APPLIED = 5
    private const val COMPOSED = 6
    private const val DRAWN = 7

    private val lock = Any()
    private var phase = IDLE
    private var layout = ""
    private var length = 0
    private var engineNanos = 0L
    private var dropped = 0
    // fired, started, posted, handed, applied, composed, drawn, vsync
    private val t = LongArray(8)
    private var touchUp = false
    private var touchEvent = 0L
    private var touchReceived = 0L
    private var touchDown = 0L
    private var probeTouchUp = false
    private var probeTouchEvent = 0L
    private var probeTouchReceived = 0L
    private var probeTouchDown = 0L

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
        synchronized(lock) { phase = IDLE }
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
            if (phase != IDLE) dropped++
            phase = FIRED
            layout = layoutId
            length = 0
            engineNanos = 0
            t.fill(0)
            t[0] = now
            probeTouchUp = touchUp
            probeTouchEvent = touchEvent
            probeTouchReceived = touchReceived
            probeTouchDown = touchDown
        }
    }

    private inline fun step(from: Int, to: Int, index: Int, also: () -> Unit = {}) {
        if (!enabled) return
        val now = System.nanoTime()
        synchronized(lock) {
            if (phase != from && !(from == STARTED && phase == POSTED && to == POSTED)) return
            phase = to
            t[index] = now
            also()
        }
    }

    /** 队列开始执行这次按键。 */
    fun started() = step(FIRED, STARTED, 1)

    /** 后台把结果投给主线程；同一按键多次投递取最后一次。 */
    fun posted(compositionLength: Int) {
        val engine = com.kingzcheung.xime.rime.EngineTimeMeter.current.get()?.get(0) ?: 0L
        step(STARTED, POSTED, 2) { length = compositionLength; engineNanos = engine }
    }

    /** 主线程开始应用结果。 */
    fun handed() = step(POSTED, HANDED, 3)

    /** 主线程写完候选状态。 */
    fun applied() = step(HANDED, APPLIED, 4)

    /** 键盘根组合应用了这次变化。 */
    fun composed() = step(APPLIED, COMPOSED, 5)

    private fun drawn() {
        val vsync = runCatching { Choreographer.getInstance().frameTimeNanos }.getOrDefault(0L)
        step(COMPOSED, DRAWN, 6) { t[7] = vsync }
    }

    private fun frame(metrics: FrameMetrics) {
        val vsync = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)
        val line: String
        synchronized(lock) {
            if (phase != DRAWN) return
            if (t[7] != 0L && vsync < t[7]) return
            if (t[7] != 0L && vsync > t[7]) { dropped++; phase = IDLE; return }
            phase = IDLE
            val done = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) + metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val gpu = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else -1_000L
            fun us(nanos: Long) = nanos / 1_000
            val frameStart = maxOf(t[7], t[4])
            line = "layout=$layout len=$length trigger=${if (probeTouchUp) "up" else "down"} " +
                "hold=${us(probeTouchEvent - probeTouchDown)} dispatch=${us(probeTouchReceived - probeTouchEvent)} " +
                "main=${us(t[0] - probeTouchReceived)} queue=${us(t[1] - t[0])} bg=${us(t[2] - t[1])} " +
                "engine=${us(engineNanos)} handoff=${us(t[3] - t[2])} apply=${us(t[4] - t[3])} " +
                "vsyncwait=${us(frameStart - t[4])} recompose=${us(t[5] - frameStart)} " +
                "layoutdraw=${us(t[6] - t[5])} render=${us(done - t[6])} gpu=${us(gpu)} " +
                "total=${us(done - probeTouchEvent)} dropped=$dropped"
        }
        Log.d(TAG, line)
    }
}
