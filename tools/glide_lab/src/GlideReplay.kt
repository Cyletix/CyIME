package com.kingzcheung.xime.glidelab

import java.io.File

/** File protocol is generated and validated by run.py; this is not an Android input path. */
fun main(args: Array<String>) {
    require(args.size == 1)
    val directory = File(args[0])
    val layoutLines = File(directory, "layout.tsv").readLines(Charsets.UTF_8)
    val header = layoutLines.first().split('\t')
    val layout = GlideLayout(header[0], header[1].toDouble(), layoutLines.drop(1).map {
        val fields = it.split('\t')
        GlideKey(fields[0].single(), fields[1].toDouble(), fields[2].toDouble())
    })
    val entries = File(directory, "lexicon.tsv").readLines(Charsets.UTF_8).map {
        val fields = it.split('\t')
        GlideLexiconEntry(fields[0], fields[1], fields[2], fields[3].toDouble())
    }
    val traces = linkedMapOf<String, MutableList<GlidePoint>>()
    File(directory, "traces.tsv").forEachLine(Charsets.UTF_8) {
        val fields = it.split('\t')
        traces.getOrPut(fields[0]) { mutableListOf() }.add(
            GlidePoint(fields[1].toDouble(), fields[2].toDouble(), fields[3].toLong()),
        )
    }
    val decoder = GlideDecoder(layout, entries)
    repeat(3) { traces.values.forEach { decoder.decode(it) } }
    for ((id, points) in traces) {
        val times = LongArray(7)
        var result = decoder.decode(points)
        repeat(times.size) { index ->
            val start = System.nanoTime()
            result = decoder.decode(points)
            times[index] = System.nanoTime() - start
        }
        times.sort()
        println("CASE\t$id\t${result.rejection?.name ?: "-"}\t${times[times.size / 2] / 1_000_000.0}")
        for (candidate in result.candidates) {
            val score = candidate.score
            println("CANDIDATE\t$id\t${candidate.entry.id}\t${score.shapeCost}\t${score.endpointCost}\t${score.priorCost}\t${score.totalCost}")
        }
    }
}
