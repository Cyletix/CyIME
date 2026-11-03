package com.kingzcheung.xime.clipboard

/**
 * How much text history the user keeps (user 11-03: 定期清理之外也限制条数, 有人拿剪贴板存文档, 让用户自己定,
 * 默认中庸): at most [maxCount] records, and records copied more than [keepDays] days ago go. Fixed records
 * and records in a group never count and are never removed. Images keep their own limit.
 */
internal data class ClipboardHistoryLimits(val maxCount: Int, val keepDays: Int) {
    /** Records copied before this time go; [Long.MIN_VALUE] when nothing expires. */
    fun cutoff(now: Long): Long = if (keepDays <= 0) Long.MIN_VALUE else now - keepDays * ClipboardRetention.DAY_MS
}

internal object ClipboardRetention {
    const val UNLIMITED = 0
    const val NEVER = 0
    const val DAY_MS = 24 * 60 * 60_000L

    /** Same choices as CyIME for Windows (100 / 300 / 1000), plus room for people who keep documents here. */
    val COUNT_OPTIONS = listOf(100, 300, 1000, 5000, UNLIMITED)
    const val DEFAULT_COUNT = 1000
    /** Same choices and words as CyIME for Windows; history never expired here before, so 不过期 stays the default. */
    val DAY_OPTIONS = listOf(1, 7, 30, 90, NEVER)
    const val DEFAULT_DAYS = NEVER

    fun countLabel(count: Int): String = if (count == UNLIMITED) "不限" else "$count 条"
    fun daysLabel(days: Int): String = if (days == NEVER) "不过期" else "$days 天"

    /**
     * The first time: the default, or the smallest choice that holds the history already there, so
     * updating the app deletes nothing; more than the largest choice means 不限.
     */
    fun initialCount(history: Int): Int =
        COUNT_OPTIONS.firstOrNull { it != UNLIMITED && it >= DEFAULT_COUNT && it >= history } ?: UNLIMITED

    /**
     * Records the limits remove from [history] records, [expired] of them older than the cutoff. Both
     * limits take the oldest first, so the larger of the two is the answer.
     */
    fun removedBy(history: Int, expired: Int, maxCount: Int): Int =
        maxOf(expired, if (maxCount == UNLIMITED) 0 else history - maxCount, 0)
}
