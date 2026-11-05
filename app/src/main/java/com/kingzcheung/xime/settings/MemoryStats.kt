package com.kingzcheung.xime.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import java.io.File

/**
 * 运行内存（设置 → 关于 → 存储空间 顶部，用户 11-04「更多对内存的控制和指示」）：
 * CyIME 自己的几个进程各占多少、本机还剩多少，以及安装包本身多大。
 * 只读自己的进程（同一个 uid），不碰别的应用。
 */
object MemoryStats {

    /**
     * 一个进程占的内存，按 PSS（共享的部分按份摊、不含换到 zram 的），与 dumpsys meminfo 的 TOTAL PSS 同一算法。
     * 正式包的其他进程 /proc 读不到（不可 dump，属主是 root，11-04 平板实测）：语音进程由它自己经 AIDL 报
     * （见 [selfPssKb]），其余用系统接口的值，可能是几分钟前的。
     */
    data class ProcessUse(val processName: String, val title: String, val pssBytes: Long, val isVoice: Boolean)

    data class Snapshot(
        val processes: List<ProcessUse>,
        val availBytes: Long?,
        val totalBytes: Long?,
        /** 系统现在算不算内存不足（低于它的回收线）。 */
        val lowMemory: Boolean,
        /** 安装包本身（含拆分包）；系统管理，删不掉。 */
        val apkBytes: Long?,
    ) {
        val totalPss: Long get() = processes.sumOf { it.pssBytes }
        val voice: ProcessUse? get() = processes.firstOrNull { it.isVoice }
    }

    const val VOICE_SUFFIX = ":asr"

    /** 进程名 → 给人看的叫法；排序也按这个顺序（键盘在前）。 */
    fun title(packageName: String, processName: String): String = when (processName.removePrefix(packageName)) {
        "" -> "键盘与设置"
        VOICE_SUFFIX -> "语音与字幕"
        ":inference" -> "AI 模型"
        ":ocr" -> "文字识别"
        else -> processName.substringAfterLast(':')
    }

    private fun order(packageName: String, processName: String): Int =
        when (processName.removePrefix(packageName)) { "" -> 0; VOICE_SUFFIX -> 1; ":inference" -> 2; ":ocr" -> 3; else -> 4 }

    /** `/proc/<pid>/smaps_rollup` 里的 `Pss:` 一行（kB）；内核没有这个文件时返回 null。 */
    fun parsePssKb(smapsRollup: String): Long? = field(smapsRollup, "Pss:")

    private fun field(text: String, name: String): Long? = text.lineSequence()
        .firstOrNull { it.startsWith(name) }
        ?.removePrefix(name)?.trim()?.substringBefore(' ')?.toLongOrNull()

    /** 本进程的 PSS（kB）：先读 smaps_rollup，旧内核没有这个文件时用 Debug.getPss()（读 smaps，慢一些）。在 IO 线程调用。 */
    fun selfPssKb(): Long? = readProc("self", "smaps_rollup", ::parsePssKb)
        ?: runCatching { android.os.Debug.getPss() }.getOrNull()?.takeIf { it > 0 }

    /** 系统接口给的驻留 PSS（kB）：减掉换到 zram 的部分，和 smaps_rollup 的 Pss 对得上。 */
    fun residentPssKb(totalPssKb: Int, swappedOutPssKb: Int): Long? =
        (totalPssKb.toLong() - swappedOutPssKb.coerceAtLeast(0)).takeIf { it > 0 }

    /** 用 [voicePssKb]（语音进程自己报的）换掉语音那一行；没报上来就保持原样。 */
    fun Snapshot.withVoice(voicePssKb: Long?): Snapshot = if (voicePssKb == null || voicePssKb <= 0) this
        else copy(processes = processes.map { if (it.isVoice) it.copy(pssBytes = voicePssKb * 1024) else it })

    /** IO 线程调用：会读 /proc。 */
    fun read(context: Context): Snapshot {
        val manager = context.getSystemService(ActivityManager::class.java)
        val pkg = context.packageName
        val myUid = Process.myUid()
        val own = runCatching { manager?.runningAppProcesses.orEmpty() }.getOrDefault(emptyList())
            .filter { it.uid == myUid && it.processName.startsWith(pkg) }
        val myPid = Process.myPid()
        // 进程在就列出来（语音那行的数字由 ViewModel 再问语音进程本身）。别的进程的 /proc 正式包读不到，
        // 系统接口 Android 10 起短时间内重复问只给旧值，并且算上了换到 zram 的部分，所以减掉。
        val processes = own.map { info ->
            val kb = if (info.pid == myPid) selfPssKb()
                else readProc(info.pid.toString(), "smaps_rollup", ::parsePssKb) ?: runCatching {
                    manager?.getProcessMemoryInfo(intArrayOf(info.pid))?.firstOrNull()
                        ?.let { residentPssKb(it.totalPss, it.getMemoryStat("summary.total-swap")?.toIntOrNull() ?: 0) }
                }.getOrNull()
            ProcessUse(info.processName, title(pkg, info.processName), (kb ?: 0L) * 1024, info.processName == pkg + VOICE_SUFFIX)
        }.sortedBy { order(pkg, it.processName) }
        val memory = runCatching { ActivityManager.MemoryInfo().also { manager!!.getMemoryInfo(it) } }.getOrNull()
        val apk = runCatching {
            val app = context.applicationInfo
            (listOf(app.sourceDir) + app.splitSourceDirs.orEmpty()).sumOf { File(it).length() }
        }.getOrNull()?.takeIf { it > 0 }
        return Snapshot(processes, memory?.availMem?.takeIf { it > 0 }, memory?.totalMem?.takeIf { it > 0 },
            memory?.lowMemory == true, apk)
    }

    private fun readProc(pid: String, name: String, parse: (String) -> Long?): Long? =
        runCatching { parse(File("/proc/$pid/$name").readText()) }.getOrNull()?.takeIf { it > 0 }
}
