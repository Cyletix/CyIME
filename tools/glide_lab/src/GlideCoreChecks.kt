package com.kingzcheung.xime.glidelab

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Deterministic host checks for the preparation baseline; no Android or JUnit dependency. */
fun main() {
    var passed = 0
    fun verify(name: String, body: () -> Unit) {
        try {
            body()
            passed++
        } catch (error: Throwable) {
            throw AssertionError("Glide core check failed: $name", error)
        }
    }

    val layout = checkLayout()
    val entries = listOf("hello", "help", "held", "world", "word", "test", "text", "home", "good", "food")
        .map { GlideLexiconEntry(it, it, it, 0.3) }
    val decoder = GlideDecoder(layout, entries)
    val hello = checkPath(layout, "hello")

    verify("exact path ranks correct word first") {
        val result = decoder.decode(hello)
        check(result.rejection == null && result.candidates.first().entry.id == "hello")
        check(result.layoutRevision == layout.layoutRevision)
        near(result.candidates.first().score.shapeCost, 0.0)
    }
    verify("several independent exact paths") {
        for (code in listOf("help", "world", "word", "test", "home", "good")) {
            check(decoder.decode(checkPath(layout, code)).candidates.first().entry.id == code) { code }
        }
    }
    verify("small deterministic pointer perturbations") {
        val noisy = checkPath(layout, "world", 11).mapIndexed { index, point ->
            point.copy(x = point.x + sin(index * 1.7) * 0.06, y = point.y + cos(index * 2.3) * 0.06)
        }
        check(decoder.decode(noisy).candidates.first().entry.id == "world")
    }
    verify("uniform scale and translation preserve every candidate cost") {
        val scale = 43.0
        val moved = GlideLayout("moved", scale, layout.keys.map { it.copy(x = it.x * scale + 317.0, y = it.y * scale - 86.0) })
        val transformed = hello.map { it.copy(x = it.x * scale + 317.0, y = it.y * scale - 86.0) }
        sameCandidates(decoder.decode(hello, 50), GlideDecoder(moved, entries).decode(transformed, 50))
    }
    verify("layout iteration order does not change geometry") {
        sameCandidates(
            decoder.decode(hello, 50),
            GlideDecoder(GlideLayout("reversed", 1.0, layout.keys.reversed()), entries).decode(hello, 50),
        )
    }
    verify("gesture-only translation retains its positional error") {
        val translated = hello.map { it.copy(x = it.x + 0.3) }
        val exact = decoder.decode(hello).candidates.first { it.entry.id == "hello" }
        val displaced = decoder.decode(translated).candidates.first { it.entry.id == "hello" }
        check(displaced.score.shapeCost > exact.score.shapeCost)
        check(displaced.score.endpointCost > exact.score.endpointCost)
    }
    verify("stationary pause and timestamps add no geometry evidence") {
        val paused = hello.flatMapIndexed { index, point ->
            List(if (index == hello.size / 2) 50 else 1) { point }
        }.mapIndexed { index, point -> point.copy(timeMillis = index.toLong() * 20L) }
        sameCandidates(decoder.decode(hello, 50), decoder.decode(paused, 50))
    }
    verify("uneven collinear sampling density preserves scores") {
        sameCandidates(
            decoder.decode(checkPath(layout, "hello", 1), 50),
            decoder.decode(checkPath(layout, "hello", 29), 50),
        )
    }
    verify("repeated letters stay explicitly ambiguous") {
        val repeated = GlideDecoder(layout, listOf(
            GlideLexiconEntry("a-hello", "hello", "hello"),
            GlideLexiconEntry("b-helo", "helo", "helo"),
        ))
        val result = repeated.decode(hello)
        check(repeated.entryCount == 2 && repeated.templateCount == 1)
        check(result.candidates.map { it.entry.code } == listOf("hello", "helo"))
        check(result.candidates.all { it.collapsedCode == "helo" })
        near(result.candidates[0].score.totalCost, result.candidates[1].score.totalCost)
    }
    verify("same code preserves different display texts") {
        val pinyin = GlideDecoder(layout, listOf(
            GlideLexiconEntry("a-nihao", "nihao", "你好"),
            GlideLexiconEntry("b-nihao", "nihao", "拟好"),
        ))
        val result = pinyin.decode(checkPath(layout, "nihao"))
        check(result.candidates.map { it.entry.displayText } == listOf("你好", "拟好"))
        check(pinyin.templateCount == 1)
    }
    verify("bounded prior cannot overrule a clearly better shape") {
        val biased = GlideDecoder(layout, listOf(
            GlideLexiconEntry("correct", "hello", "hello", 1.0),
            GlideLexiconEntry("popular", "help", "help", 0.0),
        ))
        val result = biased.decode(hello)
        check(result.candidates.first().entry.id == "correct")
        check(result.candidates.all { it.score.priorCost in 0.0..0.05 })
    }
    verify("prior resolves same-path ties without fake confidence") {
        val biased = GlideDecoder(layout, listOf(
            GlideLexiconEntry("a", "hello", "first", 1.0),
            GlideLexiconEntry("z", "hello", "second", 0.0),
        ))
        val result = biased.decode(hello)
        check(result.candidates.map { it.entry.id } == listOf("z", "a"))
        result.candidates.forEach {
            near(it.score.totalCost, it.score.shapeCost + it.score.endpointCost + it.score.priorCost)
        }
    }
    verify("stable id tie break is independent of insertion order") {
        val tied = listOf("z", "b", "a").map { GlideLexiconEntry(it, "hello", it) }
        val first = GlideDecoder(layout, tied).decode(hello)
        val reversed = GlideDecoder(layout, tied.reversed()).decode(hello)
        check(first.candidates.map { it.entry.id } == listOf("a", "b", "z"))
        sameCandidates(first, reversed)
    }
    verify("one point, empty gesture and stationary path are not glides") {
        rejected(decoder, emptyList(), GlideRejection.TOO_FEW_POINTS)
        rejected(decoder, hello.take(1), GlideRejection.TOO_FEW_POINTS)
        rejected(decoder, List(8) { hello.first().copy(timeMillis = it.toLong()) }, GlideRejection.PATH_TOO_SHORT)
    }
    verify("short movement is not a word request") {
        rejected(decoder, listOf(hello.first(), hello.first().copy(x = hello.first().x + 0.2, timeMillis = 1)), GlideRejection.PATH_TOO_SHORT)
    }
    verify("non-finite pointer input is rejected") {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            rejected(decoder, hello.toMutableList().also { it[1] = it[1].copy(x = bad) }, GlideRejection.NON_FINITE_COORDINATE)
            rejected(decoder, hello.toMutableList().also { it[1] = it[1].copy(y = bad) }, GlideRejection.NON_FINITE_COORDINATE)
        }
    }
    verify("time must be non-negative and non-decreasing") {
        rejected(decoder, hello.toMutableList().also { it[1] = it[1].copy(timeMillis = -1) }, GlideRejection.INVALID_TIMESTAMP)
        rejected(decoder, hello.toMutableList().also { it[2] = it[2].copy(timeMillis = 0) }, GlideRejection.INVALID_TIMESTAMP)
        check(decoder.decode(hello.map { it.copy(timeMillis = 0) }).rejection == null)
    }
    verify("point count has a hard limit") {
        val bounded = GlideDecoder(layout, entries, GlideDecoderConfig(maxInputPoints = 4))
        rejected(bounded, hello, GlideRejection.TOO_MANY_POINTS)
    }
    verify("huge finite coordinates cannot reach scoring") {
        rejected(decoder, hello.toMutableList().also { it[1] = it[1].copy(x = Double.MAX_VALUE) }, GlideRejection.COORDINATE_OUT_OF_RANGE)
    }
    verify("outside keyboard paths are rejected") {
        rejected(decoder, hello.map { it.copy(y = it.y + 5.0) }, GlideRejection.OUTSIDE_LAYOUT)
    }
    verify("absolute shape and endpoint rejection allow an empty result") {
        val strict = GlideDecoder(layout, listOf(GlideLexiconEntry("hello", "hello", "hello")), GlideDecoderConfig(maxShapeCost = 0.1, maxEndpointCost = 0.1))
        rejected(strict, checkPath(layout, "world"), GlideRejection.NO_MATCH)
        // Defaults also reject a clearly incompatible path that remains inside keyboard bounds.
        rejected(GlideDecoder(layout, listOf(GlideLexiconEntry("hello", "hello", "hello"))), checkPath(layout, "qaz"), GlideRejection.NO_MATCH)
    }
    verify("shape and endpoint cutoffs are checked independently") {
        val entry = listOf(GlideLexiconEntry("hello", "hello", "hello"))
        val endpointStrict = GlideDecoder(layout, entry, GlideDecoderConfig(maxShapeCost = 4.0, maxEndpointCost = 0.1))
        rejected(endpointStrict, hello.map { it.copy(x = it.x + 0.5) }, GlideRejection.NO_MATCH)
        val wrongInterior = listOf(hello.first(), GlidePoint(0.0, 0.0, 1), GlidePoint(0.7, 2.0, 2), hello.last())
        val shapeStrict = GlideDecoder(layout, entry, GlideDecoderConfig(maxShapeCost = 0.05, maxEndpointCost = 4.0))
        rejected(shapeStrict, wrongInterior, GlideRejection.NO_MATCH)
    }
    verify("unsupported lexicon symbols are explicitly refused") {
        for (code in listOf("Hello", "café", "hello-world", "a'b", "你好", "", "ni3hao3")) {
            invalid { GlideDecoder(layout, listOf(GlideLexiconEntry("bad", code, "text"))) }
        }
        val limited = GlideLayout("limited", 1.0, listOf(GlideKey('a', 0.0, 0.0), GlideKey('b', 1.0, 0.0)))
        invalid { GlideDecoder(limited, listOf(GlideLexiconEntry("bad", "cat", "cat"))) }
    }
    verify("lexicon count, code length and identity are bounded") {
        invalid { GlideDecoder(layout, emptyList()) }
        invalid { GlideDecoder(layout, entries, GlideDecoderConfig(maxEntries = 2)) }
        invalid { GlideDecoder(layout, listOf(GlideLexiconEntry("too-long", "a".repeat(65), "text"))) }
        invalid { GlideDecoder(layout, listOf(entries.first(), entries.first())) }
        invalid { GlideDecoder(layout, listOf(GlideLexiconEntry("", "hello", "hello"))) }
        invalid { GlideDecoder(layout, listOf(GlideLexiconEntry("empty", "hello", ""))) }
        for (prior in listOf(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            invalid { GlideDecoder(layout, listOf(entries.first().copy(priorCost = prior))) }
        }
    }
    verify("invalid configuration and output limits are refused") {
        invalid { GlideDecoderConfig(sampleCount = 15) }
        invalid { GlideDecoderConfig(sampleCount = 65) }
        invalid { GlideDecoderConfig(maxInputPoints = 8193) }
        invalid { GlideDecoderConfig(maxEntries = 10001) }
        invalid { GlideDecoderConfig(maxCodeLength = 65) }
        invalid { GlideDecoderConfig(minPathLengthKeyUnits = Double.NaN) }
        invalid { GlideDecoderConfig(maxShapeCost = Double.POSITIVE_INFINITY) }
        invalid { GlideDecoderConfig(maxEndpointCost = 0.0) }
        invalid { decoder.decode(hello, 0) }
        invalid { decoder.decode(hello, 51) }
        check(decoder.decode(hello, 1).candidates.size == 1)
    }
    verify("invalid layout snapshots are refused") {
        invalid { GlideLayout("", 1.0, layout.keys) }
        invalid { GlideLayout("zero", 0.0, layout.keys) }
        invalid { GlideLayout("nan", Double.NaN, layout.keys) }
        invalid { GlideLayout("empty", 1.0, emptyList()) }
        invalid { GlideLayout("duplicate", 1.0, listOf(layout.keys[0], layout.keys[0])) }
        invalid { GlideLayout("collision", 1.0, listOf(GlideKey('a', 0.0, 0.0), GlideKey('b', 0.0, 0.0))) }
        invalid { GlideLayout("signed-zero", 1.0, listOf(GlideKey('a', -0.0, 0.0), GlideKey('b', 0.0, 0.0))) }
        invalid { GlideLayout("nonfinite", 1.0, listOf(GlideKey('a', Double.NaN, 0.0))) }
        invalid { GlideLayout("uppercase", 1.0, listOf(GlideKey('A', 0.0, 0.0))) }
        invalid { GlideLayout("huge", 1.0, listOf(GlideKey('a', 0.0, 0.0), GlideKey('b', Double.MAX_VALUE, 0.0))) }
    }
    verify("layout and lexicon use defensive snapshots") {
        val mutableKeys = layout.keys.toMutableList()
        val snapshot = GlideLayout("snapshot", 1.0, mutableKeys)
        val mutableEntries = entries.toMutableList()
        val retained = GlideDecoder(snapshot, mutableEntries)
        mutableKeys.clear()
        mutableEntries.clear()
        val exported = snapshot.keys as MutableList<GlideKey>
        exported.clear()
        check(snapshot.keys.size == 26)
        check(retained.entryCount == entries.size)
        sameCandidates(decoder.decode(hello, 50), retained.decode(hello, 50))
    }
    verify("output mutation cannot change future decoder results") {
        val samePath = GlideDecoder(layout, listOf(
            GlideLexiconEntry("a", "hello", "hello"),
            GlideLexiconEntry("b", "helo", "helo"),
        ))
        val first = samePath.decode(hello, 50)
        val expected = first.candidates.map { it.entry.id }
        (first.candidates as MutableList<GlideCandidate>).clear()
        check(samePath.decode(hello, 50).candidates.map { it.entry.id } == expected)
    }
    verify("sample count endpoints are valid") {
        for (count in listOf(16, 64)) {
            val configured = GlideDecoder(layout, entries, GlideDecoderConfig(sampleCount = count))
            check(configured.decode(hello).candidates.first().entry.id == "hello")
        }
    }
    verify("input point list remains untouched") {
        val mutable = hello.toMutableList()
        val before = mutable.toList()
        decoder.decode(mutable)
        check(mutable == before)
    }

    println("GlideCoreChecks: $passed passed")
}

