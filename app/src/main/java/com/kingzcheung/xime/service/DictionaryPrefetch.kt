package com.kingzcheung.xime.service

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 词库和词组模型的自适应预读。
 *
 * 这些文件按页映射读取，放在系统页缓存里：内存够时系统留着，不够时回收，回收后的页要在打字时从存储读回来
 * （平板九键慢键 50~160 ms 都伴随上百次读盘）。这里不把文件读进程序自己的内存常驻（多占两三百 MB，
 * 输入法更容易被系统杀掉，冷启动更慢），只判断什么时候该把被回收的页补回来、补多少。
 *
 * 不定时轮询，只在事件上判断：
 * - 键盘弹出：抽查各文件在内存的比例（每 64 页查一页），不足九成就按优先级补读缺的部分；
 * - 键盘收起：本次打字期间进程读盘次数多，说明页被回收过，下次弹出不等间隔直接补；
 * - 系统内存紧张（onTrimMemory）：停止补读一段时间，并拉长间隔，让系统回收。
 * 间隔自适应：补读后很快又被回收（内存紧的机器）就加倍，最长 30 分钟；连续几次都留得住就回到 1 分钟。
 * 补读前留出内存余量，余量不够时只补优先级高的（音节表、主词库），词组模型最后补。
 * 省电模式、严重发热、低内存设备不做。
 */
internal class PrefetchPolicy(
    val minIntervalMs: Long = 60_000L,
    val maxIntervalMs: Long = 30 * 60_000L,
) {
    data class Conditions(
        val powerSave: Boolean = false,
        val lowRamDevice: Boolean = false,
        val lowMemory: Boolean = false,
        val thermalSevere: Boolean = false,
    )

    /** One file's state at a check: [resident] is the sampled fraction in memory, 0..1. */
    data class FileState(val path: String, val bytes: Long, val resident: Float)

    var intervalMs = minIntervalMs
        private set
    private var lastCheckAt: Long? = null
    private var lastPrefetchAt: Long? = null
    private var pressureAt: Long? = null
    private var stableChecks = 0
    private var faultsSeen = false
    private var thrashCounted = false

    fun blocked(now: Long, c: Conditions): Boolean =
        c.powerSave || c.lowRamDevice || c.lowMemory || c.thermalSevere ||
            pressureAt?.let { now - it < PRESSURE_HOLD_MS } == true

    /** Keyboard shown: look at residency now? (The look itself costs about a millisecond.) */
    fun shouldCheck(now: Long, c: Conditions): Boolean =
        !blocked(now, c) && (faultsSeen || lastCheckAt?.let { now - it >= MIN_CHECK_GAP_MS } != false)

    /**
     * Residency was read; returns the files to prefetch, most valuable first, within the memory
     * [headroomBytes] above [reserveBytes].
     */
    fun plan(now: Long, files: List<FileState>, headroomBytes: Long, reserveBytes: Long): List<String> {
        lastCheckAt = now
        val total = files.sumOf { it.bytes }.coerceAtLeast(1)
        val resident = files.sumOf { (it.bytes * it.resident).toLong() }.toFloat() / total
        val sinceLast = lastPrefetchAt?.let { now - it }
        if (resident >= WARM) {
            if (++stableChecks >= STABLE_CHECKS_TO_RESET) intervalMs = minIntervalMs
            faultsSeen = false
            return emptyList()
        }
        stableChecks = 0
        // Filled not long ago and already mostly gone again: this device is short of memory.
        if (sinceLast != null && sinceLast < THRASH_WINDOW_MS && resident < THRASHED && !thrashCounted) {
            thrashCounted = true
            widen()
        }
        if (!faultsSeen && sinceLast != null && sinceLast < intervalMs) return emptyList()
        var room = headroomBytes - reserveBytes
        val chosen = mutableListOf<String>()
        for (file in files) {
            val missing = (file.bytes * (1f - file.resident)).toLong()
            if (missing <= 0 || file.resident >= WARM) continue
            if (missing > room) break
            room -= missing
            chosen += file.path
        }
        if (chosen.isNotEmpty()) {
            lastPrefetchAt = now
            faultsSeen = false
            thrashCounted = false
        }
        return chosen
    }

    /** Keyboard hidden: [majorFaults] page reads during this typing session. */
    fun onSessionEnd(majorFaults: Long) {
        if (majorFaults >= SESSION_FAULTS) faultsSeen = true
    }

    /** The system asked to free memory. */
    fun onMemoryPressure(now: Long) {
        pressureAt = now
        widen()
    }

    private fun widen() {
        intervalMs = (intervalMs * 2).coerceAtMost(maxIntervalMs)
        stableChecks = 0
    }

    companion object {
        const val WARM = 0.9f
        const val THRASHED = 0.5f
        const val THRASH_WINDOW_MS = 5 * 60_000L
        const val PRESSURE_HOLD_MS = 5 * 60_000L
        const val MIN_CHECK_GAP_MS = 10_000L
        const val STABLE_CHECKS_TO_RESET = 3
        const val SESSION_FAULTS = 50L
    }
}

internal object DictionaryPrefetch {
    private const val TAG = "DictionaryPrefetch"
    private val policy = PrefetchPolicy()
    @Volatile private var faultsAtShow = -1L

    fun onShow(context: Context, scope: CoroutineScope, engine: RimeEngine) {
        val now = SystemClock.elapsedRealtime()
        faultsAtShow = majorFaults()
        val activity = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { info -> runCatching { activity?.getMemoryInfo(info) } }
        val power = context.getSystemService(PowerManager::class.java)
        val conditions = PrefetchPolicy.Conditions(
            powerSave = power?.isPowerSaveMode == true,
            lowRamDevice = activity?.isLowRamDevice == true,
            lowMemory = memory.lowMemory,
            thermalSevere = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE,
        )
        if (!synchronized(policy) { policy.shouldCheck(now, conditions) }) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val schema = engine.getCurrentSchema()
                val files = engine.prefetchCandidates(schema).mapNotNull { path ->
                    val fraction = engine.residentFraction(path)
                    if (fraction < 0) null else PrefetchPolicy.FileState(path, File(path).length(), fraction)
                }
                if (files.isEmpty()) return@runCatching
                // Leave the system its low-memory threshold plus an eighth of RAM after reading.
                val reserve = maxOf(memory.threshold, memory.totalMem / 8)
                val chosen = synchronized(policy) { policy.plan(now, files, memory.availMem, reserve) }
                val requested = chosen.sumOf { engine.prefetchFile(it).coerceAtLeast(0) }
                if (requested > 0) FileLogger.i(TAG, "schema=$schema requested=${requested}KB files=${chosen.size} " +
                    "interval=${policy.intervalMs / 1000}s")
            }.onFailure { FileLogger.w(TAG, "prefetch skipped: ${it.message}") }
        }
    }

    fun onHide() {
        val start = faultsAtShow
        val end = majorFaults()
        if (start >= 0 && end >= start) synchronized(policy) { policy.onSessionEnd(end - start) }
        faultsAtShow = -1
    }

    fun onTrimMemory(level: Int) {
        val pressure = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
        if (pressure) synchronized(policy) { policy.onMemoryPressure(SystemClock.elapsedRealtime()) }
    }

    /** Major page faults of this process so far (field 12 of /proc/self/stat); -1 if unreadable. */
    private fun majorFaults(): Long = runCatching {
        val stat = File("/proc/self/stat").readText()
        stat.substring(stat.lastIndexOf(')') + 2).split(' ')[9].toLong()
    }.getOrDefault(-1L)
}
