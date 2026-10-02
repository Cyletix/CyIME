package com.kingzcheung.xime.glidelab

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A captured pointer sample. Time is non-negative and non-decreasing within a gesture. */
data class GlidePoint(val x: Double, val y: Double, val timeMillis: Long)

/** Coordinates and [GlideLayout.keyUnit] must use the same coordinate space. */
data class GlideKey(val symbol: Char, val x: Double, val y: Double)

private const val MAX_NORMALIZED_COORDINATE = 1024.0
private const val LAYOUT_MARGIN = 2.0
private const val ENDPOINT_WEIGHT = 0.35
private const val PRIOR_WEIGHT = 0.05

internal data class GlideVector(val x: Double, val y: Double)

/**
 * Immutable geometry snapshot for lowercase Latin letter codes. [keyUnit] is one shared physical
 * key-width scale, not independently fitted x/y scales. Translating/scaling both layout and gesture
 * therefore preserves scores without erasing the gesture's position on the keyboard.
 *
 * Callers must assign a new [layoutRevision] whenever geometry or its coordinate space changes.
 * This offline decoder does not capture UI geometry or manage editor/profile sessions.
 */
class GlideLayout(
    val layoutRevision: String,
    val keyUnit: Double,
    keys: Collection<GlideKey>,
) {
    private val keySnapshot: List<GlideKey>
    private val normalizedKeys: Map<Char, GlideVector>
    private val originX: Double
    private val originY: Double
    private val minX: Double
    private val maxX: Double
    private val minY: Double
    private val maxY: Double

    /** Returns a detached copy, including when a caller casts the result to MutableList. */
    val keys: List<GlideKey> get() = keySnapshot.toList()

    init {
        require(layoutRevision.isNotBlank() && layoutRevision.length <= 128) {
            "layoutRevision must contain 1..128 characters and not be blank"
        }
        require(keyUnit.isFinite() && keyUnit > 0.0) { "keyUnit must be finite and positive" }
        require(keys.size in 1..26) { "Layout must contain 1..26 Latin letter keys" }
        keySnapshot = keys.toList()
        require(keySnapshot.all { it.symbol in 'a'..'z' && it.x.isFinite() && it.y.isFinite() }) {
            "Keys must have lowercase ASCII a-z symbols and finite coordinates"
        }
        require(keySnapshot.map { it.symbol }.toSet().size == keySnapshot.size) {
            "Layout symbols must be unique"
        }
        // A symbol-based origin makes scores independent of collection iteration order.
        val origin = keySnapshot.minBy { it.symbol }
        originX = origin.x
        originY = origin.y
        normalizedKeys = keySnapshot.associate { key ->
            val position = normalize(key.x, key.y)
            require(position.isBounded()) { "Layout coordinates exceed the supported key-unit range" }
            key.symbol to position
        }
        val centers = normalizedKeys.values.toList()
        require(centers.indices.all { index ->
            (0 until index).none { earlier ->
                centers[index].x == centers[earlier].x && centers[index].y == centers[earlier].y
            }
        }) { "Layout key centers must be distinct at the selected keyUnit scale" }
        minX = normalizedKeys.values.minOf { it.x }
        maxX = normalizedKeys.values.maxOf { it.x }
        minY = normalizedKeys.values.minOf { it.y }
        maxY = normalizedKeys.values.maxOf { it.y }
    }

    internal fun key(symbol: Char): GlideVector? = normalizedKeys[symbol]

    internal fun normalize(x: Double, y: Double): GlideVector =
        GlideVector((x - originX) / keyUnit, (y - originY) / keyUnit)

    internal fun contains(point: GlideVector): Boolean =
        point.x >= minX - LAYOUT_MARGIN && point.x <= maxX + LAYOUT_MARGIN &&
            point.y >= minY - LAYOUT_MARGIN && point.y <= maxY + LAYOUT_MARGIN
}

/**
 * An explicit code-to-text mapping. English words and Pinyin-to-Chinese fixture entries can share
 * this representation; no language, input scheme, Rime schema or transliteration is inferred.
 * [priorCost] is a caller-supplied normalized cost in 0..1 (smaller is preferred), not a probability.
 */
data class GlideLexiconEntry(
    val id: String,
    val code: String,
    val displayText: String,
    val priorCost: Double = 0.0,
)

/** Resource ceilings are deliberately small enough for a host-side exhaustive baseline. */
data class GlideDecoderConfig(
    val sampleCount: Int = 32,
    val maxInputPoints: Int = 2048,
    val maxEntries: Int = 10000,
    val maxCodeLength: Int = 64,
    val minPathLengthKeyUnits: Double = 0.75,
    val maxShapeCost: Double = 1.25,
    val maxEndpointCost: Double = 1.0,
) {
    init {
        require(sampleCount in 16..64) { "sampleCount must be in 16..64" }
        require(maxInputPoints in 2..8192) { "maxInputPoints must be in 2..8192" }
        require(maxEntries in 1..10000) { "maxEntries must be in 1..10000" }
        require(maxCodeLength in 1..64) { "maxCodeLength must be in 1..64" }
        require(minPathLengthKeyUnits.isFinite() && minPathLengthKeyUnits in 0.1..10.0) {
            "minPathLengthKeyUnits must be finite and in 0.1..10"
        }
        require(maxShapeCost.isFinite() && maxShapeCost in 0.05..4.0) {
            "maxShapeCost must be finite and in 0.05..4"
        }
        require(maxEndpointCost.isFinite() && maxEndpointCost in 0.05..4.0) {
            "maxEndpointCost must be finite and in 0.05..4"
        }
    }
}

