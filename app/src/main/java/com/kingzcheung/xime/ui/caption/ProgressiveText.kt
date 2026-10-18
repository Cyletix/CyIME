package com.kingzcheung.xime.ui.caption

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** New characters fade in over this long (ASRInput: 140 ms). */
private const val FADE_MS = 140L
/** A character corrected in place glows in the accent colour and settles back over this long (ASRInput: 650 ms). */
private const val CORRECTION_MS = 650L
private const val MIN_RATE = 14f // characters per second, ASRInput's automatic default
private const val MAX_RATE = 40f
/** Text older than this is shown whole: a reader coming back to the page is not made to wait. */
private const val STALE_MS = 15_000L

/**
 * How much of one sentence is on screen. It belongs to the sentence, not to the block drawing it,
 * so a sentence that moves to another speaker's row or into a merged block keeps its progress.
 * Main thread only.
 */
class Reveal {
    private var text by mutableStateOf("")
    /** Characters of the current text already on screen. */
    var shown by mutableIntStateOf(0)
        private set
    private var revealedAt = LongArray(0)
    private var correctedAt = LongArray(0)

    val backlog: Int get() = text.length - shown

    /**
     * New text for the sentence. Characters already on screen stay where they are; the ones the
     * recognizer changed or inserted among them stay visible and glow, the rest wait their turn.
     */
    fun update(next: String, now: Long) {
        if (next == text) return
        val old = text.substring(0, shown)
        val from = IntArray(next.length) { -1 } // index in old of each kept character
        if (old.isNotEmpty() && next.isNotEmpty()) {
            val lcs = Array(old.length + 1) { IntArray(next.length + 1) }
            for (i in old.length - 1 downTo 0) for (j in next.length - 1 downTo 0)
                lcs[i][j] = if (old[i] == next[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            var i = 0; var j = 0
            while (i < old.length && j < next.length) when {
                old[i] == next[j] -> { from[j] = i; i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> i++
                else -> j++
            }
        }
        val lastKept = from.indices.lastOrNull { from[it] >= 0 } ?: -1
        val visible = if (old.isEmpty()) 0 else maxOf(lastKept + 1, minOf(old.length, next.length))
        val reveal = LongArray(next.length)
        val correct = LongArray(next.length)
        for (j in 0 until visible) {
            val i = from[j]
            if (i >= 0) { reveal[j] = revealedAt[i]; correct[j] = correctedAt[i] }
            else { reveal[j] = 1L; correct[j] = now }
        }
        text = next
        revealedAt = reveal
        correctedAt = correct
        shown = visible
    }

    /** Puts the whole text on screen at once (the user's own replies, old lines). */
    fun showAll() {
        for (i in shown until text.length) revealedAt[i] = 1L
        shown = text.length
    }

    fun step(now: Long) {
        if (shown >= text.length) return
        revealedAt[shown] = now
        shown++
    }

    fun alpha(index: Int, now: Long): Float =
        if (index >= revealedAt.size) 1f else ((now - revealedAt[index]).toFloat() / FADE_MS).coerceIn(0f, 1f)

    fun glow(index: Int, now: Long): Float =
        if (index >= correctedAt.size || correctedAt[index] == 0L) 0f
        else 1f - ((now - correctedAt[index]).toFloat() / CORRECTION_MS).coerceIn(0f, 1f)

    fun animating(now: Long): Boolean =
        revealedAt.any { it > 1L && now - it < FADE_MS } || correctedAt.any { it > 0L && now - it < CORRECTION_MS }
}

/**
 * Writes the captions out like the desktop ASRInput captions: one character after another in the
 * order they were said, at a reading pace (14 per second) that speeds up only when it falls behind.
 */
@Composable
internal fun RevealTicker(controller: CaptionController) {
    LaunchedEffect(controller) {
        while (true) {
            snapshotFlow { controller.segments.any { it.reveal.backlog > 0 } }.first { it }
            val now = System.currentTimeMillis()
            var next: SegmentUi? = null
            var backlog = 0
            for (line in controller.segments) {
                if (line.reveal.backlog <= 0) continue
                val ignored = controller.speakers.any { it.id == line.speakerId && it.ignored }
                if (ignored || (line.confirmed && now - (line.wallMs + line.durationMs) > STALE_MS)) { line.reveal.showAll(); continue }
                if (next == null) next = line
                backlog += line.reveal.backlog
            }
            val line = next ?: continue
            delay((1000f / (backlog * 4f).coerceIn(MIN_RATE, MAX_RATE)).toLong())
            line.reveal.step(System.currentTimeMillis())
        }
    }
}

/** A run of sentences drawn with their reveal progress; [suffix] follows once all of it is out. */
@Composable
internal fun ProgressiveText(segments: List<SegmentUi>, fontSize: TextUnit, lineHeight: TextUnit, color: Color, modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE, softWrap: Boolean = true, suffix: String = "") {
    val accent = MaterialTheme.colorScheme.primary
    var frame by remember { mutableLongStateOf(0L) }
    val shown = segments.sumOf { it.reveal.shown }
    val text = segments.joinToString("") { it.text }
    // Repaint every frame only while something is still fading in or glowing.
    LaunchedEffect(shown, text) {
        while (segments.any { it.reveal.animating(System.currentTimeMillis()) }) withFrameMillis { frame = it }
    }
    @Suppress("UNUSED_VARIABLE") val tick = frame
    val now = System.currentTimeMillis()
    var complete = true
    val styled = buildAnnotatedString {
        for (line in segments) {
            val reveal = line.reveal
            val count = minOf(reveal.shown, line.text.length)
            for (i in 0 until count) {
                val c = lerp(color, accent, reveal.glow(i, now)).copy(alpha = color.alpha * reveal.alpha(i, now))
                withStyle(SpanStyle(color = c)) { append(line.text[i]) }
            }
            // Keep reading order: nothing from a later sentence before this one is out.
            if (count < line.text.length) { complete = false; break }
        }
        if (complete) append(suffix)
    }
    Text(styled, fontSize = fontSize, lineHeight = lineHeight, maxLines = maxLines, softWrap = softWrap,
        overflow = TextOverflow.Clip, modifier = modifier, inlineContent = emptyMap<String, InlineTextContent>())
}
