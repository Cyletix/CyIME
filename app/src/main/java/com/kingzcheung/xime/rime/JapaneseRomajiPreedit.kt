package com.kingzcheung.xime.rime

/** 罗马音里的元音：以元音结尾即已构成一个假名。 */
private const val ROMAJI_VOWELS = "aiueo"

/**
 * 末尾尚未成音的罗马音（需要按按下的原字母显示的部分）；null 表示末段已经拼完。
 *
 * 依据方案 `japanese.schema.yaml` / `jaroomaji.schema.yaml` 的 preedit_format 判定，
 * 不查引擎：把已经成音的部分交给引擎读音，只有「还在等后续元音的辅音」需要改回原字母。
 * 方案里未识别的小写原字母会被单字母规则直接显示（`xform/k/っ/`、`xform/n/ん/`，
 * 见 japanese.schema.yaml 511-530 行），所以单按一个声母时屏幕上会出现 っ / ん。
 *
 * 判定只依赖「罗马音里未成音的尾部必然以辅音结尾」这一条：
 * 末尾是元音/长音/分隔符就是完整假名（ka→か、kya→きゃ、xtsu→っ、ka-→かー）；
 * 末尾是辅音才可能没拼完，再从末尾回溯出整段辅音串，扣掉串内已经成音的部分
 * （`nn`→ん、双写辅音的第一个→促音 っ）。这样 `k`→k、`sh`→sh、`ka|k`→k，
 * 而 `nn`→完整、`nka`→完整（んか）、`kk`→只留最后一个 k（前面的 k 是促音）。
 *
 * 纯字符串判定，因此可以在持引擎锁的显示路径里直接调用。
 */
internal fun pendingRomajiTail(input: String): String? {
    val last = input.lastOrNull() ?: return null
    if (!last.isLetter() || last.lowercaseChar() in ROMAJI_VOWELS) return null
    // 末尾是辅音（含 n）：回溯出「末尾整段辅音」再判其中哪些已经成音。
    var start = input.length
    while (start > 0) {
        val previous = input[start - 1]
        if (!previous.isLetter() || previous.lowercaseChar() in ROMAJI_VOWELS) break
        start--
    }
    return pendingTailInConsonants(input.substring(start))
}

/**
 * 从整段辅音里扣掉已经成音的部分，返回剩下的待拼尾部；null 表示整段都已成音。
 *
 * 只处理罗马音里两种「辅音也能独立成音」的写法：
 * - `nn` → 撥音 ん（末尾单独的 n 不算，它可能在等第二个 n）；
 * - 同一辅音双写 → 第一个是促音 っ（`kk` 表示 っ + 待拼 k，与主流日语输入法一致）。
 * 其余辅音（`sh`、`ky`、`ts`…）都还没有元音，一起算作待拼尾部。
 */
private fun pendingTailInConsonants(consonants: String): String? {
    var index = 0
    while (index < consonants.length) {
        val current = consonants[index].lowercaseChar()
        val next = consonants.getOrNull(index + 1)?.lowercaseChar()
        when {
            current == 'n' && next == 'n' -> index += 2
            current == 'n' && next != null -> index += 1
            next == current -> index += 1
            else -> break
        }
    }
    return if (index >= consonants.length) null else consonants.substring(index)
}

/**
 * 退格一次要删到的位置（与显示同源，避免删掉屏幕上没显示的内容）。
 *
 * - 末尾还有未成音的尾部 → 只删尾部最后一个字母（`kak` → 删 k）；
 * - 末尾已经成音 → 删掉最后一个完整假名，拗音与撥音整体删
 *   （`kya`、`kann`），双写促音只删后一个假名（`kakka` → 删 ka，保留 かっ）。
 */
internal fun romajiDeleteStart(input: String): Int {
    if (input.isEmpty()) return 0
    if (pendingRomajiTail(input) != null) return input.length - 1
    val last = input.last()
    // 长音符 / 分隔符 / 标点：单个字符即一个删除单位。
    if (!last.isLetter()) return input.length - 1
    // 末尾 nn → 撥音 ん 一个假名。
    if (last.lowercaseChar() == 'n' && input.length >= 2 &&
        input[input.length - 2].lowercaseChar() == 'n'
    ) return input.length - 2
    // 末尾元音：连同它前面的辅音群一起删（拗音 ky、sh 等）。
    var start = input.length - 1
    while (start > 0) {
        val previous = input[start - 1]
        if (!previous.isLetter() || previous.lowercaseChar() in ROMAJI_VOWELS) break
        start--
    }
    // 双写促音（kka）：第一个辅音属于上一个假名 っ，留给下一次删除。
    val consonants = input.substring(start, input.length - 1)
    if (consonants.length >= 2 && consonants[0].lowercaseChar() == consonants[1].lowercaseChar()) {
        start += 1
    }
    return start
}

/**
 * 显示串：已拼完部分用引擎读音，末尾待拼的罗马音按原字母。
 * 没有待拼尾部时返回 null，调用方沿用引擎原回显。
 *
 * [readingOf] 由调用方提供（引擎读音 or 测试里的假映射），使判定逻辑可 JVM 单测。
 */
internal fun romajiTailDisplay(input: String, readingOf: (String) -> String): String? {
    val tail = pendingRomajiTail(input) ?: return null
    val prefix = input.dropLast(tail.length)
    return if (prefix.isEmpty()) tail else readingOf(prefix) + tail
}
