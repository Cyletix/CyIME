import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

// Stroke (五笔画) table for the built-in stroke keypad: rime/rime-stroke (LGPL-3.0), pinned.
// The adapted schema lives in src/main/assets/rime_chinese/stroke.schema.yaml; only the table is generated here.
val strokeRevision = "1e8fff9b9494ddec23b0cbc526bcfd8171a6fd48"
val strokeDictSha = "b3e93dce89c185f45c3d6e189b86b3a8626913352cc85e1094c786579a665791"
val strokeAssetRoot = layout.buildDirectory.dir("generated/stroke-assets/rime_chinese")
/** 笔画词表收词的最低词频：雾凇 base 里两到四字、词频不低于它的约十四万个词（明目张胆 21510、巅峰对决 1105 都在）。 */
val STROKE_PHRASE_MIN_WEIGHT = 1000L
fun strokeSha(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(65536)
        while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
val prepareStrokeDictionary by tasks.registering {
    group = "build setup"
    description = "Fetch the pinned rime-stroke table and rank it by simplified Chinese character frequency"
    inputs.property("revision", strokeRevision)
    inputs.property("sha256", strokeDictSha)
    inputs.property("policy", 2)
    val charWeights = file("data/rime_ice_8105_weights.tsv.gz")
    inputs.file(charWeights)
    val wordWeights = file("data/rime_ice_base_weights.tsv.gz")
    inputs.file(wordWeights)
    outputs.dir(strokeAssetRoot)
    doLast {
        val source = File(rootProject.projectDir, ".gradle/stroke-data/$strokeRevision.stroke.dict.yaml")
        if (!source.isFile || strokeSha(source) != strokeDictSha) {
            check(!gradle.startParameter.isOffline) { "Run prepareStrokeDictionary once online." }
            source.parentFile.mkdirs()
            val pending = File(source.parentFile, "${source.name}.download")
            val connection = URI("https://raw.githubusercontent.com/rime/rime-stroke/$strokeRevision/stroke.dict.yaml")
                .toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 600000
            try {
                check(connection.responseCode == 200) { "Stroke dictionary HTTP ${connection.responseCode}" }
                connection.inputStream.use { i -> pending.outputStream().use { i.copyTo(it) } }
                check(strokeSha(pending) == strokeDictSha) { "Stroke dictionary checksum mismatch" }
                pending.copyTo(source, overwrite = true)
            } finally { connection.disconnect(); pending.delete() }
        }
        // Upstream sorts by essay.txt frequencies (use_preset_vocabulary), which CyIME does not ship: every
        // character would then tie at 0 and come out in code-point order (㐀 before 十 for hs). Rank instead by
        // rime-ice's 8105 character weights (chat + written blend, summed over readings); other BMP characters
        // follow, basic CJK before extension A. Supplementary-plane characters outside 8105 (about 115k of the
        // 170k rows, mostly Ext B–J and Tangut) are dropped: Android fonts show most of them as boxes.
        val weights = HashMap<String, Long>()
        java.util.zip.GZIPInputStream(charWeights.inputStream()).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val fields = line.split('\t')
                if (fields.size == 3) weights.merge(fields[0], fields[2].toLong(), Long::plus)
            }
        }
        check(weights.size > 8000) { "Character weights look truncated: ${weights.size}" }
        val root = strokeAssetRoot.get().asFile.apply { mkdirs() }
        var body = false
        var kept = 0
        var ranked = 0
        val out = StringBuilder()
        for (line in source.readLines()) {
            if (!body) {
                when {
                    line.startsWith("version:") -> out.append("version: \"2.0-cyime1\"\n")
                    line.startsWith("use_preset_vocabulary:") -> out.append("use_preset_vocabulary: false  # CyIME：字频见下方权重\n")
                    else -> out.append(line).append('\n')
                }
                if (line.trim() == "...") body = true
                continue
            }
            val fields = line.split('\t')
            if (fields.size != 2 || fields[0].isEmpty()) { out.append(line).append('\n'); continue }
            val text = fields[0]
            val cp = text.codePointAt(0)
            val known = weights[text]
            val weight = when {
                known != null -> known + 2
                cp >= 0x10000 -> continue
                // Radicals, stroke glyphs and compatibility ideographs only look like real characters
                // (⼀ ㇀ beside 一 for h) and would come before common completions as exact matches.
                cp in 0x2E80..0x2FDF || cp in 0x31C0..0x31EF || cp in 0xF900..0xFAFF -> continue
                cp in 0x4E00..0x9FFF -> 1L
                else -> 0L
            }
            if (known != null) ranked++
            kept++
            out.append(text).append('\t').append(fields[1]).append('\t').append(weight).append('\n')
        }
        check(body && kept > 40000 && ranked > 8000) { "Unexpected stroke table: rows=$kept ranked=$ranked" }
        File(root, "stroke.dict.yaml").writeText(out.toString())

        // 笔画分词打词（用户 11-09 录屏：每个字只打头几笔、用分词键隔开，出不了词）。
        // 码表只有单字，这里另生成一张词表：雾凇 base 词库里两到四字的常用词，编码是每个字的第一笔；
        // lua/cyime_stroke_phrase.lua 按第一笔查出一批词，再逐字核对打的那几笔是不是这个字笔顺的开头。
        val firstStrokes = HashMap<String, java.util.TreeSet<Char>>()
        body = false
        for (line in source.readLines()) {
            if (!body) { if (line.trim() == "...") body = true; continue }
            val fields = line.split('\t')
            if (fields.size == 2 && fields[0].isNotEmpty() && fields[1].isNotEmpty() && fields[1][0] in "hspnz")
                firstStrokes.getOrPut(fields[0]) { java.util.TreeSet() }.add(fields[1][0])
        }
        val words = HashMap<String, Long>()
        java.util.zip.GZIPInputStream(wordWeights.inputStream()).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val fields = line.split('\t')
                if (fields.size != 3) return@forEach
                val text = fields[0]
                val length = text.codePointCount(0, text.length)
                if (length !in 2..4 || text.length != length) return@forEach
                words.merge(text, fields[2].toLongOrNull() ?: return@forEach, Long::plus)
            }
        }
        val phrases = words.entries
            .filter { (text, weight) -> weight >= STROKE_PHRASE_MIN_WEIGHT && text.all { firstStrokes.containsKey(it.toString()) } }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        check(phrases.size > 100000) { "Stroke phrase list looks truncated: ${phrases.size}" }
        val table = StringBuilder()
        table.append("# Rime dictionary: stroke_phrase\n# encoding: utf-8\n#\n")
        table.append("# 笔画分词打词用的词表，由 app/build-logic/tasks-stroke.gradle.kts 生成，不要手改。\n")
        table.append("# 词和词频取自雾凇拼音 base 词库（app/data/rime_ice_base_weights.tsv.gz）两到四字、词频不低于 $STROKE_PHRASE_MIN_WEIGHT 的词；\n")
        table.append("# 编码是每个字的第一笔（h s p n z），笔顺取自 stroke.dict.yaml。\n\n")
        table.append("---\nname: stroke_phrase\nversion: \"1.0-cyime1\"\nsort: by_weight\nuse_preset_vocabulary: false\n...\n\n")
        for ((text, weight) in phrases) {
            // 个别字有两种笔顺、第一笔不同：每种组合各写一行（最多 4 行），哪种打法都找得到。
            var codes = listOf("")
            for (ch in text) codes = codes.flatMap { prefix -> firstStrokes.getValue(ch.toString()).map { prefix + it } }.take(4)
            for (code in codes) table.append(text).append('\t').append(code).append('\t').append(weight).append('\n')
        }
        File(root, "stroke_phrase.dict.yaml").writeText(table.toString())
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareStrokeDictionary) }
