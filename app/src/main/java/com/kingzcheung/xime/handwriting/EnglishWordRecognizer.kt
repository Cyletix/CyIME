package com.kingzcheung.xime.handwriting

import android.content.Context

/**
 * 英文整词手写：整段笔迹一次认成单词（不再一个字母一个字母地切），解码时只许出英文字母和句末、句中的标点。
 * 模型是在公式模型上用合成整词笔迹接着训练的（tools/handwriting_training/english_words_data.py），
 * 结构和文件名同公式模型；推到外部私有目录 `handwriting_models/english/`，不随安装包分发。
 * 没有这个模型时英文手写照旧用字母模板逐个切分。
 */
object EnglishWordRecognizer {
    /** 预留给模型下载页的 id；现在只读外部私有目录里的个人模型包。 */
    const val MODEL_ID = "handwriting-english"
    private const val DIR = "handwriting_models/english"
    private const val BEAM = 5

    private val model = InkSeq2SeqModel(MODEL_ID, DIR, "english")

    fun isAvailable(context: Context): Boolean = model.isAvailable(context)

    fun release() = model.release()

    /** 整段笔迹认成英文单词，返回 (单词, 置信度 0~1)，按置信度降序。 */
    fun recognize(context: Context, strokes: List<List<Pair<Float, Float>>>): List<Pair<String, Float>> =
        model.recognize(context, strokes, BEAM, ::allowed)

    /**
     * 分好词的一段笔迹（[EnglishWordSpacing]）：每个词单独认（模型是按单词训练的，整行一起认基本认不出），
     * 首选用空格连成一句；其余候选把最拿不准的几个词各换成它的第二候选。
     */
    fun recognizeWords(context: Context, strokes: List<List<Pair<Float, Float>>>, words: List<List<Int>>): List<Pair<String, Float>> {
        if (words.size <= 1) return recognize(context, strokes)
        val each = words.map { word -> recognize(context, word.map { strokes[it] }) }
        return joinWords(each)
    }

    /** 每个词的候选 → 整句候选：首选连成一句，再把首选分数最低的几个词各换成第二候选。 */
    internal fun joinWords(each: List<List<Pair<String, Float>>>): List<Pair<String, Float>> {
        val words = each.filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        fun sentence(swap: Int = -1) = words.mapIndexed { i, w -> w[if (i == swap) 1 else 0].first }.joinToString(" ") to
            words.mapIndexed { i, w -> w[if (i == swap) 1 else 0].second }.average().toFloat()
        val unsure = words.indices.filter { words[it].size > 1 }.sortedBy { words[it][0].second }.take(SENTENCE_ALTERNATIVES)
        return (listOf(sentence()) + unsure.map { sentence(it) }).distinctBy { it.first }
    }

    private const val SENTENCE_ALTERNATIVES = 4

    /**
     * 解码时能出的记号：英文字母，和句子里常见的 . , ? !（模型词表没有撇号）。
     * 电脑回放：你写的句子字母错误率 8.7% → 6.2%；公开数据 1000 个单词（没有标点）只多出 1 个标点，全对率不变。
     */
    internal fun allowed(token: String): Boolean = token.length == 1 && token[0].let { it in 'a'..'z' || it in 'A'..'Z' || it in PUNCTUATION }

    private const val PUNCTUATION = ".,?!"
}
