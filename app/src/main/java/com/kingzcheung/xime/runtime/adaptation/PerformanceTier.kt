package com.kingzcheung.xime.runtime.adaptation

/** 低配 / 中配 / 高配。只看本机硬件事实，不跑基准、不看机型名。 */
enum class PerformanceTier { LOW, MEDIUM, HIGH }

/**
 * 唯一的档位判断。首次安装的按键效果默认值和性能预设页都用它，不再各自复制阈值。
 * 高配要求 8 GB：两遍语音常驻就要约 650 MB，6 GB 机器上再常驻容易被系统回收。
 */
object PerformanceTiers {
    const val HIGH_MEMORY_BYTES = 8 * DeviceCapabilities.GIB
    const val LOW_MEMORY_BYTES = 4 * DeviceCapabilities.GIB
    const val MEDIUM_CPU_KHZ = 1_800_000L
    const val HIGH_CPU_KHZ = 2_200_000L

    fun classify(device: DeviceCapabilities): PerformanceTier {
        val memory = device.totalMemoryBytes?.takeIf { it > 0 }
        val cores = device.processors?.takeIf { it > 0 }
        val frequency = device.maxCpuKHz?.takeIf { it > 0 }
        return when {
            device.lowRam != false || memory == null || cores == null -> PerformanceTier.LOW
            memory < LOW_MEMORY_BYTES || cores < 4 || frequency != null && frequency < MEDIUM_CPU_KHZ -> PerformanceTier.LOW
            memory >= HIGH_MEMORY_BYTES && (frequency == null || frequency >= HIGH_CPU_KHZ) -> PerformanceTier.HIGH
            else -> PerformanceTier.MEDIUM
        }
    }
}
