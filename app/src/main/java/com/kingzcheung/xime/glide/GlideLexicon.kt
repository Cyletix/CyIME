package com.kingzcheung.xime.glide

import android.content.Context
import com.kingzcheung.xime.keyboard.LetterKeyRect
import com.kingzcheung.xime.settings.DoublePinyinScheme

/**
 * 滑行词表与模板缓存。
 * - 英文：随包的 english.txt（按词频排序的常用英文词，与英文补全共用）加上缩写的去撇号写法和
 *   english_glide_words.txt 的补充词（english.txt 没有的 fuck、alright 等，只给滑行用）；
 *   连着划时用 english_bigrams.txt 给前后两个词整对打分（[rankGlideChoices]）。
 * - 中文：随包的 glide_pinyin.txt（雾凇词频合并同音后的常用拼音编码，音节用空格隔开，
 *   由 tools/glide_lab/build_pinyin_lexicon.py 生成）。全拼直接连写；双拼按当前方案逐音节换码。
 * 每种词表按布局缓存一份模板。
 */
object GlideLexicon {
    private const val FILE = "english.txt"
    private const val PINYIN_FILE = "glide_pinyin.txt"
    @Volatile private var words: List<String>? = null
    @Volatile private var pinyin: List<List<String>>? = null
    private const val CONTRACTIONS_FILE = "english_contractions.txt"
    private const val BIGRAMS_FILE = "english_bigrams.txt"
    private const val EXTRA_FILE = "english_glide_words.txt"
    @Volatile private var contractions: EnglishContractions? = null
    @Volatile private var bigrams: EnglishBigrams? = null
    /** 英文、全拼、双拼各留一份；双拼换方案（或改了键位）时按方案内容重建。 */
    private val cached = HashMap<String, Pair<Any?, GlideDecoder>>()

    fun words(context: Context): List<String> = words ?: synchronized(this) {
        words ?: vocabulary(context).filter { it.length >= 2 && it.all { char -> char in 'a'..'z' } }.also { words = it }
    }

    /** english.txt 加上插进去的缩写写法（didnt、youre）和滑行补充词（fuck、alright），滑行词表和双词表共用这一个排名。 */
    private fun vocabulary(context: Context): List<String> = contractions(context).vocabulary(
        context.applicationContext.assets.open(FILE).bufferedReader().useLines { it.toList() },
        context.applicationContext.assets.open(EXTRA_FILE).bufferedReader().useLines { glideExtraWords(it) })

    fun contractions(context: Context): EnglishContractions = contractions ?: synchronized(this) {
        contractions ?: context.applicationContext.assets.open(CONTRACTIONS_FILE).bufferedReader()
            .useLines { EnglishContractions(it.toList().asSequence()) }.also { contractions = it }
    }

    /** 英文双词表；须在后台线程调用。 */
    fun bigrams(context: Context): EnglishBigrams = bigrams ?: synchronized(this) {
        bigrams ?: context.applicationContext.assets.open(BIGRAMS_FILE).bufferedReader()
            .useLines { EnglishBigrams(vocabulary(context), it) }.also { bigrams = it }
    }

    /** 已读入的双词表与缩写表；还没读完时为空，这一笔就不按上下文重排、不换缩写。 */
    fun loadedBigrams(): EnglishBigrams? = bigrams
    fun loadedContractions(): EnglishContractions? = contractions

    /** 常用拼音编码，每项是一个词的音节，按常用程度排列。 */
    fun pinyin(context: Context): List<List<String>> = pinyin ?: synchronized(this) {
        pinyin ?: context.applicationContext.assets.open(PINYIN_FILE).bufferedReader().useLines { lines ->
            // 只有四百多个不同音节：共用同一个字符串，三万个词只多几 MB 的列表。
            val syllables = HashMap<String, String>()
            lines.map { it.trim() }.filter { it.isNotEmpty() }
                .map { line -> line.split(' ').map { syllables.getOrPut(it) { it } } }.toList()
        }.also { pinyin = it }
    }

    /** 布局变化（尺寸、错位、分体）时重建模板；同一布局复用。须在后台线程调用。 */
    fun decoder(context: Context, keyboard: GlideKeyboard): GlideDecoder = cachedDecoder("en", null, keyboard) { words(context) }

    /** 中文滑行：[doublePinyin] 为空时按全拼，否则按该双拼方案的编码。须在后台线程调用。 */
    fun chineseDecoder(context: Context, keyboard: GlideKeyboard, doublePinyin: DoublePinyinScheme?): GlideDecoder =
        cachedDecoder(if (doublePinyin == null) "py" else "sp", doublePinyin, keyboard) { pinyinCodes(pinyin(context), doublePinyin) }

    /**
     * 九键滑行出的数字串：全拼、双拼按词表比对整条线最像哪个词的编码（[dictionary] 为真）；
     * 认不出或不是拼音方案时只取起点、拐弯和终点的键。须在后台线程调用。
     */
    fun t9Digits(context: Context, keys: Map<Char, LetterKeyRect>, xs: FloatArray, ys: FloatArray,
        dictionary: Boolean, doublePinyin: DoublePinyinScheme?): String {
        if (dictionary) {
            val decoder = cachedDecoder(if (doublePinyin == null) "t9" else "t9sp", doublePinyin, T9Glide.keyboard(keys),
                T9Glide.MAX_ENDPOINT) { T9Glide.codes(pinyin(context), doublePinyin) }
            decoder.decode(xs, ys, limit = 1).firstOrNull()?.let { return it.word }
        }
        return T9Glide.turnDigits(xs, ys, keys)
    }

    private fun cachedDecoder(kind: String, source: Any?, keyboard: GlideKeyboard,
        maxEndpoint: Float = GlideDecoder.MAX_ENDPOINT, words: () -> List<String>): GlideDecoder =
        synchronized(this) {
            cached[kind]?.takeIf { (cachedSource, decoder) -> cachedSource == source && decoder.revision == keyboard.revision }?.second
                ?: GlideDecoder(keyboard, words(), maxEndpoint).also { cached[kind] = source to it }
        }

    fun keyboard(keys: Map<Char, LetterKeyRect>): GlideKeyboard {
        val widths = keys.values.map { it.width }.sorted()
        return GlideKeyboard(widths[widths.size / 2],
            keys.mapValues { (_, rect) -> (rect.x + rect.width / 2f) to (rect.y + rect.height / 2f) })
    }
}

/** 词的音节连成要划的字母串；同一字母串只留最常用的一次（xi an 与 xian 划法相同，交给 Rime 区分）。 */
internal fun pinyinCodes(entries: List<List<String>>, doublePinyin: DoublePinyinScheme?): List<String> =
    entries.mapNotNull { syllables ->
        if (doublePinyin == null) syllables.joinToString("")
        else syllables.map { doublePinyin.code(it) ?: return@mapNotNull null }.joinToString("")
    }.distinct()
