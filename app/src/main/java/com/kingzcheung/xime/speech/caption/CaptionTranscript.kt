package com.kingzcheung.xime.speech.caption

/**
 * Caption lines keyed by segment id, following ASRInput's caption contract: a line is created by
 * its first text, a final closes it, and anything that arrives for a closed id is rejected so a
 * reader never sees a sentence rewritten. Speaker labels are the exception: they may be
 * corrected after the fact (merge/reassign), because the text itself does not move.
 */
internal class CaptionTranscript {
    class Entry(val id: Long) {
        var text = ""
        var startMs = 0L
        var endMs = 0L
        var boundary = 0
        var confirmed = false
        var event = ""
        var speakerId: Int? = null
        var confident = true
    }

    private val entries = LinkedHashMap<Long, Entry>()

    val size: Int get() = entries.size
    fun entries(): List<Entry> = entries.values.toList()
    operator fun get(id: Long): Entry? = entries[id]

    /** Returns the updated line, or null when the line is already closed (late result ignored). */
    fun update(id: Long, text: String, startMs: Long, endMs: Long, boundary: Int, confirmed: Boolean, event: String): Entry? {
        val entry = entries[id]
        if (entry != null && entry.confirmed) return null
        val line = entry ?: Entry(id).also { entries[id] = it }
        line.text = text
        line.startMs = startMs
        if (endMs > 0) line.endMs = endMs
        line.boundary = boundary
        line.event = event
        line.confirmed = confirmed
        return line
    }

    fun assign(id: Long, speakerId: Int, confident: Boolean): Entry {
        val line = entries.getOrPut(id) { Entry(id) }
        line.speakerId = speakerId
        line.confident = confident
        return line
    }

    fun reassign(id: Long, speakerId: Int): Boolean {
        val line = entries[id] ?: return false
        line.speakerId = speakerId
        line.confident = true
        return true
    }

    /** Every line of [from] now belongs to [into]; returns how many moved. */
    fun mergeSpeakers(from: Int, into: Int): Int {
        var moved = 0
        for (line in entries.values) if (line.speakerId == from) { line.speakerId = into; moved++ }
        return moved
    }

    fun remove(id: Long) { entries.remove(id) }
    fun clear() = entries.clear()
}
