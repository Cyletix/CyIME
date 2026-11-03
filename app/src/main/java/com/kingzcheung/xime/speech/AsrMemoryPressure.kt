package com.kingzcheung.xime.speech

/**
 * 键盘收起时判断系统是否缺内存，缺就把空闲的语音模型卸掉（常驻设置也一样）。
 *
 * Android 14 起 AOSP 不再给应用发 TRIM_MEMORY_RUNNING_LOW / RUNNING_CRITICAL / MODERATE，只剩 UI_HIDDEN 和
 * BACKGROUND；:asr 被输入法绑着，进不了后台列表，照 AOSP 的系统上原来“缺内存时提前释放”的阶梯从不触发，
 * 常驻的两遍语音模型（平板实测 605 MB）就一直占着。三星平板 (SM-X800) 11-03 实测仍会发 RUNNING_CRITICAL，
 * 这条是给不发的系统兜底，两条都触发也只卸一次。
 */
internal object AsrMemoryPressure {
    /**
     * 缺内存：系统已标记 lowMemory，或可用内存低于系统低内存线的 2 倍，或低于总内存的 1/8。
     * 参数取自 ActivityManager.MemoryInfo；拿不到总内存（0）时只看前两条。
     */
    fun isTight(availBytes: Long, thresholdBytes: Long, totalBytes: Long, lowMemory: Boolean): Boolean {
        if (lowMemory) return true
        if (thresholdBytes > 0 && availBytes < thresholdBytes * 2) return true
        return totalBytes > 0 && availBytes < totalBytes / 8
    }
}
