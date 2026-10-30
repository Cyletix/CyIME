package com.kingzcheung.xime.clipboard

/**
 * How much clipboard history is kept: a fixed ceiling on the stored text (the user asked on
 * 2026-10-22 for a sensible fixed limit instead of measuring the phone), and never more than
 * [DISK_SHARE] of the free space. When it is full, new copies are not saved.
 */
internal object ClipboardCapacity {
    /** All stored text (fixed and history) together: about ten thousand everyday copies. */
    const val CEILING_BYTES = 2L * 1024 * 1024

    /** Share of the free storage the history may use at most (a nearly full phone). */
    const val DISK_SHARE = 0.02

    /**
     * Android reads a database row through a 2 MB window; a longer record would be saved but could
     * never be read back. This is the system's limit, kept with room for the other columns.
     */
    const val ROW_BYTES = 2 * 1024 * 1024 - 64 * 1024

    /**
     * Copies received from another device have their own room, outside [CEILING_BYTES]: the oldest
     * received copy makes way for a new one, so they never push out or block this phone's own copies
     * or fixed records (user 2026-10-30). Same numbers as CyIME for Windows.
     */
    const val DEVICE_COUNT = 100
    const val DEVICE_BYTES = 1L * 1024 * 1024

    fun capacity(freeDiskBytes: Long): Long = minOf(CEILING_BYTES, (freeDiskBytes * DISK_SHARE).toLong())

    /** Bytes one record costs: its text and its search key, as stored (UTF-8). */
    fun recordBytes(text: String, searchKey: String): Long =
        text.encodeToByteArray().size.toLong() + searchKey.length

    /** False for a record the database could not read back. */
    fun fitsRow(text: String): Boolean = text.length <= ROW_BYTES && text.encodeToByteArray().size <= ROW_BYTES

    fun accepts(storedBytes: Long, addingBytes: Long, capacityBytes: Long): Boolean =
        storedBytes + addingBytes <= capacityBytes
}

/**
 * Room left in the history. [refused] is set when a copy was not saved for lack of room and
 * cleared once records are removed; the board then says the clipboard is full.
 */
internal data class ClipboardRoom(val storedBytes: Long = 0, val capacityBytes: Long = Long.MAX_VALUE,
    val refused: Boolean = false) {
    val full get() = refused || storedBytes >= capacityBytes
}
