package com.kingzcheung.xime.association

import org.json.JSONArray
import org.json.JSONObject

data class LearnedSequence(val tokens: List<String>, val count: Int)
data class PersonalContinuation(val context: String, val text: String, val count: Int)

/** Portable personal data. A corpus prior is distinct from actual keyboard observations. */
data class PersonalLearningData(
    val bigrams: List<LearnedSequence> = emptyList(),
    val trigrams: List<LearnedSequence> = emptyList(),
    val recentInputs: List<String> = emptyList(),
    val profileName: String = "",
    val continuations: List<PersonalContinuation> = emptyList(),
) {
    val uniqueSequences get() = bigrams.size + trigrams.size
    val observations get() = (bigrams + trigrams).sumOf { it.count.toLong() }

    fun encode(): String {
        fun sequences(rows: List<LearnedSequence>) = JSONArray().apply {
            rows.forEach { put(JSONObject().put("tokens", JSONArray(it.tokens)).put("count", it.count)) }
        }
        return JSONObject().put("format", "cyime-personal-learning").put("version", 1)
            .put("bigrams", sequences(bigrams)).put("trigrams", sequences(trigrams))
            .put("recentInputs", JSONArray(recentInputs))
            .put("profile", JSONObject().put("name", profileName).put("entries", JSONArray().apply {
                continuations.forEach { put(JSONObject().put("context", it.context).put("text", it.text).put("count", it.count)) }
            })).toString()
    }

    /**
     * 本机学习数据的存储上限：超过 [STORED_HIGH_WATER] 条时只留次数最多的 [STORED_KEPT] 条，
     * 任一次数超过 [MAX_COUNT] 时全部减半。这样保存的文件永远能被 [decode] 读回，
     * 不会因长期使用越界而让学习静默停止。没有越界时原样返回同一对象。
     */
    fun boundedForStorage(): PersonalLearningData {
        fun bound(rows: List<LearnedSequence>): List<LearnedSequence> {
            var kept = if (rows.size > STORED_HIGH_WATER) rows.sortedByDescending { it.count }.take(STORED_KEPT) else rows
            while (kept.any { it.count > MAX_COUNT }) kept = kept.map { it.copy(count = maxOf(1, it.count / 2)) }
            return kept
        }
        val b = bound(bigrams)
        val t = bound(trigrams)
        val r = if (recentInputs.size > MAX_RECENT) recentInputs.takeLast(MAX_RECENT) else recentInputs
        return if (b === bigrams && t === trigrams && r === recentInputs) this
        else copy(bigrams = b, trigrams = t, recentInputs = r)
    }

    companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
        const val MAX_SEQUENCES = 100_000
        const val MAX_COUNT = 1_000_000
        const val MAX_RECENT = 100
        const val STORED_HIGH_WATER = MAX_SEQUENCES
        const val STORED_KEPT = 90_000

        /**
         * [storedLocally] 读本机自己的学习文件：条目数、次数和最近输入超限时先读入、再由
         * [boundedForStorage] 裁剪；格式错误仍然拒绝，避免覆盖损坏前的数据。导入外部文件保持严格。
         */
        fun decode(text: String, storedLocally: Boolean = false): PersonalLearningData {
            if (storedLocally) return decodeChecked(text, lenient = true).boundedForStorage()
            return decodeChecked(text, lenient = false)
        }

        private fun decodeChecked(text: String, lenient: Boolean): PersonalLearningData {
            require(lenient || text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "学习文件超过 32 MB" }
            val root = JSONObject(text)
            if (root.has("format")) {
                require(root.getString("format") == "cyime-personal-learning" && root.getInt("version") == 1) { "不支持的学习文件格式" }
            }
            require(root.has("bigrams") && root.has("trigrams")) { "缺少手机学习数据" }
            fun count(row: JSONObject): Int {
                val value = row.getLong("count")
                require(value >= 1 && (lenient || value <= MAX_COUNT)) { "次数必须在 1～1000000 之间" }
                require(row.getDouble("count") == value.toDouble()) { "次数必须是整数" }
                return value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            fun sequences(key: String, size: Int): List<LearnedSequence> {
                val array = root.getJSONArray(key)
                require(lenient || array.length() <= MAX_SEQUENCES) { "学习条目过多" }
                val seen = hashSetOf<List<String>>()
                return List(array.length()) { index ->
                    val row = array.getJSONObject(index)
                    val tokens = row.getJSONArray("tokens")
                    require(tokens.length() == size) { "无效的 $key 长度" }
                    val values = List(size) { tokens.getString(it).also { token -> require(token.length in 1..2) } }
                    require(seen.add(values)) { "重复的学习条目" }
                    LearnedSequence(values, count(row))
                }
            }
            val profile = root.optJSONObject("profile")
            val entries = profile?.getJSONArray("entries") ?: JSONArray()
            require(entries.length() <= 100_000) { "个人方案超过 100000 条" }
            val seen = hashSetOf<Pair<String, String>>()
            val continuations = List(entries.length()) { index ->
                val row = entries.getJSONObject(index)
                val prefix = row.getString("context")
                val next = row.getString("text")
                require(prefix.length in 1..8 && next.length in 1..24 && prefix.isNotBlank() && next.isNotBlank()) { "无效的联想条目" }
                require(seen.add(prefix to next)) { "重复的联想条目" }
                PersonalContinuation(prefix, next, count(row))
            }
            val recent = root.optJSONArray("recentInputs") ?: JSONArray()
            require(lenient || recent.length() <= MAX_RECENT)
            return PersonalLearningData(sequences("bigrams", 2), sequences("trigrams", 3),
                List(recent.length()) { recent.getString(it).also { value -> require(value.length in 1..2) } },
                profile?.optString("name").orEmpty().also { require(it.length <= 100) }, continuations)
        }
    }
}

/** Query only matching suffix groups; corpus size never adds a full scan to each keystroke. */
class PersonalContinuationIndex(rows: List<PersonalContinuation>) {
    private val groups = rows.groupBy { it.context }.mapValues { (_, group) ->
        val total = group.sumOf { it.count.toLong() }.toFloat()
        group.sortedByDescending { it.count }.map { AssociationCandidate(it.text, 0.4f * it.count / total) }
    }
    fun predict(context: String, topK: Int = 10): List<AssociationCandidate> {
        if (topK <= 0) return emptyList()
        for (size in minOf(context.length, 8) downTo 1) groups[context.takeLast(size)]?.let { return it.take(topK) }
        return emptyList()
    }
}
