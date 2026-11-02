package com.kingzcheung.xime.clipboard.sync

/**
 * 剪贴板的先后顺序（三台互相推送，用户 11-02 选的做法 A）。
 *
 * 每条剪贴板带一个序号：本机复制的用复制时间（毫秒），收到的沿用发出那台的序号。
 * 收到的只在比手头这条新时才写进剪贴板，并且不再转发给别人，所以几台之间不会来回推。
 * 旧版对端不带序号（0），按“刚复制的”处理，和以前一样以对端为准。
 */
object ClipboardClock {
    private class Entry(val hash: String, val stamp: Long, val received: Boolean)

    private var current: Entry? = null
    private var last = 0L

    /** A fresh order number, never smaller than one already seen. */
    @Synchronized fun now(): Long = maxOf(System.currentTimeMillis(), last + 1).also { last = it }

    /**
     * The text now on this device's clipboard. A copy this device just received keeps the sender's
     * number; anything else is a local copy, ordered by when it was copied ([copiedAt], 0 = unknown).
     */
    @Synchronized fun noteLocal(hash: String, copiedAt: Long): Long {
        current?.takeIf { it.hash == hash }?.let { return it.stamp }
        val stamp = if (copiedAt > 0) copiedAt else System.currentTimeMillis()
        last = maxOf(last, stamp)
        current = Entry(hash, stamp, false)
        return stamp
    }

    @Synchronized fun stampOf(hash: String): Long? = current?.takeIf { it.hash == hash }?.stamp

    @Synchronized fun isCurrent(hash: String): Boolean = current?.hash == hash

    /** Received from another device: never forwarded again. */
    @Synchronized fun isReceived(hash: String): Boolean = current?.let { it.hash == hash && it.received } == true

    /**
     * Another device sent [hash] with [stamp] (0 = an older CyIME without numbers). True when it is
     * newer than what this device holds and should be written; the caller then writes it.
     */
    @Synchronized fun accept(hash: String, stamp: Long): Boolean {
        val held = current
        if (held != null && held.hash == hash) {
            if (stamp > held.stamp) current = Entry(hash, stamp, held.received)
            last = maxOf(last, stamp)
            return false
        }
        val order = if (stamp > 0) stamp else now()
        if (held != null && order <= held.stamp) return false
        last = maxOf(last, order)
        current = Entry(hash, order, true)
        return true
    }
}
