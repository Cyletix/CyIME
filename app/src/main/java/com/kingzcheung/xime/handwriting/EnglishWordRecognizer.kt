package com.kingzcheung.xime.handwriting

import android.content.Context

/**
 * 英文整词手写：整段笔迹一次认成单词（不再一个字母一个字母地切），解码时只许出英文字母。
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
        model.recognize(context, strokes, BEAM) { it.length == 1 && (it[0] in 'a'..'z' || it[0] in 'A'..'Z') }
}
