package com.kingzcheung.xime.handwriting

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 英文手写整段回放：训练模式导出的英文词句，按键盘里的做法切成「停顿一次识别一段」，每段用 App 同一份切分
 * （OverlappedHandwritingRecognizer，参数同 HandwritingKeyboardLayout）逐个字母识别再拼起来，和原文比字母错误率。
 * 单字母打分由电脑上的同源 ONNX 脚本给出（每行一个 JSON 请求）。
 * 只在设置了 CYIME_HW_REPLAY=<python>;<打分脚本>;<导出目录>;<模式 och|lat|latb>;<停顿毫秒> 时运行，平时跳过。
 */
class EnglishHandwritingReplayBench {
    private class Scorer(python: String, script: String) {
        private val proc = ProcessBuilder(python, script).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        private val out = proc.outputStream.bufferedWriter(Charsets.UTF_8)
        private val inp = proc.inputStream.bufferedReader(Charsets.UTF_8)
        private val cache = HashMap<String, List<HandwritingCandidate>>()

        fun score(mode: String, strokes: List<List<Pair<Float, Float>>>, k: Int): List<HandwritingCandidate> {
            val s = JSONArray().also { a -> strokes.forEach { st -> a.put(JSONArray().also { b -> st.forEach { (x, y) -> b.put(JSONArray().put(x).put(y)) } }) } }
            val req = JSONObject().put("m", mode).put("s", s).put("k", k).toString()
            return cache.getOrPut(req) {
                out.write(req); out.newLine(); out.flush()
                val arr = JSONArray(inp.readLine())
                (0 until arr.length()).map { arr.getJSONArray(it).let { p -> HandwritingCandidate(p.getString(0), p.getDouble(1).toFloat()) } }
            }
        }

        fun close() { out.close(); proc.waitFor() }
    }

    private class Sample(val label: String, val strokes: List<List<Pair<Float, Float>>>, val times: List<Pair<Long, Long>>)

    private fun load(dir: File): List<Sample> {
        val seen = HashSet<String>()
        val out = mutableListOf<Sample>()
        dir.listFiles { f -> f.name.endsWith(".jsonl") }!!.sortedBy { it.name }.forEach { f ->
            f.forEachLine(Charsets.UTF_8) { line ->
                if (line.isBlank()) return@forEachLine
                val o = JSONObject(line)
                if (o.optString("type") != (System.getenv("CYIME_HW_REPLAY_TYPE") ?: "english") || !seen.add(o.getString("id"))) return@forEachLine
                val strokes = mutableListOf<List<Pair<Float, Float>>>()
                val times = mutableListOf<Pair<Long, Long>>()
                val arr = o.getJSONArray("strokes")
                for (i in 0 until arr.length()) {
                    val pts = arr.getJSONObject(i).getJSONArray("points")
                    if (pts.length() == 0) continue
                    strokes += (0 until pts.length()).map { j -> pts.getJSONArray(j).let { p -> p.getDouble(0).toFloat() to p.getDouble(1).toFloat() } }
                    times += pts.getJSONArray(0).getLong(2) to pts.getJSONArray(pts.length() - 1).getLong(2)
                }
                out += Sample(o.getString("label"), strokes, times)
            }
        }
        return out
    }

    private fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val cur = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = cur
            }
        }
        return d[b.length]
    }

    @Test fun replay() {
        val spec = System.getenv("CYIME_HW_REPLAY")
        assumeTrue(!spec.isNullOrEmpty())
        val (python, script, dir, mode, pause) = spec!!.split(';')
        val pauseMs = pause.toLong()
        val scorer = Scorer(python, script)
        var errors = 0
        var letters = 0
        var exact = 0
        val samples = load(File(dir))
        for (s in samples) {
            val gaps = s.times.indices.map { i -> if (i == 0) 0L else s.times[i].first - s.times[i - 1].second }
            // 停顿超过设置的时长就识别一次已写的笔迹（HandwritingInputSession），接着写的笔画另起一段
            val chunks = mutableListOf<IntRange>()
            var start = 0
            for (i in 1 until s.strokes.size) if (gaps[i] > pauseMs) { chunks += start until i; start = i }
            chunks += start until s.strokes.size
            val text = StringBuilder()
            for (range in chunks) {
                val strokes = s.strokes.slice(range)
                val chunkGaps = range.map { if (it == range.first) 0L else gaps[it] * OverlappedHandwritingRecognizer.GAP_SPLIT_MS / pauseMs }
                val recognizer = OverlappedHandwritingRecognizer(maxSegments = 12, maxStrokesPerSegment = 4, sideBySide = true, lettersApart = true,
                    letterCost = if (mode == "och") OverlappedHandwritingRecognizer.MODEL_LETTER_COST else OverlappedHandwritingRecognizer.TEMPLATE_LETTER_COST)
                val segments = recognizer.recognize(strokes, chunkGaps) { seg, k ->
                    if (mode == "och") HandwritingLanguages.latinCandidates(scorer.score("och", seg, k), k)
                    else scorer.score(mode, seg, k).map { HandwritingCandidate(it.char, (2f * it.score - 1f).coerceAtLeast(0.0001f)) }
                }.segments
                text.append(segments.joinToString("") { it.candidates.firstOrNull()?.char.orEmpty() })
                if (System.getenv("CYIME_HW_REPLAY_DEBUG") != null) for (seg in segments) println("HW_SEG ${s.label.take(12)} " +
                    "[${range.first + seg.startStroke}+${seg.strokeCount}] " + seg.candidates.take(4).joinToString(" ") { "%s:%.2f".format(it.char, it.score) })
            }
            val target = s.label.filterNot { it == ' ' }
            val got = text.toString()
            val e = distance(got, target)
            errors += e; letters += target.length
            if (got == target) exact++
            println("HW_REPLAY $mode pause=$pauseMs chunks=${chunks.size} strokes=${s.strokes.size} err=$e/${target.length}  $target -> $got")
        }
        scorer.close()
        println("HW_REPLAY_SUMMARY mode=$mode pause=$pauseMs samples=${samples.size} exact=$exact CER=%.1f%%".format(100.0 * errors / letters))
    }
}
