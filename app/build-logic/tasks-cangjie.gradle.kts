import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

// 仓颉五代 table for the built-in cyime_cangjie schema: Jackchows/Cangjie5 (MIT, 五倉世紀 codes revised against
// the official manuals) and McBopomofo's Taiwan corpus counts (MIT) for character and word weights, both pinned.
// CyIME Windows builds the same table (one code table on both). The schema lives in
// src/main/assets/rime_chinese/cyime_cangjie.schema.yaml; only the table is generated here.
// 仓颉速成 (cyime_quick) gets its table from the same rows: each code cut to its first and last letter.
val cangjieRevision = "c9b64e77951cba48bdfa5de8eaedd1a908666b9b"
val cangjieTableSha = "9ef8b462ad0f7c70463307aa81c1110d28650b9cdbd76905d0f2362a362fcbcc"
val mcBopomofoRevision = "18e2060dd5d0b511753ad79a8fe0d112e944f1af"
val mcBopomofoCountsSha = "f3b52d572233ea2c0f2eac1872adfa2fbe2491d8ad051f875863217707561a0e"
val cangjieAssetRoot = layout.buildDirectory.dir("generated/cangjie-assets/rime_chinese")
fun cangjieSha(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(65536)
        while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
fun cangjieFetch(url: String, target: File, sha: String) {
    if (target.isFile && cangjieSha(target) == sha) return
    check(!gradle.startParameter.isOffline) { "Run prepareCangjieDictionary once online." }
    target.parentFile.mkdirs()
    val pending = File(target.parentFile, "${target.name}.download")
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 30000
    connection.readTimeout = 600000
    try {
        check(connection.responseCode == 200) { "${target.name} HTTP ${connection.responseCode}" }
        connection.inputStream.use { i -> pending.outputStream().use { i.copyTo(it) } }
        check(cangjieSha(pending) == sha) { "${target.name} checksum mismatch" }
        pending.copyTo(target, overwrite = true)
    } finally { connection.disconnect(); pending.delete() }
}
val prepareCangjieDictionary by tasks.registering {
    group = "build setup"
    description = "Fetch the pinned 仓颉五代 table and weight it by McBopomofo's Taiwan corpus counts"
    inputs.property("revision", cangjieRevision)
    inputs.property("sha256", cangjieTableSha)
    inputs.property("countsRevision", mcBopomofoRevision)
    inputs.property("countsSha256", mcBopomofoCountsSha)
    inputs.property("policy", 3)
    val charWeights = file("data/rime_ice_8105_weights.tsv.gz")
    inputs.file(charWeights)
    outputs.dir(cangjieAssetRoot)
    doLast {
        val cache = File(rootProject.projectDir, ".gradle/cangjie-data")
        val table = File(cache, "$cangjieRevision.Cangjie5.txt").also {
            cangjieFetch("https://raw.githubusercontent.com/Jackchows/Cangjie5/$cangjieRevision/Cangjie5.txt", it, cangjieTableSha)
        }
        val counts = File(cache, "$mcBopomofoRevision.phrase.occ").also {
            cangjieFetch("https://raw.githubusercontent.com/openvanilla/McBopomofo/$mcBopomofoRevision/Source/Data/phrase.occ",
                it, mcBopomofoCountsSha)
        }
        // rime-ice's 8105 character weights (mainland text, summed over readings). Supplementary-plane characters
        // are kept only when they are in this 通用规范汉字表 (𬌗 …): Android fonts show most of the other 97k
        // (Ext B–J) as boxes, as for the stroke table.
        val mainland = HashMap<String, Long>()
        java.util.zip.GZIPInputStream(charWeights.inputStream()).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val fields = line.split('\t')
                if (fields.size == 3) mainland.merge(fields[0], fields[2].toLong(), Long::plus)
            }
        }
        check(mainland.size > 8000) { "Character weights look truncated: ${mainland.size}" }
        val common = mainland.keys
        val codePattern = Regex("[a-z]+")
        val rows = table.readLines().mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size < 2 || fields[0].isEmpty() || !codePattern.matches(fields[1])) return@mapNotNull null
            val cp = fields[0].codePointAt(0)
            when {
                // Radicals, stroke glyphs and compatibility ideographs only look like real characters
                // (⽇ 暈 beside 日 暈) and would be committed as different code points.
                cp in 0x2E80..0x2FDF || cp in 0x31C0..0x31EF || cp in 0xF900..0xFAFF || cp in 0x2F800..0x2FA1F -> null
                cp >= 0x10000 && fields[0] !in common -> null
                else -> fields[0] to fields[1]
            }
        }.distinct()
        // The table carries no frequencies; CyIME ships no essay.txt either. A character weighs its count in
        // McBopomofo's phrase.occ (the traditional text cangjie types) or its mainland weight scaled to the same
        // corpus size by 的, whichever is larger, so simplified forms rank as used (会 before the Japanese 伝 on
        // ommi). Weights are ×20; characters counted in neither come after, in the table's own order (symbols too)
        // through the last digit. Words of 2–5 characters counted at least 5 times (about 60k) go in without codes,
        // so librime encodes them with the word rules below (我們 → hioan).
        val weights = HashMap<String, Long>()
        counts.forEachLine { line ->
            val fields = line.split(' ')
            if (fields.size == 2) fields[1].toLongOrNull()?.let { weights[fields[0]] = it }
        }
        val scale = weights.getValue("的").toDouble() / mainland.getValue("的")
        fun characterCount(text: String) = maxOf(weights[text] ?: 0L, Math.round((mainland[text] ?: 0L) * scale))
        val characters = rows.map { it.first }.toHashSet()
        val words = weights.filter { (text, count) ->
            count >= 5 && text.codePointCount(0, text.length) in 2..5 &&
                text.codePoints().allMatch { String(Character.toChars(it)) in characters }
        }.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        val out = StringBuilder()
        out.append("# Rime dictionary\n# encoding: utf-8\n#\n")
        out.append("# 仓颉五代码表，由 CyIME app/build-logic/tasks-cangjie.gradle.kts 生成，勿手改。\n")
        out.append("# 单字码：Jackchows/Cangjie5 $cangjieRevision Cangjie5.txt（MIT，朱邦復發明，倉頡之友·馬來西亞、\n")
        out.append("# 倉頡五代補完計劃修訂）。字频与词组：小麦注音 McBopomofo $mcBopomofoRevision Source/Data/phrase.occ（MIT）。\n")
        out.append("# 词组不写编码，由 librime 按下面的构词规则（取各字首尾码）编出。\n\n---\n")
        out.append("name: cyime_cangjie\nversion: \"1.0-cyime1\"\nsort: by_weight\nuse_preset_vocabulary: false\n")
        out.append("max_phrase_length: 5\ncolumns:\n  - text\n  - code\n  - weight\n")
        // x codes are alternative codes for hard characters, yyy and zx are symbols: words never take them.
        out.append("encoder:\n  exclude_patterns:\n    - '^x.*$'\n    - '^yyy.*$'\n    - '^z.*$'\n  rules:\n")
        out.append("    - length_equal: 2\n      formula: \"AaAzBaBbBz\"\n")
        out.append("    - length_equal: 3\n      formula: \"AaAzBaBzCz\"\n")
        out.append("    - length_in_range: [4, 10]\n      formula: \"AaBzCaYzZz\"\n...\n\n")
        val placeOnCode = HashMap<String, Int>()
        var counted = 0
        for ((text, code) in rows) {
            val place = placeOnCode.merge(code, 1, Int::plus)!! - 1
            val count = characterCount(text)
            if (count > 0) counted++
            out.append(text).append('\t').append(code).append('\t').append(count * 20 + (9 - place).coerceAtLeast(0)).append('\n')
        }
        for ((text, count) in words) out.append(text).append("\t\t").append(count * 20).append('\n')
        check(rows.size > 30000 && counted > 7000 && words.size > 50000) {
            "Unexpected cangjie table: rows=${rows.size} counted=$counted words=${words.size}"
        }
        val root = cangjieAssetRoot.get().asFile.apply { mkdirs() }
        File(root, "cyime_cangjie.dict.yaml").writeText(out.toString())
        // 速成: first and last letter of the 仓颉 code, single characters only. x codes (alternative codes of
        // hard characters), z and yyy (symbols) have no 速成 form; punctuation comes from the punctuation keys.
        val quick = StringBuilder()
        quick.append("# Rime dictionary\n# encoding: utf-8\n#\n")
        quick.append("# 仓颉速成码表，由 CyIME app/build-logic/tasks-cangjie.gradle.kts 从 cyime_cangjie 的同一份单字码生成，勿手改。\n")
        quick.append("# 速成码取仓颉码的首尾两码；字频同仓颉。\n\n---\n")
        quick.append("name: cyime_quick\nversion: \"1.0-cyime1\"\nsort: by_weight\nuse_preset_vocabulary: false\n")
        quick.append("columns:\n  - text\n  - code\n  - weight\n...\n\n")
        val quickPlace = HashMap<String, Int>()
        var quickRows = 0
        for ((text, code) in rows.filterNot { (_, code) -> code[0] == 'x' || code[0] == 'z' || code.startsWith("yyy") }
            .map { (text, code) -> text to (if (code.length <= 2) code else "${code.first()}${code.last()}") }.distinct()) {
            val place = quickPlace.merge(code, 1, Int::plus)!! - 1
            quick.append(text).append('\t').append(code).append('\t')
                .append(characterCount(text) * 20 + (9 - place).coerceAtLeast(0)).append('\n')
            quickRows++
        }
        check(quickRows > 25000) { "Unexpected quick table: rows=$quickRows" }
        File(root, "cyime_quick.dict.yaml").writeText(quick.toString())
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareCangjieDictionary) }
