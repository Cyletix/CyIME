package com.kingzcheung.xime.util

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.concurrent.LinkedBlockingQueue

object FileLogger {
    private const val TAG = "FileLogger"
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024
    private const val MAX_LOG_FILES = 10
    /** 所有 APP 日志合计上限；常驻进程写满单个文件就轮转，不再只在启动时检查。 */
    private const val MAX_TOTAL_LOG_BYTES = 30L * 1024 * 1024
    private val ACTIVE_NAME = Regex("""kime_\d{8}(-[a-z0-9]+)?\.log""")
    private const val QUEUE_CAPACITY = 4096
    
    private var logFile: File? = null
    private var logsDir: File? = null
    private var isInitialized = false
    /** 各进程写自己的文件（:asr → kime_日期-asr.log），不再几个进程追加同一个文件。 */
    private var processSuffix = ""
    private var openedDay: LocalDate? = null

    /** 调试日志总开关：同时控制 v/d/i 级 logcat 和文件日志。
     * 默认关闭；仅 Debug 构建可启用，警告/错误始终保留。 */
    @Volatile
    private var verboseLoggingEnabled = false
    
    private val logQueue = LinkedBlockingQueue<String>(QUEUE_CAPACITY)
    private var writer: BufferedWriter? = null
    private var running = false
    private val writeLock = Any()
    
    // java.time 格式化器线程安全；SimpleDateFormat 在多线程写日志时会写出错乱时间。
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val fileDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val rotatedFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    /** 当天正在写的文件名（含其他进程的），清理日志时必须保留。 */
    fun isActiveLogName(name: String, today: LocalDate = LocalDate.now()): Boolean =
        ACTIVE_NAME.matches(name) && name.startsWith("kime_${fileDateFormat.format(today)}")
    
    fun init(context: Context) {
        if (isInitialized) return
        try {
            logsDir = File(context.filesDir, "logs")
            if (!logsDir!!.exists()) {
                logsDir!!.mkdirs()
            }
            
            verboseLoggingEnabled = SettingsPreferences.isVerboseLoggingEnabled(context)
            processSuffix = android.app.Application.getProcessName().substringAfter(':', "")
                .lowercase().filter { it.isLetterOrDigit() }.let { if (it.isEmpty()) "" else "-$it" }
            
            openLogFile()
            cleanOldLogs()
            cleanRimeLogs(context)
            cleanOrphanedLogs()
            
            running = true
            Thread({ flusherLoop() }, "log-flusher").also { it.isDaemon = true }.start()
            
            isInitialized = true
            i(TAG, "FileLogger initialized, log file: ${logFile!!.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FileLogger", e)
        }
    }
    
    private fun openLogFile() = synchronized(writeLock) {
        val day = LocalDate.now()
        val today = fileDateFormat.format(day)
        logFile = File(logsDir!!, "kime_$today$processSuffix.log")
        
        if (logFile!!.exists() && logFile!!.length() > MAX_LOG_SIZE) {
            val ts = rotatedFormat.format(LocalDateTime.now())
            val newLogFile = File(logsDir!!, "kime_$ts$processSuffix.log")
            logFile!!.renameTo(newLogFile)
            logFile = File(logsDir!!, "kime_$today$processSuffix.log")
        }
        
        writer?.close()
        writer = BufferedWriter(FileWriter(logFile, true))
        openedDay = day
    }

    /** 写满单个文件或跨天时换文件，并按数量和总大小清理旧日志。 */
    private fun rotateIfNeeded() {
        val file = logFile ?: return
        if (file.length() <= MAX_LOG_SIZE && openedDay == LocalDate.now()) return
        openLogFile()
        cleanOldLogs()
    }
    
    private fun flusherLoop() {
        val batch = ArrayList<String>(100)
        while (running) {
            try {
                batch.clear()
                batch.add(logQueue.take())
                logQueue.drainTo(batch, 99)

                val w = writer ?: continue
                synchronized(writeLock) {
                    for (line in batch) {
                        w.write(line)
                    }
                    w.flush()
                }
                rotateIfNeeded()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                Log.e(TAG, "Log flusher error", e)
            }
        }
    }
    
    fun isInitialized(): Boolean = isInitialized

