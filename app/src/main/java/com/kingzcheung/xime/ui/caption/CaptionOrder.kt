package com.kingzcheung.xime.ui.caption

import android.content.SharedPreferences

/**
 * Where a new sentence goes in the caption list: after everything said before it. A sentence cut
 * at a change of voice publishes its later pieces only once SenseVoice has finished the whole
 * sentence, by which time the next sentence is already on screen; appending them made two
 * people's words run into each other (2026-10-21 report "两段话重叠在一起").
 */
internal fun insertionIndex(segments: List<SegmentUi>, wallMs: Long): Int {
    var i = segments.size
    while (i > 0 && segments[i - 1].wallMs > wallMs) i--
    return i
}

/** The quick-reply card stays turned towards the other person until the user turns it back. */
internal class ReplyFlipMemory(private val prefs: SharedPreferences) {
    fun read(): Boolean = prefs.getBoolean(KEY, false)
    fun write(flipped: Boolean) { prefs.edit().putBoolean(KEY, flipped).apply() }
    private companion object { const val KEY = "reply_flipped" }
}
