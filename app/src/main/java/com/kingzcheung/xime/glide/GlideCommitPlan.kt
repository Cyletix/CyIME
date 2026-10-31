package com.kingzcheung.xime.glide

/** 紧挨着的上一笔英文滑行：屏上的写法、它的候选和当时的大小写规则。 */
class PreviousGlide(val text: String, val candidates: List<GlideCandidate>, val case: (String) -> String)

/**
 * 一笔英文滑行怎么上屏。[replacePrevious] 不为空时先把上一笔滑行的词换成它（i 划到 m、n 之间是 in，
 * 接着划 done 就改成 I'm done）；[pair] 为真时 [alternatives] 是“上一个词 + 这个词”的整对，选中时连上一个词一起换。
 */
data class GlideCommitPlan(
    val replacePrevious: String?,
    val word: String,
    val alternatives: List<String>,
    val pair: Boolean,
)

/**
 * 按上下文选词：有双词表时，紧挨着上一笔滑行就前后两个词一起选，否则按光标前的词 [before] 选这个词；
 * 双词表还没读入时按轨迹代价原样排序。缩写在这里换成上屏写法（dont → don't、im → I'm）。
 */
fun planGlideCommit(
    candidates: List<GlideCandidate>,
    case: (String) -> String,
    previous: PreviousGlide?,
    before: String?,
    lm: EnglishBigrams?,
    contractions: EnglishContractions?,
): GlideCommitPlan {
    fun shown(word: String, case: (String) -> String) = case(contractions?.display(word) ?: word)
    fun offer(word: String, case: (String) -> String) = contractions?.offer(word)?.let(case)
    if (previous != null && lm != null && previous.candidates.isNotEmpty()) {
        val choices = rankGlideChoices(lm, before, previous.candidates, candidates)
        val best = choices.first()
        val head = shown(best.previous!!, previous.case)
        val word = shown(best.word, case)
        val pairs = LinkedHashSet<String>()
        offer(best.word, case)?.let { pairs += "$head $it" }
        choices.drop(1).forEach { pairs += "${shown(it.previous!!, previous.case)} ${shown(it.word, case)}" }
        pairs -= "$head $word"
        return GlideCommitPlan(head.takeIf { it != previous.text }, word, pairs.take(MAX_ALTERNATIVES), pair = true)
    }
    val ranked = if (lm == null) candidates.map { it.word }
        else rankGlideChoices(lm, before, null, candidates).map { it.word } + candidates.drop(PAIR_TOP).map { it.word }
    val word = shown(ranked.first(), case)
    val alternatives = LinkedHashSet<String>()
    offer(ranked.first(), case)?.let(alternatives::add)
    ranked.drop(1).forEach { alternatives += shown(it, case) }
    alternatives -= word
    return GlideCommitPlan(null, word, alternatives.take(MAX_ALTERNATIVES), pair = false)
}

/** 光标前紧挨着的英文词（去撇号、小写），用作上下文；隔着标点、换行或不是英文时为空。 */
fun glideContextWord(textBefore: String): String? =
    Regex("""(?:^|[\s"(“])([A-Za-z]+(?:['’][A-Za-z]+)?) ?$""").find(textBefore)?.groupValues?.get(1)
        ?.replace("'", "")?.replace("’", "")?.lowercase()

private const val MAX_ALTERNATIVES = 5
