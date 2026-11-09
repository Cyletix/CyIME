package com.kingzcheung.xime.runtime.adaptation

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.platform.AndroidPlatform

/**
 * 什么时候跑 [CpuBenchmark]、结果存哪。
 * - 只在「性能与省电」页、「按设备推荐配置」页打开时跑（设置界面在前台，系统让它用大核），约 0.3～2 秒，在后台线程。
 * - 键盘显示时不跑；跑到一半键盘弹出来就停，结果不要。按键回调里从不调用。
 * - 省电模式（频率被压低）、设备发热（API 29 起能读）时不跑，免得把好机器测成差机器。
 *   发热分两种门槛（#268，荣耀 Play7T 凉着也一直报「中度」发热，点「重测」永远测不了也没提示）：
 *   打开页面自动测时「中度」起就不测；用户点「重测」时到「严重」（系统真在降频）才不测。
 * - 结果存在单独的 device_benchmark 里（不进备份，不跟着设置换到别的设备），
 *   算法版本或系统版本（Build.FINGERPRINT）变了就作废，下次打开那两页时重测。
 */
object CpuBenchmarkRunner {
    private const val PREFS = "device_benchmark"
    private const val KEY_VERSION = "cpu_version"
    private const val KEY_FINGERPRINT = "cpu_fingerprint"
    private const val KEY_SINGLE = "cpu_single_ns"
    private const val KEY_DUAL = "cpu_dual_ns"
    private const val KEY_CPUS = "cpu_cpus"
    private const val TAG = "CpuBenchmark"

    /** 输入法窗口在不在屏幕上，由 XimeInputMethodService 的 onWindowShown/onWindowHidden 写。 */
    @Volatile var keyboardShown: Boolean = false

    enum class Skip { KEYBOARD, POWER_SAVE, THERMAL, UNSUPPORTED }

    sealed interface Outcome {
        data class Measured(val measurement: CpuMeasurement) : Outcome
        data class Skipped(val reason: Skip) : Outcome
    }

    private val lock = Any()

    /** 存着的结果；没测过、算法或系统换了都返回 null。只读偏好，键盘里读也不费事。 */
    fun cached(context: Context): CpuMeasurement? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_VERSION, -1) != CpuBenchmark.VERSION) return@runCatching null
        if (prefs.getString(KEY_FINGERPRINT, null) != Build.FINGERPRINT) return@runCatching null
        val single = prefs.getLong(KEY_SINGLE, -1)
        val dual = prefs.getLong(KEY_DUAL, -1)
        if (single <= 0 || dual <= 0) null else CpuMeasurement(single, dual)
    }.getOrNull()

    /** 测过的核（如 "4-7"），给开发者工具看；没有返回 null。 */
    fun cachedCpus(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CPUS, null)

    /**
     * 发热到哪一级就不测：自动测从「中度」起；[manual]（点「重测」）到「严重」才拦。有的机型凉着也一直报「中度」，
     * 只按中度拦的话这些机器永远测不了（#268）。
     */
    fun thermalBlocks(status: Int, manual: Boolean): Boolean =
        status >= if (manual) PowerManager.THERMAL_STATUS_SEVERE else PowerManager.THERMAL_STATUS_MODERATE

    /** 现在不能测的原因；能测返回 null。[manual] = 用户点了「重测」，见 [thermalBlocks]。 */
    fun blocker(context: Context, manual: Boolean = false): Skip? {
        if (keyboardShown) return Skip.KEYBOARD
        val power = runCatching { context.getSystemService(PowerManager::class.java) }.getOrNull()
        if (runCatching { power?.isPowerSaveMode }.getOrNull() == true) return Skip.POWER_SAVE
        if (AndroidPlatform.thermalStatus && power != null &&
            thermalBlocks(runCatching { power.currentThermalStatus }.getOrDefault(0), manual)) return Skip.THERMAL
        return null
    }

    /** 有存着的就用，没有才测。在后台线程调用。 */
    fun ensure(context: Context): Outcome = cached(context)?.let { Outcome.Measured(it) } ?: run(context)

    /** 不管有没有存着都重测（页面上点「重测」时 [manual]）。在后台线程调用；同一时间只测一份。 */
    fun run(context: Context, manual: Boolean = false): Outcome = synchronized(lock) {
        blocker(context, manual)?.let { return Outcome.Skipped(it) }
        if (!CpuBenchmark.load()) return Outcome.Skipped(Skip.UNSUPPORTED)
        val measured = try {
            CpuBenchmark.measure(shouldStop = { keyboardShown })
        } catch (error: RuntimeException) {
            FileLogger.e(TAG, "CPU benchmark failed", error)
            null
        }
        val result = measured ?: return Outcome.Skipped(if (keyboardShown) Skip.KEYBOARD else Skip.UNSUPPORTED)
        // 测的过程中开了省电或热起来，结果偏低，不存。
        blocker(context, manual)?.let { return Outcome.Skipped(it) }
        val measurement = result.measurement
        save(context, measurement, CpuBenchmark.cpuList(result.singleCpus or result.dualCpus))
        FileLogger.i(TAG, "single=${result.single} dual=${result.dual} " +
            "cpus=${CpuBenchmark.cpuList(result.singleCpus)}/${CpuBenchmark.cpuList(result.dualCpus)}")
        Outcome.Measured(measurement)
    }

    /** 存一次结果（测完时；渲染测试也用它放一组假数）。 */
    internal fun save(context: Context, measurement: CpuMeasurement, cpus: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_VERSION, measurement.version)
            .putString(KEY_FINGERPRINT, Build.FINGERPRINT)
            .putLong(KEY_SINGLE, measurement.singleNs)
            .putLong(KEY_DUAL, measurement.dualNs)
            .putString(KEY_CPUS, cpus)
            .apply()
    }
}
