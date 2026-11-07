package com.kingzcheung.xime.service

/** Tracks glide syllables while later typed code is appended and removed. */
internal class ChineseGlideDeleteHistory {
    data class State(
        val input: String, val caret: Int, val confirmed: Int, val confirmedText: String,
        val schema: String, val session: Long, val target: InputTarget, val revision: Long,
    ) {
        fun sameComposition(other: State): Boolean = copy(revision = other.revision) == other
    }

    data class Entry(val before: State, val after: State, val ends: List<Int>, val separatorLength: Int,
        val trailingInput: Boolean = false) {
        val removeStart: Int get() = if (ends.size == 1) before.caret
            else before.caret + separatorLength + ends[ends.lastIndex - 1]
        val remainingInput: String get() = after.input.removeRange(removeStart, after.caret)
    }
    private val entries = ArrayDeque<Entry>()

    data class TypedTail(val before: State, val code: String)

    /** When a glide follows typing, include only the unconfirmed typed suffix. */
    fun typedTail(current: State): TypedTail? {
        val previous = entries.lastOrNull()?.after
        if (previous != null && previous.sameComposition(current)) return null
        val base = if (previous != null && hasTrailingInput(previous, current)) previous else {
            if (current.caret !in current.confirmed..current.input.length) return null
            current.copy(input = current.input.removeRange(current.confirmed, current.caret), caret = current.confirmed)
        }
        if (current.caret <= base.caret) return null
        val inserted = current.input.substring(base.caret, current.caret)
        val code = inserted.removePrefix("'")
        return if (code.isEmpty()) null else TypedTail(base, code)
    }

    private fun hasTrailingInput(base: State, current: State): Boolean {
        if (current.copy(input = base.input, caret = base.caret, revision = base.revision) != base) return false
        val extra = current.input.length - base.input.length
        return extra > 0 && current.caret == base.caret + extra &&
            current.input.startsWith(base.input.take(base.caret)) &&
            current.input.drop(current.caret) == base.input.drop(base.caret)
    }

    private fun replaceLast(entry: Entry) { entries.removeLast(); entries.addLast(entry) }

    fun record(before: State?, after: State?, code: String, syllables: List<String>?) {
        if (before == null || after == null || before.session != after.session || before.target != after.target ||
            before.schema != after.schema || before.confirmed != after.confirmed || before.confirmedText != after.confirmedText ||
            before.caret !in before.confirmed..before.input.length || syllables.isNullOrEmpty() ||
            syllables.any { it.isEmpty() } || syllables.joinToString("") != code) {
            entries.clear(); return
        }
        val prefix = before.input.take(before.caret)
        val suffix = before.input.drop(before.caret)
        val inserted = when {
            after.input == prefix + code + suffix -> code
            after.input == prefix + "'" + code + suffix -> "'" + code
            else -> { entries.clear(); return }
        }
        if (after.caret != before.caret + inserted.length) { entries.clear(); return }
        entries.lastOrNull()?.let { previous ->
            when {
                previous.after == before -> Unit
                hasTrailingInput(previous.after, before) -> replaceLast(previous.copy(trailingInput = true))
                previous.trailingInput && previous.after.sameComposition(before) -> replaceLast(previous.copy(after = before, trailingInput = false))
                else -> entries.clear()
            }
        }
        var offset = 0
        val ends = syllables.map { offset += it.length; offset }
        entries.addLast(Entry(before, after, ends, inserted.length - code.length))
    }

    fun last(current: State?): Entry? {
        val entry = entries.lastOrNull() ?: return null
        if (current == entry.after) return entry
        if (current != null && hasTrailingInput(entry.after, current)) {
            if (!entry.trailingInput) replaceLast(entry.copy(trailingInput = true))
            return null
        }
        if (current != null && entry.trailingInput && entry.after.sameComposition(current)) {
            val resumed = entry.copy(after = current, trailingInput = false)
            replaceLast(resumed)
            return resumed
        }
        entries.clear()
        return null
    }

    fun removed(restored: State?) {
        val last = entries.removeLastOrNull() ?: return
        val expected = last.after.copy(input = last.remainingInput, caret = last.removeStart)
        if (restored == null || !expected.sameComposition(restored)) { entries.clear(); return }
        if (last.ends.size > 1) entries.addLast(last.copy(after = restored, ends = last.ends.dropLast(1)))
        else refresh(restored)
    }

    fun refresh(restored: State?) {
        val previous = entries.lastOrNull() ?: return
        when {
            restored == null -> entries.clear()
            previous.after.sameComposition(restored) -> replaceLast(previous.copy(after = restored, trailingInput = false))
            hasTrailingInput(previous.after, restored) -> replaceLast(previous.copy(trailingInput = true))
            else -> entries.clear()
        }
    }
}
