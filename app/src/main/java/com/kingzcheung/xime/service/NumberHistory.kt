package com.kingzcheung.xime.service

import android.content.Context
import java.io.File
import java.util.concurrent.Executors

/**
 * 联想常用数字（用户 11-04 要求，默认开）：屏幕键盘打过两次以上的 4~15 位数字（电话、QQ 号、单号）记在本机，
 * 再打开头两位时和「数字出短语」放在同一组候选里，点一下把光标前这串数字换成整串。数字照常先上屏，不点和以前一样。
 *
 * 隐私：只存本机 noBackupFilesDir（不进系统云备份），不联网；密码框、无痕（手动或输入框声明不学习）、
 * 键盘里的面板输入框、实体键盘、终端不记；16 位以上（银行卡、身份证）不记；字母、点、@ 后面接着的数字
 * （用户名、小数、邮箱、网址）不记；算式里的数字不记；只打过一次的不给出，一个月没再用就忘掉；关掉开关立即清空。
 */
internal object NumberHistory {
    /** 设置键，默认开（用户自己的要求）。 */
    const val PREF_KEY = "number_history_candidates"

    const val FILE_NAME = "number_history.txt"

    fun isEnabled(context: Context): Boolean =
        com.kingzcheung.xime.settings.SettingsPreferences.getPrefsPublic(context).getBoolean(PREF_KEY, true)

    /** 关掉时清空已经记下的数字。 */
    fun setEnabled(context: Context, on: Boolean) {
        com.kingzcheung.xime.settings.SettingsPreferences.getPrefsPublic(context).edit().putBoolean(PREF_KEY, on).apply()
        if (!on) clear(context)
    }

    @Volatile private var memory: NumberMemory? = null
    private val saver by lazy { Executors.newSingleThreadExecutor { Thread(it, "number-history").apply { isDaemon = true } } }

    private fun file(context: Context) = File(context.noBackupFilesDir, FILE_NAME)

    /** 第一次用时读文件，之后只用内存。不要在主线程第一次调用。 */
    fun load(context: Context): NumberMemory = memory ?: synchronized(this) {
        memory ?: NumberMemory.read(file(context)).also { memory = it }
    }

    /** 已经读过时给内存里的那份（主线程用，不碰文件）；还没读过返回 null。 */
    fun loaded(): NumberMemory? = memory

    /** 记一次用到 [number]，在后台写文件。 */
    fun record(context: Context, number: String, now: Long = System.currentTimeMillis()) {
        val m = load(context)
        if (!m.record(number, now)) return
        val target = file(context)
        val text = m.serialize()
        saver.execute { NumberMemory.write(target, text) }
    }

    fun clear(context: Context) {
        memory?.clear()
        val target = file(context)
        saver.execute { target.delete() }
    }
}

/** 记下的数字：号码 → 用了几次、最后一次什么时候。线程安全。 */
internal class NumberMemory(entries: Map<String, Entry> = emptyMap()) {
    data class Entry(val count: Int, val lastUsed: Long)

    private val map = LinkedHashMap(entries)

    companion object {
        const val MIN_LENGTH = 4
        /** 16 位以上是银行卡、身份证这类，不记。 */
        const val MAX_LENGTH = 15
        /** 用过几次才给出。 */
        const val MIN_USES = 2
        /** 打了几位开始给。 */
        const val MIN_TYPED = 2
        const val LIMIT = 5
        const val MAX_ENTRIES = 200
        /** 只用过一次的，这么久没再用就忘掉。 */
        const val FORGET_SINGLE_MS = 30L * 24 * 60 * 60 * 1000

        fun learnable(number: String): Boolean =
            number.length in MIN_LENGTH..MAX_LENGTH && number.all { it in '0'..'9' }

        fun parse(text: String, now: Long = System.currentTimeMillis()): NumberMemory {
            val entries = LinkedHashMap<String, Entry>()
            text.lineSequence().forEach { line ->
                val parts = line.split('\t')
                if (parts.size != 3 || !learnable(parts[0])) return@forEach
                val count = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
                val last = parts[2].toLongOrNull() ?: return@forEach
                if (count < MIN_USES && now - last > FORGET_SINGLE_MS) return@forEach
                entries[parts[0]] = Entry(count, last)
            }
            return NumberMemory(entries)
        }

        fun read(file: File): NumberMemory =
            if (!file.isFile) NumberMemory()
            else runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrDefault(NumberMemory())

        /** 先写临时文件再换名，写到一半退出不会坏掉原文件。 */
        fun write(file: File, text: String) {
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                tmp.writeText(text, Charsets.UTF_8)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }
    }

    /** 记一次；不该记的（太短、太长、不全是数字）返回 false。 */
    @Synchronized
    fun record(number: String, now: Long): Boolean {
        if (!learnable(number)) return false
        val old = map.remove(number)
        map[number] = Entry((old?.count ?: 0) + 1, now)
        while (map.size > MAX_ENTRIES) {
            val weakest = map.entries.minWithOrNull(compareBy({ it.value.count }, { it.value.lastUsed })) ?: break
            map.remove(weakest.key)
        }
        return true
    }

    /** 以 [typed]（半角）开头、比它长、用过两次以上的号码：次数多的在前，同样多的最近用的在前。 */
    @Synchronized
    fun suggestions(typed: String): List<String> {
        if (typed.length < MIN_TYPED) return emptyList()
        return map.entries.filter { (n, e) -> e.count >= MIN_USES && n.length > typed.length && n.startsWith(typed) }
            .sortedWith(compareBy({ -it.value.count }, { -it.value.lastUsed }))
            .take(LIMIT).map { it.key }
    }

    @Synchronized
    fun contains(number: String): Boolean = map.containsKey(number)

    @Synchronized
    fun count(number: String): Int = map[number]?.count ?: 0

    @Synchronized
    fun clear() = map.clear()

    @Synchronized
    fun serialize(): String = map.entries.joinToString("") { (n, e) -> "$n\t${e.count}\t${e.lastUsed}\n" }
}

/**
 * 跟着光标前正在打的那串数字，打完（下一次打数字时已经不是接着这串）才交出去记。
 * 只看光标前的文字，不另存打过的字；退格或不该记的时候直接丢掉。
 */
internal class NumberRunTracker {
    private var run: String? = null

    /**
     * 刚上屏一个数字，[before] 是光标前的文字（null：这里不该记，比如密码框、算式里、邮箱 @ 后面）。
     * 返回上一串已经打完、该记下的号码，没有返回 null。
     */
    fun onDigit(before: String?): String? {
        val current = before?.let(::learnableRun)
        val previous = run
        if (previous != null && current != null && current.length > previous.length && current.startsWith(previous)) {
            run = current
            return null
        }
        run = current
        return previous?.takeIf(NumberMemory::learnable)
    }

    /** 退格、选了候选、换了别的东西：这一串不记。 */
    fun reset() {
        run = null
    }

    companion object {
        /** 光标前最后那串数字（换成半角）；前面紧挨着字母、点、@、下划线、斜杠、冒号的不算（用户名、小数、网址、时间）。 */
        fun learnableRun(before: String): String? {
            val digits = DigitPhrases.typedDigits(before) ?: return null
            val prev = before.getOrNull(before.length - digits.length - 1)
            if (prev != null && (prev in 'a'..'z' || prev in 'A'..'Z' || prev in ".@_/:．＠")) return null
            return DigitPhrases.code(digits)
        }
    }
}
