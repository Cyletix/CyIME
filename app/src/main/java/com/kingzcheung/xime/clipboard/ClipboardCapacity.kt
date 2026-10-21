package com.kingzcheung.xime.clipboard

/**
 * How much clipboard history this device can hold, worked out from the device itself instead of a
 * fixed number of records. Every search reads all stored text once, so the history may only grow
 * as far as this phone can search through it within [SEARCH_BUDGET_MS] per keystroke; it also never
 * takes more than [DISK_SHARE] of the free space. When it is full, new copies are not saved.
 */
internal object ClipboardCapacity {
    /** Longest one search over the whole history may take; typing stays smooth below it. */
    const val SEARCH_BUDGET_MS = 40.0

    /** Share of the free storage the history may use at most. */
    const val DISK_SHARE = 0.02

    /**
     * Android reads a database row through a 2 MB window; a longer record would be saved but could
     * never be read back. This is the system's limit, kept with room for the other columns.
     */
    const val ROW_BYTES = 2 * 1024 * 1024 - 64 * 1024

    /** Scan speed assumed before this device has been measured (a slow phone's), so it errs small. */
    const val UNMEASURED_BYTES_PER_MS = 20_000.0

    /** Below this much stored text a timing is mostly noise. */
    const val MIN_MEASURED_BYTES = 512L * 1024

    /** Bytes one record costs: its text and its search key, as stored (UTF-8). */
    fun recordBytes(text: String, searchKey: String): Long =
        text.encodeToByteArray().size.toLong() + searchKey.length

    /** False for a record the database could not read back. */
    fun fitsRow(text: String): Boolean = text.length <= ROW_BYTES && text.encodeToByteArray().size <= ROW_BYTES

    /** Search speed from timing a full scan of [bytes]; null when there was too little to time. */
    fun scanRate(bytes: Long, elapsedMs: Double): Double? =
        if (bytes < MIN_MEASURED_BYTES || elapsedMs <= 0.0) null else bytes / elapsedMs

    fun capacity(bytesPerMs: Double, freeDiskBytes: Long): Long =
        minOf((bytesPerMs * SEARCH_BUDGET_MS).toLong(), (freeDiskBytes * DISK_SHARE).toLong())

    fun accepts(storedBytes: Long, addingBytes: Long, capacityBytes: Long): Boolean =
        storedBytes + addingBytes <= capacityBytes

    /** Measure again once the history has grown a quarter beyond the last measurement. */
    fun shouldRemeasure(storedBytes: Long, measuredAtBytes: Long): Boolean =
        storedBytes >= MIN_MEASURED_BYTES && storedBytes > measuredAtBytes + measuredAtBytes / 4
}

/**
 * Room left in the history. [refused] is set when a copy was not saved for lack of room and
 * cleared once records are removed; the board then says the clipboard is full.
 */
internal data class ClipboardRoom(val storedBytes: Long = 0, val capacityBytes: Long = Long.MAX_VALUE,
    val refused: Boolean = false) {
    val full get() = refused || storedBytes >= capacityBytes
}