enum class GlideRejection {
    TOO_FEW_POINTS,
    TOO_MANY_POINTS,
    NON_FINITE_COORDINATE,
    INVALID_TIMESTAMP,
    COORDINATE_OUT_OF_RANGE,
    OUTSIDE_LAYOUT,
    PATH_TOO_SHORT,
    NO_MATCH,
}

/**
 * Additive cost contributions, lower is better. Shape is DTW accumulated Euclidean distance divided
 * by sampleCount; endpoint is mean endpoint distance * 0.35; prior is entry.priorCost * 0.05.
 * These are diagnostic costs, never calibrated confidence/probability values.
 */
data class GlideScore(
    val shapeCost: Double,
    val endpointCost: Double,
    val priorCost: Double,
    val totalCost: Double,
)

data class GlideCandidate(
    val entry: GlideLexiconEntry,
    val score: GlideScore,
    val collapsedCode: String,
)

data class GlideDecodeResult(
    val layoutRevision: String,
    val candidates: List<GlideCandidate>,
    val rejection: GlideRejection? = null,
)

/**
 * Experimental, dependency-free lexical path decoder, with no Android/Rime/LM integration.
 * Layout/lexicon snapshots and sampled templates are retained; decoding uses only local scratch
 * state. Work is bounded by maxInputPoints and maxEntries * sampleCount^2, with a quarter-length
 * DTW band. All entries are scored; there is no production-scale trie/beam or learned model here.
 *
 * Consecutive repeated letters collapse ("hello" and "helo" have identical geometry). Entries
 * with identical codes/paths remain separate candidates; spelling/context must resolve ambiguity
 * in a later layer. Non-Latin, uppercase, punctuation and missing-key codes are explicitly refused.
 *
 * Shape/endpoint cutoffs are uncalibrated, permissive preparation defaults. They provide basic
 * absolute rejection, not an open-vocabulary detector or evidence of real-user accuracy.
 */
class GlideDecoder(
    val layout: GlideLayout,
    entries: Collection<GlideLexiconEntry>,
    val config: GlideDecoderConfig = GlideDecoderConfig(),
) {
    private data class Template(val code: String, val samples: List<GlideVector>)
    private data class PreparedEntry(val entry: GlideLexiconEntry, val template: Template)

    private val prepared: List<PreparedEntry>
    val entryCount: Int
    val templateCount: Int

    init {
        require(entries.size in 1..config.maxEntries) { "Lexicon must contain 1..maxEntries entries" }
        val snapshot = entries.toList()
        val ids = HashSet<String>()
        val templates = LinkedHashMap<String, Template>()
        prepared = snapshot.map { entry ->
            require(entry.id.isNotBlank() && entry.id.length <= 128 && ids.add(entry.id)) {
                "Lexicon ids must be unique, non-blank and at most 128 characters"
            }
            require(entry.displayText.isNotEmpty() && entry.displayText.length <= 256) {
                "Entry ${entry.id}: displayText must contain 1..256 characters"
            }
            require(entry.code.length in 1..config.maxCodeLength && entry.code.all { it in 'a'..'z' }) {
                "Entry ${entry.id}: code must contain 1..maxCodeLength lowercase ASCII a-z letters"
            }
            require(entry.code.all { layout.key(it) != null }) {
                "Entry ${entry.id}: code contains a symbol absent from the layout"
            }
            require(entry.priorCost.isFinite() && entry.priorCost in 0.0..1.0) {
                "Entry ${entry.id}: priorCost must be finite and in 0..1"
            }
            val code = collapseRepeats(entry.code)
            val template = templates.getOrPut(code) {
                Template(code, resample(code.map { layout.key(it)!! }, config.sampleCount))
            }
            PreparedEntry(entry, template)
        }
        entryCount = prepared.size
        templateCount = templates.size
    }

    /**
     * Rejections contain no candidates. A point list is copied before validation/use. Timestamps are
     * validated but not scored: stationary pauses and extra collinear samples cannot add evidence.
     * [limit] must be in 1..50. Equal scores use id order, independent of lexicon insertion order.
     */
    fun decode(points: List<GlidePoint>, limit: Int = 5): GlideDecodeResult {
        require(limit in 1..50) { "limit must be in 1..50" }
        if (points.size < 2) return rejected(GlideRejection.TOO_FEW_POINTS)
        if (points.size > config.maxInputPoints) return rejected(GlideRejection.TOO_MANY_POINTS)
        val snapshot = points.toList()
        val normalized = ArrayList<GlideVector>(snapshot.size)
        var previousTime = -1L
        for (point in snapshot) {
            if (!point.x.isFinite() || !point.y.isFinite()) {
                return rejected(GlideRejection.NON_FINITE_COORDINATE)
            }
            if (point.timeMillis < 0L || point.timeMillis < previousTime) {
                return rejected(GlideRejection.INVALID_TIMESTAMP)
            }
            previousTime = point.timeMillis
            val position = layout.normalize(point.x, point.y)
            if (!position.isBounded()) return rejected(GlideRejection.COORDINATE_OUT_OF_RANGE)
            if (!layout.contains(position)) return rejected(GlideRejection.OUTSIDE_LAYOUT)
            normalized.add(position)
        }
        if (pathLength(normalized) < config.minPathLengthKeyUnits) {
            return rejected(GlideRejection.PATH_TOO_SHORT)
        }
        val samples = resample(normalized, config.sampleCount)
        // Same-path entries share geometry computation, while retaining their own prior/display.
        val geometry = HashMap<String, Pair<Double, Double>>()
        val candidates = ArrayList<GlideCandidate>()
        for ((entry, template) in prepared) {
            val (shape, endpoint) = geometry.getOrPut(template.code) {
                dtw(samples, template.samples) to
                    ((distance(samples.first(), template.samples.first()) +
                        distance(samples.last(), template.samples.last())) / 2.0)
            }
            if (shape > config.maxShapeCost || endpoint > config.maxEndpointCost) continue
            val endpointCost = endpoint * ENDPOINT_WEIGHT
            val priorCost = entry.priorCost * PRIOR_WEIGHT
            candidates.add(
                GlideCandidate(
                    entry,
                    GlideScore(shape, endpointCost, priorCost, shape + endpointCost + priorCost),
                    template.code,
                ),
            )
        }
        if (candidates.isEmpty()) return rejected(GlideRejection.NO_MATCH)
        val ordered = candidates.sortedWith(
            compareBy<GlideCandidate> { it.score.totalCost }
                .thenBy { it.score.shapeCost }
                .thenBy { it.score.endpointCost }
                .thenBy { it.entry.id },
        )
        return GlideDecodeResult(layout.layoutRevision, ordered.take(limit))
    }

    private fun rejected(reason: GlideRejection) =
        GlideDecodeResult(layout.layoutRevision, emptyList(), reason)
}

