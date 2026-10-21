package com.kingzcheung.xime.handwriting

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 日语手写的漢字：内置汉字手写模型的字表是简体，这里把模型认出的字换成日语字形（图→図、驿→駅、广→広），
 * 并按日语常用度排序。模型字表里没有的日本字（込、働、峠…）走笔画模板（HandwritingTemplates 的内置模板）。
 * 数据：assets/handwriting/japanese_kanji.json，由 tools/handwriting_training/japanese_kanji_map.py 生成。
 */
object JapaneseKanji {
    private const val TAG = "JapaneseKanji"
    private const val ASSET = "handwriting/japanese_kanji.json"
    /** 日语常用度加分的幅度（电脑上用个人样本调过：漢字首选更准，假名几乎不受影响）。 */
    internal const val FREQUENCY_WEIGHT = 0.03f
    private const val RARE_RANK = 3000

    internal class Table(val forms: Map<String, List<String>>, val rank: Map<String, Int>)

    @Volatile
    private var table: Table? = null

    internal fun parse(json: String): Table {
        val o = JSONObject(json)
        val map = o.getJSONObject("map")
        val forms = HashMap<String, List<String>>(map.length() * 2)
        for (k in map.keys()) map.getJSONArray(k).let { a -> forms[k] = List(a.length()) { a.getString(it) } }
        val freq = o.getJSONObject("freq")
        val rank = HashMap<String, Int>(freq.length() * 2)
        for (k in freq.keys()) rank[k] = freq.getInt(k)
        return Table(forms, rank)
    }

    private fun table(context: Context): Table? = table ?: runCatching {
        parse(context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })
    }.onFailure { Log.w(TAG, "japanese kanji table unreadable", it) }.getOrNull()?.also { table = it }

    /**
     * 往候选里加上模型认出的漢字（日语字形）：分数是这个字在查询特征里的值（与“只像这一个字”的模板的余弦，
     * 和假名模板的相似度同一量纲）；同一简体对应多个日语字形时按常用度先后。
     */
    internal fun addModelKanji(best: MutableMap<String, Float>, raw: List<HandwritingRawCandidate>, query: HandwritingTemplates.Feature, t: Table) {
        val idx = query.idx ?: return
        val vals = query.vals ?: return
        for (c in raw) {
            val forms = t.forms[c.char] ?: continue
            val at = idx.binarySearch(c.index)
            if (at < 0) continue
            forms.forEachIndexed { j, form ->
                val s = vals[at] - 0.001f * j
                if (s > (best[form] ?: -2f)) best[form] = s
            }
        }
    }

    /** 常用的字略加分：1 名最多加 [FREQUENCY_WEIGHT]，越生僻越少；假名没有名次不加。 */
    internal fun applyFrequency(best: MutableMap<String, Float>, t: Table) {
        val logMax = kotlin.math.ln(RARE_RANK.toFloat())
        for ((ch, s) in best.entries.toList()) {
            val r = t.rank[ch] ?: continue
            best[ch] = s + FREQUENCY_WEIGHT * (1f - kotlin.math.ln(r.coerceIn(1, RARE_RANK).toFloat()) / logMax)
        }
    }

    internal fun tableFor(context: Context): Table? = table(context)
}
