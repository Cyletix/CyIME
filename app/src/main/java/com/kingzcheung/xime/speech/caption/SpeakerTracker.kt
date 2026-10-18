package com.kingzcheung.xime.speech.caption

import kotlin.math.sqrt

/** One voiceprint for a window of speech; null when the audio is unusable. */
internal fun interface EmbeddingSource {
    fun embed(samples: FloatArray): FloatArray?
}

/**
 * Who is speaking, decided on the continuous audio rather than on recognition segments.
 *
 * The recognizer cuts at every comma-length pause, and a one-second piece is too short for a
 * stable voiceprint (device log 2026-10-16: one voice split into "第 3 / 第 4 个人"). Instead the
 * session sends a 2 s window of speech every second; windows are clustered online with hysteresis
 * (a new person needs two consecutive unmatched windows that resemble each other) and clusters
 * that drift together are merged. A recognized sentence then takes the label that covers most of
 * its time span. All methods are synchronized: the speaker worker labels while binder threads
 * merge or reassign.
 */
internal class SpeakerTracker(strictness: Int = 1) {
    /** A label for a sentence; re-sent when a merge or correction changes it. */
    /**
     * A label for a sentence; re-sent when a merge or correction changes it. [initial] marks the first
     * label of a sentence, which carries [runs]: the voices heard inside it in time order as
     * (speaker, from ms), more than one when two people spoke without a pause between them.
     */
    data class Label(val segmentId: Long, val speakerId: Int, val initial: Boolean = false, val runs: List<Pair<Int, Long>> = emptyList())

    private class Speaker(val id: Int) {
        val prints = ArrayDeque<FloatArray>()
        var centroid: FloatArray? = null
        var ignored = false
        var named = false
        fun add(print: FloatArray) {
            prints.addLast(print)
            while (prints.size > MAX_PRINTS) prints.removeFirst()
            val sum = FloatArray(print.size)
            for (p in prints) for (i in sum.indices) sum[i] += p[i]
            centroid = normalized(sum)
        }
    }

    private class Window(val startMs: Long, val endMs: Long, val print: FloatArray, var speaker: Int)
    private class Pending(val id: Long, val startMs: Long, val endMs: Long)