private fun checkLayout(): GlideLayout {
    val keys = listOf("qwertyuiop" to 0.0, "asdfghjkl" to 0.3, "zxcvbnm" to 0.7)
        .flatMapIndexed { row, (letters, offset) ->
            letters.mapIndexed { column, letter -> GlideKey(letter, column + offset, row.toDouble()) }
        }
    return GlideLayout("qwerty-checks-v1", 1.0, keys)
}

private fun checkPath(layout: GlideLayout, code: String, stepsPerSegment: Int = 6): List<GlidePoint> {
    val keys = layout.keys.associateBy { it.symbol }
    val letters = code.filterIndexed { index, symbol -> index == 0 || code[index - 1] != symbol }
    val path = ArrayList<GlidePoint>()
    val first = keys.getValue(letters.first())
    path.add(GlidePoint(first.x, first.y, 0L))
    for (index in 1 until letters.length) {
        val start = keys.getValue(letters[index - 1])
        val end = keys.getValue(letters[index])
        // Alternating density intentionally creates unequal temporal sampling along the path.
        val steps = stepsPerSegment * (if (index % 2 == 0) 2 else 1)
        for (step in 1..steps) {
            val fraction = step.toDouble() / steps
            path.add(GlidePoint(start.x + (end.x - start.x) * fraction, start.y + (end.y - start.y) * fraction, path.size.toLong() * 8L))
        }
    }
    return path
}

private fun sameCandidates(first: GlideDecodeResult, second: GlideDecodeResult) {
    check(first.rejection == second.rejection)
    check(first.candidates.map { it.entry.id } == second.candidates.map { it.entry.id })
    first.candidates.zip(second.candidates).forEach { (left, right) ->
        near(left.score.shapeCost, right.score.shapeCost)
        near(left.score.endpointCost, right.score.endpointCost)
        near(left.score.priorCost, right.score.priorCost)
        near(left.score.totalCost, right.score.totalCost)
    }
}

private fun rejected(decoder: GlideDecoder, points: List<GlidePoint>, reason: GlideRejection) {
    val result = decoder.decode(points)
    check(result.rejection == reason) { "Expected $reason, got ${result.rejection}" }
    check(result.candidates.isEmpty())
}

private fun near(actual: Double, expected: Double) {
    check(abs(actual - expected) < 1e-10) { "Expected $expected, got $actual" }
}

private fun invalid(body: () -> Unit) {
    var refused = false
    try {
        body()
    } catch (_: IllegalArgumentException) {
        refused = true
    }
    check(refused) { "Expected IllegalArgumentException" }
}
