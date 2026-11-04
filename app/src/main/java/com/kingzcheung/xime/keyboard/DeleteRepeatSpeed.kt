package com.kingzcheung.xime.keyboard

/**
 * 「长按删除速度」（别人提的需求，11-03：长按删除振得太慢不够爽）。按住删除键连删的间隔和振动的最小间隔一起换档，
 * 振动始终跟着连删走：每档都是连删两三下振一下，不会一边删得快一边振得慢。默认「标准」= 原来的 30 ms 连删、
 * 100 ms 振动间隔，没调过的人手感不变。最快一档振动仍隔开 45 ms，转子马达不会连成一长串嗡（10-31 的问题）。
 */
enum class DeleteRepeatSpeed(val label: String, val intervalMs: Long, val hapticGapMs: Long) {
    SLOW("慢", 60L, 180L),
    SLOWER("较慢", 45L, 135L),
    STANDARD("标准", 30L, 100L),
    FASTER("较快", 22L, 66L),
    FAST("快", 16L, 45L);

    /** 离上一下这么近的只能是连删，手指连点到不了；标准档是原来的 80 ms，慢档也不超过 100 ms，快速连点照常每下都振。 */
    val repeatGapMs: Long get() = minOf(intervalMs + 50L, 100L)

    companion object {
        const val KEY = "delete_repeat_speed"
        val DEFAULT = STANDARD
        fun fromName(name: String?): DeleteRepeatSpeed = entries.firstOrNull { it.name == name } ?: DEFAULT
        /** 设置页滑条的位置（0 = 慢）。 */
        fun fromIndex(index: Int): DeleteRepeatSpeed = entries[index.coerceIn(0, entries.lastIndex)]
    }
}