    private val speakers = linkedMapOf<Int, Speaker>()
    private val windows = ArrayDeque<Window>()
    private val pending = ArrayList<Pending>()
    private val segmentSpeaker = object : LinkedHashMap<Long, Int>(64, .75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Int>?) = size > MAX_SEGMENTS
    }
    private val segmentSpan = object : LinkedHashMap<Long, LongArray>(64, .75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, LongArray>?) = size > MAX_SEGMENTS
    }
    private var nextId = 1
    private var unmatched: Window? = null
    private var threshold = thresholdFor(strictness)

    val speakerIds: List<Int> get() = synchronized(this) { speakers.keys.toList() }
    val lastWindowSpeaker: Int get() = synchronized(this) { windows.lastOrNull()?.speaker ?: -1 }
    val lastWindowEndMs: Long get() = synchronized(this) { windows.lastOrNull()?.endMs ?: -1L }

    @Synchronized fun setStrictness(level: Int) { threshold = thresholdFor(level) }

    /** One window of speech ending at [endMs]; returns labels of sentences this window settles or changes. */
    @Synchronized fun addWindow(startMs: Long, endMs: Long, rawPrint: FloatArray): List<Label> {
        val print = normalized(rawPrint)
        val scored = speakers.values.map { it to similarity(print, it.centroid) }.maxByOrNull { it.second }
        val window: Window
        if (scored == null) {
            window = Window(startMs, endMs, print, newSpeaker(print).id)
            unmatched = null
        } else {
            val (best, score) = scored
            val limit = if (best.ignored) threshold - IGNORED_SLACK else threshold
            if (score >= limit) {
                best.add(print)
                window = Window(startMs, endMs, print, best.id)
                unmatched = null
            } else {
                val previous = unmatched
                if (score < CLEARLY_DIFFERENT) {
                    // Nothing like anyone heard so far (through the air: other voices ≈ 0.15–0.23,
                    // the same voice ≥ 0.6): a new person at once, so a short first reply is not lost.
                    val speaker = newSpeaker(print)
                    window = Window(startMs, endMs, print, speaker.id)
                    unmatched = null
                    windows.addLast(window)
                    while (windows.size > MAX_WINDOWS) windows.removeFirst()
                    return revote(startMs, endMs) + mergeSimilar() + resolve(endMs)
                } else if (previous != null && similarity(print, previous.print) >= threshold) {
                    // Two unmatched windows in a row that sound alike: a new person.
                    val speaker = newSpeaker(previous.print)
                    speaker.add(print)
                    previous.speaker = speaker.id
                    window = Window(startMs, endMs, print, speaker.id)
                    unmatched = null
                    windows.addLast(window)
                    while (windows.size > MAX_WINDOWS) windows.removeFirst()
                    // Sentences already labelled from the borrowed window get the new person.
                    return revote(previous.startMs, endMs) + mergeSimilar() + resolve(endMs)
                } else {
                    // One odd window (cough, overlap, TV burst) borrows the closest voice for now.
                    window = Window(startMs, endMs, print, best.id)
                    unmatched = window
                }
            }
        }
        windows.addLast(window)
        while (windows.size > MAX_WINDOWS) windows.removeFirst()
        val changed = mergeSimilar()
        return changed + resolve(endMs)
    }

    /** A finished sentence; labelled once a window covers its end (or [settle] is called). */
    @Synchronized fun addSegment(id: Long, startMs: Long, endMs: Long): List<Label> {
        pending.add(Pending(id, startMs, endMs))
        return resolve(windows.lastOrNull()?.endMs ?: Long.MIN_VALUE)
    }

    /** Label every waiting sentence with what is known now (end of speech, stop). */
    @Synchronized fun settle(): List<Label> = resolve(Long.MAX_VALUE)

    private fun resolve(upTo: Long): List<Label> {
        if (pending.isEmpty() || windows.isEmpty()) return emptyList()
        val done = pending.filter { it.endMs <= upTo }
        pending.removeAll(done.toSet())
        return done.map { segment ->
            val votes = HashMap<Int, Long>()
            for (w in windows) {
                val overlap = minOf(w.endMs, segment.endMs) - maxOf(w.startMs, segment.startMs)
                if (overlap > 0) votes[w.speaker] = (votes[w.speaker] ?: 0) + overlap
            }
            val speaker = votes.maxByOrNull { it.value }?.key
                ?: windows.minBy { minOf(kotlin.math.abs(it.endMs - segment.startMs), kotlin.math.abs(it.startMs - segment.endMs)) }.speaker
            segmentSpeaker[segment.id] = speaker
            segmentSpan[segment.id] = longArrayOf(segment.startMs, segment.endMs)
            Label(segment.id, speaker, initial = true, runs = runs(segment.startMs, segment.endMs))
        }
    }

    /** Voices inside [startMs, endMs] in order; a change point sits halfway between the two windows' centres. */
    private fun runs(startMs: Long, endMs: Long): List<Pair<Int, Long>> {
        val inside = windows.filter { minOf(it.endMs, endMs) - maxOf(it.startMs, startMs) > 0 }.sortedBy { it.endMs }
        val out = ArrayList<Pair<Int, Long>>()
        var previous: Window? = null
        for (w in inside) {
            if (out.isEmpty()) out += w.speaker to startMs
            else if (w.speaker != out.last().first) {
                val prev = previous!!
                val change = ((prev.startMs + prev.endMs) / 2 + (w.startMs + w.endMs) / 2) / 2
                out += w.speaker to change.coerceIn(startMs, endMs)
            }
            previous = w
        }
        return out
    }

    /** Already-labelled sentences overlapping a relabelled stretch are voted again; changed ones are returned. */
    private fun revote(fromMs: Long, toMs: Long): List<Label> {
        val changed = ArrayList<Label>()
        for ((id, span) in segmentSpan.entries.toList()) {
            if (minOf(span[1], toMs) - maxOf(span[0], fromMs) <= 0) continue
            val votes = HashMap<Int, Long>()
            for (w in windows) {
                val overlap = minOf(w.endMs, span[1]) - maxOf(w.startMs, span[0])
                if (overlap > 0) votes[w.speaker] = (votes[w.speaker] ?: 0) + overlap
            }
            val speaker = votes.maxByOrNull { it.value }?.key ?: continue
            if (segmentSpeaker[id] != speaker) { segmentSpeaker[id] = speaker; changed += Label(id, speaker) }
        }
        return changed
    }

    /** A sentence piece created by splitting at a change of voice; kept so merges and corrections reach it. */
    @Synchronized fun register(segmentId: Long, startMs: Long, endMs: Long, speakerId: Int) {
        segmentSpeaker[segmentId] = speakerId
        segmentSpan[segmentId] = longArrayOf(startMs, endMs)
    }

    /** Clusters of one voice that drift together are joined; the older id survives. */
    private fun mergeSimilar(): List<Label> {
        val changed = ArrayList<Label>()
        while (true) {
            val list = speakers.values.toList()
            var pair: Pair<Speaker, Speaker>? = null
            loop@ for (i in list.indices) for (j in i + 1 until list.size) {
                if (list[i].named && list[j].named) continue
                if (similarity(list[i].centroid ?: continue, list[j].centroid) >= MERGE_SIMILARITY) { pair = list[i] to list[j]; break@loop }
            }
            val (keep, drop) = pair ?: return changed
            changed += moveAll(drop, keep)
        }
    }

    private fun moveAll(from: Speaker, into: Speaker): List<Label> {
        for (p in from.prints) into.add(p)
        into.named = into.named || from.named
        into.ignored = into.ignored && from.ignored
        speakers.remove(from.id)
        for (w in windows) if (w.speaker == from.id) w.speaker = into.id
        val moved = ArrayList<Label>()
        for (entry in segmentSpeaker.entries) if (entry.value == from.id) { entry.setValue(into.id); moved += Label(entry.key, into.id) }
        return moved
    }

    @Synchronized fun merge(from: Int, into: Int): List<Label> {
        if (from == into) return emptyList()
        val source = speakers[from] ?: return emptyList()
        val target = speakers[into] ?: return emptyList()
        return moveAll(source, target)
    }

    /** "This sentence was that person": its windows teach the target voice. */
    @Synchronized fun reassign(segmentId: Long, speakerId: Int) {
        val target = speakers[speakerId] ?: return
        segmentSpeaker[segmentId] = speakerId
        val span = segmentSpan[segmentId] ?: return
        for (w in windows) if (w.startMs >= span[0] - 500 && w.endMs <= span[1] + 500) {
            w.speaker = speakerId
            target.add(w.print)
        }
    }

    @Synchronized fun setIgnored(speakerId: Int, ignored: Boolean) { speakers[speakerId]?.ignored = ignored }

    /**
     * The phone holder's own voice, remembered between sessions so call captions can leave it out.
     * It matches with the ignored slack, so near matches of the holder go to it rather than to a new person.
     */
    @Synchronized fun seedSelf(print: FloatArray) {
        val self = speakers[SELF] ?: Speaker(SELF).also { speakers[SELF] = it }
        self.ignored = true
        self.add(normalized(print))
    }

    /** "This voice is me": [from] becomes the holder's voice. Returns the holder's voiceprint to keep. */
    @Synchronized fun markSelf(from: Int): FloatArray? {
        val source = speakers[from] ?: return speakers[SELF]?.centroid
        if (from != SELF) {
            val self = speakers[SELF] ?: Speaker(SELF).also { speakers[SELF] = it }
            moveAll(source, self)
            self.ignored = true
        }
        return speakers[SELF]?.centroid
    }

    @Synchronized fun forgetSelf() { speakers.remove(SELF)?.let { self -> for (w in windows) if (w.speaker == self.id) w.speaker = -1 } }
    @Synchronized fun setNamed(speakerId: Int, named: Boolean) { speakers[speakerId]?.named = named }

    @Synchronized fun reset() {
        speakers.clear(); windows.clear(); pending.clear(); segmentSpeaker.clear(); segmentSpan.clear()
        nextId = 1; unmatched = null
    }

    private fun newSpeaker(print: FloatArray): Speaker = Speaker(nextId++).also { it.add(print); speakers[it.id] = it }

    companion object {
        /** The phone holder (see [seedSelf]); far above any automatic number. */
        const val SELF = 999
        const val WINDOW_MS = 2000L
        const val HOP_MS = 1000L
        const val MAX_PRINTS = 30
        const val MAX_WINDOWS = 600
        const val MAX_SEGMENTS = 1000
        const val IGNORED_SLACK = .05f
        /** Below this a window is someone new straight away (no second window needed). */
        const val CLEARLY_DIFFERENT = .25f
        /** Through-air same-voice segments measured 0.43–0.86 on the tablet; averaged centroids sit higher. */
        const val MERGE_SIMILARITY = .62f

        fun thresholdFor(level: Int): Float = when {
            level <= 0 -> .35f
            level == 1 -> .40f
            else -> .45f
        }

        fun similarity(a: FloatArray, b: FloatArray?): Float {
            if (b == null || a.size != b.size) return -1f
            var dot = 0f
            for (i in a.indices) dot += a[i] * b[i]
            return dot
        }

        fun normalized(v: FloatArray): FloatArray {
            var norm = 0f
            for (x in v) norm += x * x
            val scale = if (norm > 0f) 1f / sqrt(norm) else 0f
            return FloatArray(v.size) { v[it] * scale }
        }
    }
}
