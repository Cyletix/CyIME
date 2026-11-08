package com.kingzcheung.xime.runtime.adaptation

/** 低配 / 中配 / 高配。看本机 CPU 实测和内存，不看机型名。 */
enum class PerformanceTier { LOW, MEDIUM, HIGH }

/**
 * 唯一的档位判断。首次安装的按键效果默认值、性能预设页的「自动」、推荐配置页都用它，不再各自复制阈值。
 *
 * 11-04 改：原来按内存、核数、最高主频定高配，很差的手机也因为 8 GB、2.2 GHz 被评成高配。现在：
 * - 算力只看实测（[CpuBenchmark]）：主频、核数只显示，不再让一台机器升档。没测过最多中配。
 * - 内存只封顶：不到 4 GB 低配，4～8 GB 最高中配，8 GB 起才可能高配（两遍语音常驻约 650 MB，6 GB 机器常驻容易被系统回收）。
 * - 最终档 = 算力档和内存档取低的那个，内存大不能补 CPU 慢。
 * 档位只决定「自动」时的默认值，用户自己选的档、改过的项不动（[com.kingzcheung.xime.settings.PerformanceProfile]）。
 */
object PerformanceTiers {
    const val HIGH_MEMORY_BYTES = 8 * DeviceCapabilities.GIB
    const val LOW_MEMORY_BYTES = 4 * DeviceCapabilities.GIB
    /** 没测过时，低于这个主频直接算低配：主频低说明慢，主频高说明不了快。 */
    const val MEDIUM_CPU_KHZ = 1_800_000L

    /**
     * 实测分档线：跑完一遍要多少纳秒，越快越强。单线程、双线程各自定档，取低的那个。
     * 低配线来自真机核簇实测；高配线留待跨设备复核。数据见 device-adaptation-plan.md。
     */
    data class CpuThresholds(val highSingleNs: Long?, val highDualNs: Long?, val lowSingleNs: Long, val lowDualNs: Long) {
        init {
            require(lowSingleNs > 0 && lowDualNs > 0)
            require((highSingleNs == null) == (highDualNs == null))
            require(highSingleNs == null || highSingleNs in 1 until lowSingleNs)
            require(highDualNs == null || highDualNs in 1 until lowDualNs)
        }
    }

    val cpuThresholds = CpuThresholds(highSingleNs = null, highDualNs = null,
        lowSingleNs = 80_000_000, lowDualNs = 85_000_000)

    /** 档位卡在哪：给页面写一句为什么是这一档。 */
    enum class Limit {
        /** 实测算力决定。 */
        CPU,
        /** 算力够更高一档，被内存压住了。 */
        MEMORY,
        /** 还没测（或分档线还没定），按内存和主频保守估计，最多中配。 */
        UNMEASURED,
        /** 内存、核数读不到或系统标了低内存设备。 */
        UNKNOWN,
    }

    data class Verdict(val tier: PerformanceTier, val limit: Limit, val cpuTier: PerformanceTier?)

    /** 只看实测的算力档；没测、版本对不上或分档线没定返回 null。 */
    fun cpuTier(cpu: CpuMeasurement?, thresholds: CpuThresholds? = cpuThresholds): PerformanceTier? {
        if (cpu == null || thresholds == null || cpu.version != CpuBenchmark.VERSION ||
            cpu.singleNs <= 0 || cpu.dualNs <= 0) return null
        fun tier(ns: Long, high: Long?, low: Long) = when {
            high != null && ns <= high -> PerformanceTier.HIGH
            ns >= low -> PerformanceTier.LOW
            else -> PerformanceTier.MEDIUM
        }
        return minOf(tier(cpu.singleNs, thresholds.highSingleNs, thresholds.lowSingleNs),
            tier(cpu.dualNs, thresholds.highDualNs, thresholds.lowDualNs))
    }

    fun classify(device: DeviceCapabilities, thresholds: CpuThresholds? = cpuThresholds): PerformanceTier =
        explain(device, thresholds).tier

    fun explain(device: DeviceCapabilities, thresholds: CpuThresholds? = cpuThresholds): Verdict {
        val memory = device.totalMemoryBytes?.takeIf { it > 0 }
        val cores = device.processors?.takeIf { it > 0 }
        val cpu = cpuTier(device.cpu, thresholds)
        if (device.lowRam != false || memory == null || cores == null) return Verdict(PerformanceTier.LOW, Limit.UNKNOWN, cpu)
        if (memory < LOW_MEMORY_BYTES) return Verdict(PerformanceTier.LOW, Limit.MEMORY, cpu)
        if (cores < 4) return Verdict(PerformanceTier.LOW, if (cpu == null) Limit.UNMEASURED else Limit.CPU, cpu)
        val memoryCap = if (memory >= HIGH_MEMORY_BYTES) PerformanceTier.HIGH else PerformanceTier.MEDIUM
        if (cpu == null) {
            val frequency = device.maxCpuKHz?.takeIf { it > 0 }
            val guess = if (frequency != null && frequency < MEDIUM_CPU_KHZ) PerformanceTier.LOW else PerformanceTier.MEDIUM
            return Verdict(guess, Limit.UNMEASURED, null)
        }
        return if (memoryCap < cpu) Verdict(memoryCap, Limit.MEMORY, cpu) else Verdict(cpu, Limit.CPU, cpu)
    }
}
