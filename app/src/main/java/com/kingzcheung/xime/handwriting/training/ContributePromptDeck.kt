package com.kingzcheung.xime.handwriting.training

/**
 * 贡献页随机出题（用户 11-12：原来手写和滑行贡献每人都是同样一组字词，五十多人交上来全一样，没用）。
 *
 * 题从 [POOL_ASSET]（3000 常用字、3000 少用字、3000 两字词，tools/handwriting_training/build_contribute_pool.py 生成）里出，
 * 按每个人的随机贡献编号打乱：不同人拿到不同的字词，同一个人每次进来顺序不变，接着上次写。题量九千多道，写不完。
 * 纯函数，单测见 ContributePromptDeckTest。
 */
object ContributePromptDeck {
    /** 一轮里每 10 道题的类型：P 缺数据的常用字（排完让给常用字）、C 常用字、R 生僻字、W 两字词。 */
    private const val PATTERN = "PCWCCRWCCW"
    private const val CHAR_HINT = "按平时的速度写"
    private const val WORD_HINT = "像平时手写输入那样写"
    /** 题库：每行 `类型<TAB>文字<TAB>附加`（char 常用字；rare 少用字，附加是读音；word 两字词，附加是拼音）。 */
    const val POOL_ASSET = "handwriting/contribute_pool.tsv"

    /** 贡献编号（16 位十六进制）当种子；不是十六进制时用字符串哈希。[salt] 让同一个人不同页面的顺序也不同。 */
    fun seedOf(writer: String, salt: String = ""): Long =
        (writer.toULongOrNull(16)?.toLong() ?: writer.hashCode().toLong()) xor salt.hashCode().toLong()

    fun <T> shuffled(items: List<T>, seed: Long): List<T> = items.shuffled(java.util.Random(seed))

    data class Pools(
        val priority: List<TrainingPrompt>,
        val common: List<TrainingPrompt>,
        val rare: List<TrainingPrompt>,
        val words: List<TrainingPrompt>,
    )

    private fun prompt(kind: String, text: String, hint: String?) = TrainingPrompt(
        id = "contribute:$kind:$text", type = TrainingPromptType.Chinese, label = text,
        source = "contribute", hint = hint, kind = kind,
    )

    private fun rows(pool: Sequence<String>): Sequence<List<String>> =
        pool.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
            .filter { it.size >= 2 && it[1].isNotBlank() }

    /**
     * [fixed] 是 contribute_prompts.tsv 解析出的题（char 缺数据的常用字、rare 生僻字、word 两字词），
     * [pool] 是 [POOL_ASSET] 的行。固定题先放进各类，题库里重复的不再出。
     */
    fun pools(fixed: List<TrainingPrompt>, pool: Sequence<String>): Pools {
        val priority = fixed.filter { it.kind == "char" }
        val rare = fixed.filter { it.kind == "rare" }.toMutableList()
        val words = fixed.filter { it.kind == "word" }.toMutableList()
        val common = ArrayList<TrainingPrompt>()
        val taken = fixed.mapTo(HashSet()) { it.label }
        rows(pool).forEach { cols ->
            val text = cols[1].trim()
            val extra = cols.getOrNull(2)?.trim().orEmpty()
            when (cols[0]) {
                "char" -> if (isOneHan(text) && taken.add(text)) common += prompt("char", text, CHAR_HINT)
                "rare" -> if (isOneHan(text) && extra.isNotEmpty() && taken.add(text)) rare += prompt("rare", text, extra)
                "word" -> if (text.length in 2..4 && taken.add(text)) words += prompt("word", text, WORD_HINT)
            }
        }
        return Pools(priority, common, rare, words)
    }

    /** 每类按 [seed] 打乱，再按 [PATTERN] 穿插；某类出完就跳过，全部出完为止。同一个题只出一次。 */
    fun handwriting(pools: Pools, seed: Long): List<TrainingPrompt> {
        val queues = mapOf(
            'P' to ArrayDeque(shuffled(pools.priority, seed xor 1)),
            'C' to ArrayDeque(shuffled(pools.common, seed xor 2)),
            'R' to ArrayDeque(shuffled(pools.rare, seed xor 3)),
            'W' to ArrayDeque(shuffled(pools.words, seed xor 4)),
        )
        val out = ArrayList<TrainingPrompt>(queues.values.sumOf { it.size })
        val seen = HashSet<String>()
        while (queues.values.any { it.isNotEmpty() }) {
            for (slot in PATTERN) {
                val queue = queues.getValue(slot).takeIf { it.isNotEmpty() }
                    ?: queues.getValue('C').takeIf { slot == 'P' && it.isNotEmpty() } ?: continue
                val next = queue.removeFirst()
                if (seen.add(next.id)) out += next
            }
        }
        return out
    }

    /**
     * 从 [from] 往后找第一道这个人还没写过的题（到末尾从头找）；都写过就停在 [from]。
     * 以前写过固定题的人换成随机题后，不再重复写同一个字。
     */
    fun firstUnwritten(prompts: List<TrainingPrompt>, from: Int, written: (String) -> Boolean): Int {
        if (prompts.isEmpty()) return 0
        val start = from.coerceIn(0, prompts.size - 1)
        for (step in prompts.indices) {
            val i = (start + step) % prompts.size
            if (!written(prompts[i].id)) return i
        }
        return start
    }

    private fun isOneHan(text: String): Boolean =
        text.codePointCount(0, text.length) == 1 &&
            Character.UnicodeScript.of(text.codePointAt(0)) == Character.UnicodeScript.HAN

    /** 滑行贡献的一道题：[letters] 是要滑的字母，[label] 是显示用的词（中文题），英文题为空。 */
    data class GlidePrompt(val letters: String, val label: String = "")

    /** english.txt 是网页词频，前几千里有这些不适合当题的词。 */
    private val ENGLISH_BLOCK = setOf("sex", "sexy", "porn", "xxx", "teen", "teens", "gay", "lesbian", "naked", "nude",
        "fetish", "cialis", "viagra", "casino", "poker", "gambling", "escort", "fuck", "shit", "damn", "ass", "bitch")
    private val ENGLISH_WORD = Regex("[a-z]{3,10}")
    private val HAS_VOWEL = Regex("[aeiouy]")

    /** 英文题：english.txt 前 [limit] 个常用词里 3～10 个字母的（没有元音的缩写如 www、html 不要），按 [seed] 打乱。 */
    fun englishGlide(lines: Sequence<String>, seed: Long, limit: Int = 5000): List<GlidePrompt> =
        shuffled(lines.take(limit).map { it.trim() }
            .filter { ENGLISH_WORD.matches(it) && HAS_VOWEL.containsMatchIn(it) && it !in ENGLISH_BLOCK }
            .distinct().map { GlidePrompt(it) }.toList(), seed)

    private val PINYIN_LETTERS = Regex("[a-z]{2,16}")

    /** 中文、九键题：[POOL_ASSET] 里的两字词，滑全拼（拼音去掉空格），按 [seed] 打乱。 */
    fun pinyinGlide(pool: Sequence<String>, seed: Long): List<GlidePrompt> =
        shuffled(rows(pool).filter { it[0] == "word" }.mapNotNull { cols ->
            val letters = cols.getOrNull(2)?.replace(" ", "")?.takeIf { PINYIN_LETTERS.matches(it) }
            letters?.let { GlidePrompt(it, cols[1].trim()) }
        }.distinctBy { it.letters }.toList(), seed)
}
