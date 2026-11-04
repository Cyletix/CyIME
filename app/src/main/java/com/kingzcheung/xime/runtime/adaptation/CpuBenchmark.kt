package com.kingzcheung.xime.runtime.adaptation

/**
 * 一次 CPU 实测：同一段固定计算（app/src/main/jni/cpu_bench.cc）一份单独跑、两份同时跑，各自跑完一遍的中位耗时。
 * 越快越强。[version] 跟着计算量走，计算量一改旧结果就作废。
 */
data class CpuMeasurement(val singleNs: Long, val dualNs: Long, val version: Int = CpuBenchmark.VERSION)

/**
 * CPU 测速本身：不碰 Android 接口，单元测试和电脑上的校准脚本（app_process 直接跑 [main]）都用这一份。
 * 什么时候能跑、结果存哪见 [CpuBenchmarkRunner]：只在设置页、推荐配置页里、键盘没显示时跑，按键回调里从不跑。
 */
object CpuBenchmark {
    /** cpu_bench.cc 的计算量或算法一改就加一。 */
    const val VERSION = 1
    private const val LIBRARY = "cyime_cpubench"
    private const val WARMUP = 2
    private const val REPS = 5
    private const val MIN_REPS = 3
    /** 慢机器上测太久就少测几遍：每种至少 [MIN_REPS] 遍，过了这个时长不再加。 */
    private const val BUDGET_NS = 2_500_000_000L

    /** 一遍的结果：耗时纳秒和跑的时候待过的核（位图）。 */
    data class Pass(val ns: Long, val cpus: Long)

    data class Run(val single: List<Long>, val dual: List<Long>, val singleCpus: Long, val dualCpus: Long) {
        val measurement: CpuMeasurement get() = CpuMeasurement(median(single), median(dual))
    }

    @JvmStatic private external fun nativeRun(threads: Int, out: LongArray): Long

    @Volatile private var loaded: Boolean? = null

    /** 读不进原生库（模拟器缺 ABI 等）返回 false，档位按“未测”算。 */
    fun load(path: String? = null): Boolean = loaded ?: synchronized(this) {
        loaded?.let { return it }
        val ok = try {
            if (path != null) System.load(path) else System.loadLibrary(LIBRARY)
            true
        } catch (_: UnsatisfiedLinkError) { false } catch (_: SecurityException) { false }
        loaded = ok
        ok
    }

    private fun nativePass(threads: Int): Pass {
        val out = LongArray(1)
        val ns = nativeRun(threads, out)
        return Pass(ns, out[0])
    }

    /**
     * 先空跑 [WARMUP] 遍（让频率升上来），再一份、两份交替各跑 [REPS] 遍，取中位数。
     * [shouldStop] 每遍之间查一次（键盘弹出来就停），停了返回 null，不留半截结果。
     */
    fun measure(
        shouldStop: () -> Boolean = { false },
        pass: (threads: Int) -> Pass = ::nativePass,
        clock: () -> Long = System::nanoTime,
    ): Run? {
        val start = clock()
        repeat(WARMUP) {
            if (shouldStop()) return null
            if (pass(1).ns <= 0) return null
        }
        val single = mutableListOf<Long>()
        val dual = mutableListOf<Long>()
        var singleCpus = 0L
        var dualCpus = 0L
        while (single.size < REPS) {
            if (single.size >= MIN_REPS && clock() - start > BUDGET_NS) break
            if (shouldStop()) return null
            val one = pass(1)
            if (shouldStop()) return null
            val two = pass(2)
            if (one.ns <= 0 || two.ns <= 0) return null
            single += one.ns; singleCpus = singleCpus or one.cpus
            dual += two.ns; dualCpus = dualCpus or two.cpus
        }
        return Run(single, dual, singleCpus, dualCpus)
    }

    fun median(values: List<Long>): Long {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** 位图写成 "0-3,7" 这样，和 /sys/devices/system/cpu 的写法一样。 */
    fun cpuList(mask: Long): String {
        val cpus = (0 until 64).filter { mask and (1L shl it) != 0L }
        if (cpus.isEmpty()) return ""
        val ranges = mutableListOf<String>()
        var first = cpus[0]
        var last = first
        for (cpu in cpus.drop(1) + listOf(-2)) {
            if (cpu == last + 1) { last = cpu; continue }
            ranges += if (first == last) "$first" else "$first-$last"
            first = cpu; last = cpu
        }
        return ranges.joinToString(",")
    }

    /**
     * 给电脑上的校准脚本（tools/ime_lab/cpu_bench_calibrate.py）用：
     * `CLASSPATH=<base.apk> taskset <核> app_process /system/bin com.kingzcheung.xime.runtime.adaptation.CpuBenchmark <libcyime_cpubench.so 路径>`。
     * 和应用里跑的是同一个原生库、同一段计算；打印一行 JSON。
     */
    @JvmStatic
    fun main(args: Array<String>) {
        if (!load(args.firstOrNull())) {
            println("{\"error\":\"load\"}")
            return
        }
        val run = measure()
        if (run == null) {
            println("{\"error\":\"run\"}")
            return
        }
        val m = run.measurement
        println(buildString {
            append("{\"version\":").append(VERSION)
            append(",\"single_ns\":").append(m.singleNs)
            append(",\"dual_ns\":").append(m.dualNs)
            append(",\"single_all\":").append(run.single.joinToString(",", "[", "]"))
            append(",\"dual_all\":").append(run.dual.joinToString(",", "[", "]"))
            append(",\"single_cpus\":\"").append(cpuList(run.singleCpus)).append('"')
            append(",\"dual_cpus\":\"").append(cpuList(run.dualCpus)).append('"')
            append('}')
        })
    }
}