private fun GlideVector.isBounded(): Boolean =
    x.isFinite() && y.isFinite() &&
        abs(x) <= MAX_NORMALIZED_COORDINATE && abs(y) <= MAX_NORMALIZED_COORDINATE

private fun collapseRepeats(code: String): String = buildString {
    for (symbol in code) if (isEmpty() || last() != symbol) append(symbol)
}

private fun distance(a: GlideVector, b: GlideVector): Double = hypot(a.x - b.x, a.y - b.y)

private fun pathLength(points: List<GlideVector>): Double {
    var length = 0.0
    for (index in 1 until points.size) length += distance(points[index - 1], points[index])
    return length
}

/** Samples the captured polyline by travel distance; stationary pauses receive no extra samples. */
private fun resample(points: List<GlideVector>, count: Int): List<GlideVector> {
    val cumulative = DoubleArray(points.size)
    for (index in 1 until points.size) {
        cumulative[index] = cumulative[index - 1] + distance(points[index - 1], points[index])
    }
    val total = cumulative.last()
    if (total == 0.0) return List(count) { points.first() }
    var segmentEnd = 1
    return List(count) { sample ->
        when (sample) {
            0 -> points.first()
            count - 1 -> points.last()
            else -> {
                val target = total * sample / (count - 1)
                while (segmentEnd < points.lastIndex &&
                    (cumulative[segmentEnd] < target ||
                        cumulative[segmentEnd] == cumulative[segmentEnd - 1])
                ) {
                    segmentEnd++
                }
                val start = points[segmentEnd - 1]
                val end = points[segmentEnd]
                val segmentLength = cumulative[segmentEnd] - cumulative[segmentEnd - 1]
                val fraction = if (segmentLength == 0.0) 0.0 else
                    ((target - cumulative[segmentEnd - 1]) / segmentLength).coerceIn(0.0, 1.0)
                GlideVector(
                    start.x + fraction * (end.x - start.x),
                    start.y + fraction * (end.y - start.y),
                )
            }
        }
    }
}

/** Endpoint-constrained DTW, bounded warping, two scratch rows, fixed-sample normalization. */
private fun dtw(query: List<GlideVector>, template: List<GlideVector>): Double {
    val count = query.size
    val band = max(1, count / 4)
    var previous = DoubleArray(count + 1) { Double.POSITIVE_INFINITY }
    var current = DoubleArray(count + 1) { Double.POSITIVE_INFINITY }
    previous[0] = 0.0
    for (i in 1..count) {
        current.fill(Double.POSITIVE_INFINITY)
        for (j in max(1, i - band)..min(count, i + band)) {
            current[j] = distance(query[i - 1], template[j - 1]) +
                min(previous[j - 1], min(previous[j], current[j - 1]))
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[count] / count
}