    /** 运行时切换 verbose 日志（跨进程通过设置项/onStartInput 同步）。 */
    fun setVerboseLoggingEnabled(enabled: Boolean) {
        verboseLoggingEnabled = enabled
    }
    
    fun isVerboseLoggingEnabled(): Boolean = BuildConfig.DEBUG && verboseLoggingEnabled

    fun v(tag: String, message: String) {
        if (!isVerboseLoggingEnabled()) return
        Log.v(tag, message)
        writeToFile("V", tag, message)
    }
    
    fun d(tag: String, message: String) {
        if (!isVerboseLoggingEnabled()) return
        Log.d(tag, message)
        writeToFile("D", tag, message)
    }
    
    fun i(tag: String, message: String) {
        if (!isVerboseLoggingEnabled()) return
        Log.i(tag, message)
        writeToFile("I", tag, message)
    }

    /** 高频路径先检查开关，关闭时连字符串插值也不执行。 */
    inline fun i(tag: String, message: () -> String) {
        if (isVerboseLoggingEnabled()) i(tag, message())
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        writeToFile("W", tag, fullMessage)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        writeToFile("E", tag, fullMessage)
    }
    
    fun wtf(tag: String, message: String, throwable: Throwable? = null) {
        Log.wtf(tag, message, throwable)
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        writeToFile("F", tag, fullMessage)
    }
    
    private fun writeToFile(level: String, tag: String, message: String) {
        if (!isInitialized) return
        try {
            val timestamp = dateFormat.format(LocalDateTime.now())
            val logLine = "$timestamp [$level] $tag: $message\n"
            logQueue.offer(logLine)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue log", e)
        }
    }
    
    private fun cleanOldLogs() {
        try {
            logsDir?.let { dir ->
                val logFiles = dir.listFiles()
                    ?.filter { it.name.startsWith("kime_") && it.name.endsWith(".log") }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
                
                var total = 0L
                logFiles.forEachIndexed { index, file ->
                    total += file.length()
                    val overLimit = index >= MAX_LOG_FILES || total > MAX_TOTAL_LOG_BYTES
                    // 其他进程今天正在写的文件不删：删了空间也不释放，新日志还会丢。
                    if (overLimit && file != logFile && !isActiveLogName(file.name)) file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean old logs", e)
        }
    }
    
    fun getCurrentLogFile(): File? = logFile
    
    fun getAllLogFiles(): List<File> {
        return logsDir?.listFiles()
            ?.filter { it.name.startsWith("kime_") && it.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
    
    fun clearAllLogs() {
        try {
            logsDir?.listFiles()
                ?.filter { it.name.startsWith("kime_") }
                ?.forEach { it.delete() }
            
            openLogFile()
            
            i(TAG, "All logs cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }
    
    private fun cleanOrphanedLogs() {
        try {
            logsDir?.let { dir ->
                dir.listFiles()
                    ?.filter { it.name.endsWith(".log") && !it.name.startsWith("kime_") }
                    ?.forEach { file ->
                        if (file.length() > MAX_LOG_SIZE) {
                            file.delete()
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean orphaned logs", e)
        }
    }

    private fun cleanRimeLogs(context: Context) {
        try {
            val rimeLogDir = File(File(context.filesDir, "rime"), "logs")
            if (!rimeLogDir.exists()) return
            rimeLogDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_LOG_FILES)
                ?.forEach { file ->
                    file.delete()
                }
            rimeLogDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".log") && it.length() > MAX_LOG_SIZE }
                ?.forEach { file ->
                    file.delete()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean rime logs", e)
        }
    }

    fun reopenLogFile() {
        openLogFile()
    }

    fun flush() {
        writer?.flush()
    }

    /**
     * 同步把队列中尚未落盘的日志全部写出并 flush。
     * 崩溃处理器必须用这个而不是 [flush]：[flush] 只刷 writer，
     * 队列里的行仍依赖后台 flusher 线程，进程即将被杀时会丢失。
     */
    fun flushNow() {
        if (!isInitialized) return
        try {
            val batch = ArrayList<String>(logQueue.size)
            logQueue.drainTo(batch)
            val w = writer ?: return
            synchronized(writeLock) {
                for (line in batch) {
                    w.write(line)
                }
                w.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "flushNow failed", e)
        }
    }
}