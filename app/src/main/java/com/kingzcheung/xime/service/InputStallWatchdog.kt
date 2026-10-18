package com.kingzcheung.xime.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kingzcheung.xime.util.FileLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/** One outstanding heartbeat while an editor is active; records stacks, never input text. */
internal class InputStallWatchdog(
    private val scope: CoroutineScope,
    private val main: Handler,
    private val worker: () -> Thread?,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val answered = AtomicBoolean()
                val requested = SystemClock.uptimeMillis()
                if (!main.post { answered.set(true) }) break
                var reported = false
                while (isActive && !answered.get()) {
                    delay(250)
                    val elapsed = SystemClock.uptimeMillis() - requested
                    if (!answered.get() && !reported && elapsed >= 1000) {
                        reported = true
                        fun stack(thread: Thread?) = thread?.stackTrace?.take(24)?.joinToString("\n") ?: "unavailable"
                        FileLogger.w("InputLatency", "main_unresponsive_ms=$elapsed\nmain:\n${stack(Looper.getMainLooper().thread)}\ninput_worker:\n${stack(worker())}")
                    }
                }
                delay(250)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
