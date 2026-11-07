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
        /** Born from short probes: keeps learning from its sure probes until a 2 s window matches it. */
        var fromProbes = false
        /** Session time it first appeared; fragments are judged only once they are old enough. */
        var bornMs = 0L
        /** Placed by [recluster]: drifting centroids no longer merge it (the look-back regrouping decides). */
        var regrouped = false
        /** The user joined someone into it: the look-back regrouping never splits it again. */
        var joinedByUser = false
        fun add(print: FloatArray) {
            prints.addLast(print)
            while (prints.size > MAX_PRINTS) prints.removeFirst()
            val sum = FloatArray(print.size)
            for (p in prints) for (i in sum.indices) sum[i] += p[i]
            centroid = normalized(sum)
        }
    }

    private class Window(val startMs: Long, val endMs: Long, val print: FloatArray, var speaker: Int, var pinned: Boolean = false)
    private class Pending(val id: Long, val startMs: Long, val endMs: Long)

    private val speakers = linkedMapOf<Int, Speaker>()
    private val windows = ArrayDeque<Window>()
    /** 1 s probes every 0.5 s: only matched against known voices, they place the change points. */
    private val probes = ArrayDeque<Window>()
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
        nowMs = endMs
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
                best.fromProbes = false
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

    /**
     * A short probe (1 s, every 0.5 s). Two-second windows straddle a quick turn and blur both voices,
     * so they only find and learn the voices; the probes, too short to learn from, say who is speaking
     * at each half second. A probe that matches nobody well keeps the window's answer.
     */
    @Synchronized fun addProbe(startMs: Long, endMs: Long, rawPrint: FloatArray): List<Label> {
        val print = normalized(rawPrint)
        val ranked = speakers.values.map { it to similarity(print, it.centroid) }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return emptyList()
        val second = ranked.getOrNull(1)?.second ?: -1f
        val sure = best.second >= threshold - PROBE_SLACK && best.second - second >= PROBE_MARGIN
        probes.addLast(Window(startMs, endMs, print, if (sure) best.first.id else -1))
        nowMs = endMs
        // A probe-born voice is judged on more and more of its own audio: as its average steadies,
        // a split of one person (one noisy short probe) drifts back to that person and merges.
        if (sure && best.first.fromProbes) {
            best.first.add(print)
            val merged = mergeSimilar()
            if (merged.isNotEmpty()) return merged
        }
        while (probes.size > MAX_WINDOWS * 2) probes.removeFirst()
        // A short reply never fills a 2 s window: two probes in a row unlike everyone known but
        // alike each other are a new person (1.5 s of that voice). PC replay 2026-10-21, 20 tight
        // two-person dialogues: missed turn changes 49% → 25% clean, 68% → 52% with room echo;
        // 60 single-voice sessions (up to 50 s, with echo) still never split.
        if (best.second < PROBE_NEW) {
            val last = probes.takeLast(PROBE_RUN)
            if (last.size == PROBE_RUN && last.all { p -> (speakers.values.maxOfOrNull { similarity(p.print, it.centroid) } ?: 0f) < PROBE_NEW } &&
                (0 until PROBE_RUN).all { i -> (i + 1 until PROBE_RUN).all { j -> similarity(last[i].print, last[j].print) >= threshold } }) {
                val speaker = newSpeaker(last[0].print)
                speaker.fromProbes = true
                for (p in last.drop(1)) speaker.add(p.print)
                for (p in last) p.speaker = speaker.id
                return revote(last.first().startMs, endMs) + mergeSimilar()
            }
        }
        return emptyList()
    }

    /** Who speaks around [ms]: the sure probe centred nearest, else the covering window. */
    private fun speakerAt(ms: Long): Int? {
        nearestCovering(probes, ms) { it.speaker > 0 && it.speaker in speakers }?.let { return it.speaker }
        return nearestCovering(windows, ms) { it.speaker in speakers }?.speaker
    }

    /**
     * The item covering [ms] whose centre is nearest, among those passing [keep]. Windows and probes
     * arrive in time order, so a binary search finds the few that cover [ms]: a sentence used to scan
     * all 1200 probes for each of its 250 ms steps, and the look-back regrouping votes every sentence.
     */
    private inline fun nearestCovering(items: ArrayDeque<Window>, ms: Long, keep: (Window) -> Boolean): Window? {
        var lo = 0; var hi = items.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (items[mid].endMs < ms) lo = mid + 1 else hi = mid }
        var best: Window? = null; var distance = Long.MAX_VALUE
        var i = lo
        while (i < items.size && items[i].startMs <= ms) {
            val w = items[i]
            val d = kotlin.math.abs((w.startMs + w.endMs) / 2 - ms)
            if (ms <= w.endMs && keep(w) && d < distance) { best = w; distance = d }
            i++
        }
        return best
    }


    /** Votes over [startMs, endMs] sampled every 250 ms with [speakerAt]. */
    private fun sampledVotes(startMs: Long, endMs: Long): Map<Int, Int> {
        val votes = HashMap<Int, Int>()
        var t = startMs + 125
        while (t < endMs) { speakerAt(t)?.let { votes[it] = (votes[it] ?: 0) + 1 }; t += 250 }
        return votes
    }

    /** The voice of most of the sentence's sure probes, else of most of its window time. */
    private fun sentenceVoice(startMs: Long, endMs: Long): Int? {
        sampledVotes(startMs, endMs).maxByOrNull { it.value }?.let { return it.key }
        val votes = HashMap<Int, Long>()
        for (w in windows) { val o = minOf(w.endMs, endMs) - maxOf(w.startMs, startMs); if (o > 0) votes[w.speaker] = (votes[w.speaker] ?: 0) + o }
        return votes.maxByOrNull { it.value }?.key
    }

    /** A finished sentence; labelled once a window covers its end (or [settle] is called). */
    @Synchronized fun addSegment(id: Long, startMs: Long, endMs: Long): List<Label> {
        pending.add(Pending(id, startMs, endMs))
        return resolve(windows.lastOrNull()?.endMs ?: Long.MIN_VALUE)
    }

    /** Label every waiting sentence with what is known now (end of speech, stop). */
    @Synchronized fun settle(): List<Label> = resolve(Long.MAX_VALUE)

    /** Label one waiting sentence with what is known now: its text is ready and should not wait for the next window. */
    @Synchronized fun labelNow(segmentId: Long): List<Label> = resolve(Long.MAX_VALUE, only = segmentId)

    private fun resolve(upTo: Long, only: Long? = null): List<Label> {
        if (pending.isEmpty() || windows.isEmpty()) return emptyList()
        val done = pending.filter { it.endMs <= upTo && (only == null || it.id == only) }
        pending.removeAll(done.toSet())
        return done.map { segment ->
            val speaker = sentenceVoice(segment.startMs, segment.endMs)
                ?: windows.minBy { minOf(kotlin.math.abs(it.endMs - segment.startMs), kotlin.math.abs(it.startMs - segment.endMs)) }.speaker
            segmentSpeaker[segment.id] = speaker
            segmentSpan[segment.id] = longArrayOf(segment.startMs, segment.endMs)
            Label(segment.id, speaker, initial = true, runs = runs(segment.startMs, segment.endMs))
        }
    }

    /** Voices inside [startMs, endMs] in order, sampled every 250 ms; a change needs 500 ms of the new voice. */
    private fun runs(startMs: Long, endMs: Long): List<Pair<Int, Long>> {
        val samples = ArrayList<Pair<Long, Int>>()
        var t = startMs + 125
        while (t < endMs) { speakerAt(t)?.let { samples += t to it }; t += 250 }
        val out = ArrayList<Pair<Int, Long>>()
        var i = 0
        while (i < samples.size) {
            val (at, who) = samples[i]
            if (out.isEmpty()) { out += who to startMs; i++; continue }
            if (who != out.last().first && i + 1 < samples.size && samples[i + 1].second == who) out += who to (at - 125).coerceIn(startMs, endMs)
            i++
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
                if (list[i].regrouped && list[j].regrouped) continue
                if (similarity(list[i].centroid ?: continue, list[j].centroid) >= MERGE_SIMILARITY) { pair = list[i] to list[j]; break@loop }
            }
            val (keep, drop) = pair ?: return changed
            changed += moveAll(drop, keep)
        }
    }

    private fun moveAll(from: Speaker, into: Speaker): List<Label> {
        for (p in from.prints) into.add(p)
        into.named = into.named || from.named
        // The holder's voice stays left out of call captions whatever joins it.
        into.ignored = if (into.id == SELF) true else into.ignored && from.ignored
        speakers.remove(from.id)
        for (w in windows) if (w.speaker == from.id) w.speaker = into.id
        // Probes too: they pick each sentence's voice and its split points. A probe still naming the
        // merged-away voice labelled later sentences with a person who no longer existed, a lane that
        // never went away; in a long call these piled up (user: 20+ people in a 10-minute call).
        for (p in probes) if (p.speaker == from.id) p.speaker = into.id
        val moved = ArrayList<Label>()
        for (entry in segmentSpeaker.entries) if (entry.value == from.id) { entry.setValue(into.id); moved += Label(entry.key, into.id) }
        return moved
    }

    @Synchronized fun merge(from: Int, into: Int): List<Label> {
        if (from == into) return emptyList()
        val source = speakers[from] ?: return emptyList()
        val target = speakers[into] ?: return emptyList()
        target.joinedByUser = true
        return moveAll(source, target)
    }

    /** "This sentence was that person": its windows teach the target voice. */
    @Synchronized fun reassign(segmentId: Long, speakerId: Int) {
        val target = speakers[speakerId] ?: return
        segmentSpeaker[segmentId] = speakerId
        val span = segmentSpan[segmentId] ?: return
        for (w in windows) if (w.startMs >= span[0] - 500 && w.endMs <= span[1] + 500) {
            w.speaker = speakerId
            w.pinned = true
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

    /** A remembered person (see [KnownVoices]): recognised from the first window, already named. */
    @Synchronized fun seedKnown(id: Int, print: FloatArray) {
        val known = speakers[id] ?: Speaker(id).also { speakers[id] = it }
        known.named = true
        known.add(normalized(print))
    }

    /** "记住这个人的声音": [from] becomes the remembered person [id]. Returns the voiceprint to keep. */
    @Synchronized fun remember(from: Int, id: Int): FloatArray? {
        if (from != id) {
            val source = speakers[from] ?: return speakers[id]?.centroid
            val target = speakers[id] ?: Speaker(id).also { speakers[id] = it }
            moveAll(source, target)
        }
        speakers[id]?.named = true
        return speakers[id]?.centroid
    }

    @Synchronized fun forget(id: Int) { speakers.remove(id) }

    @Synchronized fun forgetSelf() { speakers.remove(SELF)?.let { self -> for (w in windows) if (w.speaker == self.id) w.speaker = -1 } }
    @Synchronized fun setNamed(speakerId: Int, named: Boolean) { speakers[speakerId]?.named = named }

    @Synchronized fun reset() {
        speakers.clear(); windows.clear(); probes.clear(); pending.clear(); segmentSpeaker.clear(); segmentSpan.clear()
        nextId = 1; unmatched = null
    }

    private var nowMs = 0L
    private fun newSpeaker(print: FloatArray): Speaker = Speaker(nextId++).also { it.add(print); it.bornMs = nowMs; speakers[it.id] = it }

    /**
     * Looking back: the last [RECLUSTER_WINDOWS] two-second windows (about five minutes of speech) are
     * grouped again all at once, and every sentence in that stretch is voted again.
     *
     * Online, each window joins the closest running average; a second voice that starts close enough
     * joins the first, pulls its average over and from then on everything matches it (PC replay
     * 2026-11-06, 160 dialogues built from FLEURS speakers: 23–38% of two-person speech on the wrong
     * person, 11–16 of 20 two-person loudspeaker dialogues shown as one person). Grouping everything
     * heard so far at once does not drift. The cut is chosen per session: through a loudspeaker every
     * voice sounds alike and only a stricter cut separates them, so each cut must show clearly
     * separated groups (silhouette) before it is used; one voice alone never passes a split.
     * Ids stay stable: a new group takes the id most of its windows already had.
     */
    @Synchronized fun recluster(): List<Label> {
        val recent = windows.toList().takeLast(RECLUSTER_WINDOWS)
        val n = recent.size
        if (n < 2 * MIN_GROUP) return emptyList()
        val sim = Array(n) { FloatArray(n) }
        for (i in 0 until n) { sim[i][i] = 1f; for (j in i + 1 until n) { val v = similarity(recent[i].print, recent[j].print); sim[i][j] = v; sim[j][i] = v } }
        val merges = averageLinkage(sim)
        var chosen: IntArray? = null
        var chosenScore = Float.NEGATIVE_INFINITY
        var fallback: IntArray? = null
        for ((level, need) in RECLUSTER_LEVELS) {
            val groups = absorbSmall(cut(merges, n, level), recent)
            if (level == RECLUSTER_LEVELS.first().first) fallback = groups
            if (groups.toSet().size < 2) continue
            val score = silhouette(sim, groups) - need
            if (score >= 0f && score > chosenScore) { chosen = groups; chosenScore = score }
        }
        val groups = chosen ?: fallback ?: return emptyList()
        // Each group takes the id most of its windows had (one group per id), else a remembered voice
        // it sounds like, else a new person.
        val counts = HashMap<Int, HashMap<Int, Int>>()
        for (i in 0 until n) { val old = recent[i].speaker; if (old in speakers) counts.getOrPut(groups[i]) { HashMap() }.let { it[old] = (it[old] ?: 0) + 1 } }
        val target = HashMap<Int, Int>()
        val taken = HashSet<Int>()
        // Someone the user joined keeps every group that holds a real part of them: no split comes back.
        for ((g, m) in counts) m.filter { (old, c) -> c >= MIN_GROUP && speakers[old]?.joinedByUser == true }.maxByOrNull { it.value }?.let { target[g] = it.key; taken += it.key }
        for ((g, old, _) in counts.flatMap { (g, m) -> m.map { (old, c) -> Triple(g, old, c) } }.sortedByDescending { it.third }) {
            if (g in target) continue
            if (old !in taken || speakers[old]?.joinedByUser == true) { target[g] = old; taken += old }
        }
        val centroids = HashMap<Int, FloatArray>()
        for (g in groups.toSet()) {
            val sum = FloatArray(recent[0].print.size)
            for (i in 0 until n) if (groups[i] == g) for (k in sum.indices) sum[k] += recent[i].print[k]
            centroids[g] = normalized(sum)
        }
        for (g in groups.toSet().sorted()) {
            if (g in target) continue
            val c = centroids.getValue(g)
            val known = speakers.values.filter { (it.named || it.ignored) && it.id !in taken }.maxByOrNull { similarity(c, it.centroid) }
            target[g] = if (known != null && similarity(c, known.centroid) >= threshold) known.id else newSpeaker(c).id
            taken += target.getValue(g)
        }
        val before = recent.map { it.speaker }
        for (i in 0 until n) if (!recent[i].pinned) recent[i].speaker = target.getValue(groups[i])
        // Averages restart from their own windows only.
        for (g in groups.toSet()) {
            val sp = speakers[target.getValue(g)] ?: continue
            val own = (0 until n).filter { groups[it] == g }
            if (own.size >= MIN_GROUP) { sp.prints.clear(); for (i in own.takeLast(MAX_PRINTS)) sp.add(recent[i].print) }
            sp.regrouped = true; sp.fromProbes = false
        }
        unmatched = null
        val changed = ArrayList<Label>()
        // Automatic people whose every window went elsewhere disappear; their older sentences follow
        // the group that took most of their windows.
        val gone = before.toSet().filter { old -> old in speakers && recent.none { it.speaker == old } && automatic(old) }
        for (old in gone) {
            val into = (0 until n).filter { before[it] == old }.groupingBy { recent[it].speaker }.eachCount().maxByOrNull { it.value }?.key ?: continue
            speakers.remove(old)
            for (w in windows) if (w.speaker == old) w.speaker = into
            for (entry in segmentSpeaker.entries) if (entry.value == old) { entry.setValue(into); changed += Label(entry.key, into) }
        }
        // Probes in the stretch are judged again against the new averages, then sentences are voted again.
        val from = recent.first().startMs
        val to = recent.last().endMs
        for (p in probes) if (p.endMs >= from) {
            val ranked = speakers.values.map { it.id to similarity(p.print, it.centroid) }.sortedByDescending { it.second }
            val best = ranked.firstOrNull()
            val second = ranked.getOrNull(1)?.second ?: -1f
            p.speaker = if (best != null && best.second >= threshold - PROBE_SLACK && best.second - second >= PROBE_MARGIN) best.first else -1
        }
        for ((id, span) in segmentSpan.entries.toList()) {
            if (span[1] < from || span[0] > to) continue
            val speaker = sentenceVoice(span[0], span[1]) ?: continue
            if (segmentSpeaker[id] != speaker) { segmentSpeaker[id] = speaker; changed += Label(id, speaker) }
        }
        // A person the short probes or a stray window opened inside this stretch who holds none of its
        // windows is not a group: their sentences go to the voice they sound most like.
        val owners = recent.map { it.speaker }.toSet()
        val strays = speakers.values.filter { it.id !in owners && it.bornMs >= from && automatic(it.id) }.map { it.id }
        for (id in strays) {
            val c = speakers[id]?.centroid ?: continue
            speakers.remove(id)
            val into = speakers.values.filter { it.id in owners }.maxByOrNull { similarity(c, it.centroid) }?.id ?: continue
            for (p in probes) if (p.speaker == id) p.speaker = -1
            for (entry in segmentSpeaker.entries) if (entry.value == id) { entry.setValue(into); changed += Label(entry.key, into) }
        }
        return changed.associateBy { it.segmentId }.values.toList()
    }

    /** Every labelled sentence or piece as (start, end, speaker), for the after-the-fact re-check. */
    @Synchronized fun spans(): List<Triple<Long, Long, Int>> =
        segmentSpan.mapNotNull { (id, span) -> segmentSpeaker[id]?.let { Triple(span[0], span[1], it) } }

    /** Automatic people (not named, not ignored, not the holder or a remembered person). */
    @Synchronized fun automatic(speakerId: Int): Boolean =
        speakers[speakerId]?.let { !it.named && !it.ignored && it.id < SELF } == true

    /**
     * An automatic person who after [FRAGMENT_AGE_MS] still owns under [FRAGMENT_MS] of speech is a
     * fragment (a sentence tail, a cough, one noisy probe, one sentence through a changing phone
     * line): their sentences go to the voice they sound most like, and the person disappears.
     * PC replay 2026-10-24, six simulated 10-minute speakerphone calls: people found 2,3,3,3,3,3 →
     * 2,2,2,3,3,3 (the third is coughs and key clicks); groups of three and four kept their accuracy.
     */
    @Synchronized fun absorbFragments(): List<Label> {
        val owned = HashMap<Int, Long>()
        for ((id, span) in segmentSpan) segmentSpeaker[id]?.let { owned[it] = (owned[it] ?: 0) + span[1] - span[0] }
        if (owned.size < 2) return emptyList()
        val fragments = speakers.values.filter { sp -> sp.id < SELF && !sp.named && !sp.ignored &&
            nowMs - sp.bornMs >= FRAGMENT_AGE_MS && (owned[sp.id] ?: 0) < FRAGMENT_MS }.map { it.id }.toSet()
        if (fragments.isEmpty()) return emptyList()
        val keep = speakers.values.filter { it.id !in fragments }
        if (keep.isEmpty()) return emptyList()
        val into = fragments.associateWith { f -> keep.maxBy { similarity(speakers[f]?.centroid ?: FloatArray(0), it.centroid) }.id }
        val changed = ArrayList<Label>()
        val ordered = segmentSpan.entries.sortedBy { it.value[0] }
        for ((k, entry) in ordered.withIndex()) {
            val who = segmentSpeaker[entry.key] ?: continue
            if (who !in fragments) continue
            val near = into.getValue(who)
            segmentSpeaker[entry.key] = near
            changed += Label(entry.key, near)
        }
        for (id in fragments) speakers.remove(id)
        // Their windows go to the closest remaining voice (a window must always name someone);
        // their probes become "unsure", which only defers to the windows.
        for (w in windows) if (w.speaker in fragments) w.speaker = speakers.values.maxByOrNull { similarity(w.print, it.centroid) }?.id ?: w.speaker
        for (p in probes) if (p.speaker in fragments) p.speaker = -1
        return changed
    }

    companion object {
        /** The phone holder (see [seedSelf]); far above any automatic number. */
        const val SELF = 999
        const val PROBE_SLACK = .05f
        const val PROBE_NEW = .32f
        const val PROBE_RUN = 2
        const val FRAGMENT_MS = 3000L
        const val FRAGMENT_AGE_MS = 20_000L
        const val PROBE_MARGIN = .08f
        const val WINDOW_MS = 2000L
        const val HOP_MS = 1000L
        const val MAX_PRINTS = 30
        const val MAX_WINDOWS = 600
        // Merges relabel only remembered sentences; an older one keeps a vanished person's lane.
        const val MAX_SEGMENTS = 5000
        /** Kept at an absolute 0.35 for ignored voices (TV) when the standard threshold rose to 0.45 (2026-10-21). */
        const val IGNORED_SLACK = .10f
        /** Below this a window is someone new straight away (no second window needed). */
        const val CLEARLY_DIFFERENT = .25f
        /** Through-air same-voice segments measured 0.43–0.86 on the tablet; averaged centroids sit higher. */
        const val MERGE_SIMILARITY = .62f

        fun thresholdFor(level: Int): Float = when {
            level <= 0 -> .35f
            level == 1 -> .45f
            else -> .45f
        }

        /** Windows grouped again by [recluster]: about five minutes of speech. */
        const val RECLUSTER_WINDOWS = 300
        /** Smaller groups are not people: their windows join the closest group. */
        const val MIN_GROUP = 3
        /**
         * Cuts tried (average similarity inside a group) with the silhouette each must reach; the first
         * is used when none passes. Stricter cuts need clearer groups: one voice split at 0.5–0.55
         * scored up to 0.31, real two-person loudspeaker dialogues 0.19–0.49; a quiet room needs the
         * loose 0.35 or one person's changing voice became two (PC replay 2026-11-06).
         */
        val RECLUSTER_LEVELS = listOf(.35f to .15f, .40f to .15f, .45f to .20f, .50f to .30f, .55f to .33f)

        /**
         * Average-linkage merges of [sim] (n × n), as (kept index, absorbed index, similarity), highest
         * first. Nearest-neighbour chain: O(n²) instead of O(n³), so 300 windows take a few ms.
         */
        internal fun averageLinkage(sim: Array<FloatArray>): List<Triple<Int, Int, Float>> {
            val n = sim.size
            val d = Array(n) { sim[it].copyOf() }
            val size = IntArray(n) { 1 }
            val alive = BooleanArray(n) { true }
            var left = n
            val merges = ArrayList<Triple<Int, Int, Float>>(n)
            val chain = IntArray(n); var top = 0
            while (left > 1) {
                if (top == 0) { chain[top++] = alive.indexOfFirst { it } }
                val a = chain[top - 1]
                val previous = if (top >= 2) chain[top - 2] else -1
                var b = -1; var best = Float.NEGATIVE_INFINITY
                for (k in 0 until n) if (alive[k] && k != a) { val v = d[a][k]; if (v > best || v == best && k == previous) { best = v; b = k } }
                if (b == previous) {
                    top -= 2
                    val (keep, drop) = if (a < b) a to b else b to a
                    merges += Triple(keep, drop, best)
                    val sk = size[keep]; val sd = size[drop]
                    for (k in 0 until n) if (alive[k] && k != keep && k != drop) {
                        val v = (sk * d[keep][k] + sd * d[drop][k]) / (sk + sd)
                        d[keep][k] = v; d[k][keep] = v
                    }
                    size[keep] = sk + sd; alive[drop] = false; left--
                } else chain[top++] = b
            }
            merges.sortByDescending { it.third }
            return merges
        }

        /** Group of each item when merges down to [level] are applied (average linkage never inverts). */
        internal fun cut(merges: List<Triple<Int, Int, Float>>, n: Int, level: Float): IntArray {
            val parent = IntArray(n) { it }
            fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; var y = x; while (parent[y] != r) { val next = parent[y]; parent[y] = r; y = next }; return r }
            for ((a, b, s) in merges) { if (s < level) break; parent[find(b)] = find(a) }
            return IntArray(n) { find(it) }
        }

        private fun absorbSmall(groups: IntArray, items: List<Window>): IntArray {
            val sizes = groups.toList().groupingBy { it }.eachCount()
            val big = sizes.filter { it.value >= MIN_GROUP }.keys
            if (big.isEmpty()) return IntArray(groups.size) { groups[0] }
            if (big.size == sizes.size) return groups
            val centroids = big.associateWith { g ->
                val sum = FloatArray(items[0].print.size)
                for (i in groups.indices) if (groups[i] == g) for (k in sum.indices) sum[k] += items[i].print[k]
                normalized(sum)
            }
            return IntArray(groups.size) { i -> if (groups[i] in big) groups[i] else centroids.maxBy { similarity(items[i].print, it.value) }.key }
        }

        /** Mean silhouette with distance 1 − similarity. */
        internal fun silhouette(sim: Array<FloatArray>, groups: IntArray): Float {
            val ids = groups.distinct()
            if (ids.size < 2) return -1f
            val g = IntArray(groups.size) { ids.indexOf(groups[it]) }
            val counts = IntArray(ids.size); for (x in g) counts[x]++
            val sums = FloatArray(ids.size)
            var total = 0f
            for (i in g.indices) {
                java.util.Arrays.fill(sums, 0f)
                val row = sim[i]
                for (j in g.indices) if (j != i) sums[g[j]] += 1f - row[j]
                val own = g[i]
                if (counts[own] < 2) continue
                val a = sums[own] / (counts[own] - 1)
                var b = Float.POSITIVE_INFINITY
                for (k in ids.indices) if (k != own) b = minOf(b, sums[k] / counts[k])
                total += (b - a) / maxOf(a, b)
            }
            return total / groups.size
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
