import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

// Pinned upstream dictionaries; local schema/filter adaptations are recorded below.
val chineseRevision = "9e66b0729083b37d217312294f6d516c8d7234be"
val chineseArchiveSha = "85c66668255de755b51d3513ecdc612eada0e0b8d4ea27716a046257f17464e0"
val chineseRoot = layout.buildDirectory.dir("generated/chinese-assets/rime_ice")
val chineseTopFiles = setOf("LICENSE", "README.md", "rime_ice.schema.yaml", "rime_ice.dict.yaml",
    "double_pinyin_flypy.schema.yaml", "melt_eng.schema.yaml", "melt_eng.dict.yaml",
    "radical_pinyin.schema.yaml", "radical_pinyin.dict.yaml", "symbols_v.yaml", "symbols_caps_v.yaml")
fun chineseSha(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(65536)
        while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
val prepareChineseDictionaries by tasks.registering {
    group = "build setup"
    inputs.property("revision", chineseRevision)
    inputs.property("sha256", chineseArchiveSha)
    inputs.property("files", chineseTopFiles)
    inputs.property("qwjrtkPreset", 1)
    inputs.property("cyletix10Preset", 1)
    inputs.property("t9EnglishIndex", 6)
    inputs.file(file("src/main/assets/english.txt"))
    inputs.property("measuredNeighborCorrection", 2)
    inputs.property("candidatePolicy", 3)
    inputs.property("pinyin26WordModel", 5)
    inputs.property("candidateComments", 1)
    inputs.property("cyimeTerms", 1)
    val extWeights = file("data/rime_ice_ext_weights.tsv.gz")
    inputs.file(extWeights)
    val baseWeights = file("data/rime_ice_base_weights.tsv.gz")
    inputs.file(baseWeights)
    val charWeights = file("data/rime_ice_8105_weights.tsv.gz")
    inputs.file(charWeights)
    val finalParticles = file("data/sentence_final_particles.txt")
    inputs.file(finalParticles)
    val measureWords = file("data/sentence_measure_words.txt")
    inputs.file(measureWords)
    outputs.dir(chineseRoot)
    doLast {
        val archive = File(rootProject.projectDir, ".gradle/chinese-data/$chineseRevision.zip")
        if (!archive.isFile || chineseSha(archive) != chineseArchiveSha) {
            check(!gradle.startParameter.isOffline) { "Run prepareChineseDictionaries once online." }
            archive.parentFile.mkdirs()
            val pending = File(archive.parentFile, "${archive.name}.download")
            val connection = URI("https://codeload.github.com/iDvel/rime-ice/zip/$chineseRevision").toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 600000
            try {
                check(connection.responseCode == 200)
                connection.inputStream.use { i -> pending.outputStream().use { i.copyTo(it) } }
                check(chineseSha(pending) == chineseArchiveSha) { "Chinese dictionary checksum mismatch" }
                pending.copyTo(archive, overwrite = true)
            } finally { connection.disconnect(); pending.delete() }
        }
        val root = chineseRoot.get().asFile.apply { mkdirs() }
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                val relative = entry.name.substringAfter('/')
                if (relative in chineseTopFiles || listOf("cn_dicts/", "en_dicts/", "lua/", "opencc/").any { relative.startsWith(it) }) {
                    val target = File(root, relative)
                    check(target.canonicalPath.startsWith(root.canonicalPath + File.separator))
                    target.parentFile.mkdirs()
                    zip.getInputStream(entry).use { i -> target.outputStream().use { i.copyTo(it) } }
                }
            }
        }
        fun reweight(name: String, table: File) {
            val estimated = java.util.zip.GZIPInputStream(table.inputStream()).bufferedReader().useLines { lines ->
                lines.associate { line -> line.substringBeforeLast('\t') to line.substringAfterLast('\t') }
            }
            val dict = File(root, "cn_dicts/$name.dict.yaml")
            var body = false
            var reweighted = 0
            dict.writeText(dict.readLines().joinToString("\n", postfix = "\n") { line ->
                if (line.trim() == "...") body = true
                val fields = line.split('\t')
                val weight = if (body && fields.size == 3) estimated[fields[0] + "\t" + fields[1]] else null
                if (weight == null) line else { reweighted++; fields[0] + "\t" + fields[1] + "\t" + weight }
            })
            check(reweighted > estimated.size * 99 / 100) { "$name.dict.yaml no longer matches ${table.name}" }
        }
        // Upstream ext.dict.yaml gives every entry the placeholder weight 100, so its phrases tie in
        // arbitrary order. Use corpus estimates on base.dict's count scale (tools/ime_lab/ext_weights.py).
        reweight("ext", extWeights)
        // base.dict.yaml weights are counts from written text (核心 over 好像 for hx); blend them (70% written, 30% chat)
        // with LCCC chat counts (tools/ime_lab/chat_word_counts.py + chat_base_weights.py, beta 0.3).
        reweight("base", baseWeights)
        // Single characters (8105.dict.yaml) also had written-text weights: 把 3.8M over 吧 2.4M although chat uses
        // 吧 seven times as often, so 下个月吧 came out as 下个月把. Same blend as base (chat_base_weights.py --dict 8105).
        reweight("8105", charWeights)
        // The upstream corrector clears ordinary spelling annotations after the
        // translator has generated them. Let the host retain that real spelling
        // for responsive candidate UI without disabling pronunciation corrections.
        val corrector = File(root, "lua/corrector.lua")
        val source = corrector.readText()
        val commentGate = "if env.keep_comment then"
        check(source.split(commentGate).size == 2) { "Upstream corrector comment gate changed" }
        corrector.writeText(source.replace(commentGate,
            "if env.keep_comment or env.engine.context:get_option(\"_cyime_candidate_comments\") then"))
        // Re-index the pinned English word sources; never lowercase the displayed text.
        // Exact whole-word digits only: no abbreviation/completion collisions with Chinese.
        // Only very common words (top of the bundled frequency-ordered english.txt), upstream's
        // curated internet/brand list and two-letter capitals are offered in Chinese T9, weighted by
        // frequency rank; other words would crowd out Chinese.
        val commonEnglish = file("src/main/assets/english.txt").readLines().map { it.trim().lowercase(java.util.Locale.ROOT) }
            .filter { it.isNotEmpty() }.take(3000).withIndex().associate { it.value to 3000 - it.index }
        val english = linkedMapOf<String, String>()
        val digits = "22233344455566677778889999"
        for (source in listOf("en_ext", "en")) {
            File(root, "en_dicts/$source.dict.yaml").useLines { lines ->
                var body = false
                lines.forEach { line ->
                    if (line.trim() == "...") body = true
                    else if (body && !line.startsWith("#")) {
                        val fields = line.split('\t')
                        if (fields.size >= 2 && fields[0].isNotBlank() &&
                            fields[1].length in 2..64 && fields[1].any { it in 'A'..'Z' || it in 'a'..'z' } &&
                            fields[1].all { it.code in 32..126 }) {
                            // Keep literal spelling as well as upstream aliases (Counter-Strike 2 -> CS2).
                            // Punctuation is implicit; literal digits retain their value. Do not lose
                            // an existing word's spelling when its upstream code is an abbreviation.
                            // en_ext is upstream's curated internet/brand list (ChatGPT, Minecraft); from the
                            // full word list keep common words and two-letter capitals (QQ, AI, PC).
                            val weight = commonEnglish[fields[0].lowercase(java.util.Locale.ROOT)]
                                ?: fields.getOrNull(2)?.toIntOrNull()?.takeIf { source == "en_ext" && it > 0 }
                                ?: 1000.takeIf { source == "en_ext" || fields[0].matches(Regex("[A-Z]{2}")) }
                                ?: return@forEach
                            for (spelling in listOf(fields[1], fields[0]).distinct()) {
                                if (spelling.length !in 2..64 || !spelling.all { it.code in 32..126 }) continue
                                val code = spelling.lowercase(java.util.Locale.ROOT).mapNotNull {
                                    when (it) { in 'a'..'z' -> digits[it - 'a']; in '0'..'9' -> it; else -> null }
                                }.joinToString("")
                                if (code.length < 2) continue
                                val row = fields[0] + "\t" + code + "\t" + weight
                                english.putIfAbsent(fields[0] + "\t" + code, row)
                            }
                        }
                    }
                }
            }
        }
        File(root, "cyime_t9_english.dict.yaml").writeText(
            "# Generated from bundled rime-ice en_dicts; licenses retained alongside sources.\n" +
            "---\nname: cyime_t9_english\nversion: \"1\"\nsort: by_weight\n...\n" +
            english.values.joinToString("\n", postfix = "\n"))

        // Same engine, dictionary and options as Chinese26; only the keyboard arrangement differs.
        // A sentence-final particle (吧, 呗, 呢…, app/data/sentence_final_particles.txt) is penalised as the first
        // word of a composed sentence, and sentences may choose among four single-character homophones per
        // syllable (only one before, so 把地址给我 became 吧地址给我 once 吧 outweighed 把). Patched Poet /
        // ScriptTranslator, see docs/development/chat-base-weights-20261018.md.
        // Measure words (个, 只…, app/data/sentence_measure_words.txt) never start a sentence either (哥你好 → 个你好).
        val particles = (finalParticles.readLines() + measureWords.readLines()).map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        check(particles.isNotEmpty()) { "No sentence-final particles in ${finalParticles.name}" }
        val baseSchema = File(root, "rime_ice.schema.yaml").readText().replace("\ntranslator:\n",
            "\ntranslator:\n  enable_correction: true\n  cyime_neighbor_correction: true\n" +
            "  sentence_char_homophones: 4\n  sentence_position_penalty: -2.0\n" +
            "  sentence_position_final_only: [${particles.joinToString(", ")}]\n")
            .lineSequence().filterNot { it.contains("- lua_filter@*long_word_filter") }.joinToString("\n")
            .replace("  filters:\n", "  filters:\n    - cyime_candidate_policy\n") +
            "\ncyime:\n  candidate_policy: true\n" +
            // 26-key ranking by the nine-key word n-gram model (docs/development/pinyin26-word-model-20261018.md):
            // same decoder and model file; initials are not penalized (a letter names its initial, unlike a
            // keypad digit), a neighbour-corrected word pays t9/correction_penalty, the dictionary beam is
            // wider for all-initials input and whole-input dictionary words always compete. Learned words
            // keep their user entry and weigh more (one or two picks bring zdky's 这都可以 back to first).
            // Without t9_ngram.lm the ordinary Rime path is kept.
            // 1.14.11 used the nine-key budgets (source 128 / pool 64 / sentence beam 24) and raised tablet
            // per-key p95 from ~18 to ~55 ms; 16/16/8 is about 7x faster on the computer at nearly the same
            // accuracy (pinyin26-word-model-20261018.md). Keep only if the tablet p95 stays at the baseline.
            // Off in 1.14.14 until the tablet p95 has been measured with these budgets.
            "\nt9:\n  joint_decoder: false\n  joint_requires_ngram: true\n  source_budget: 16\n  pool_budget: 16\n" +
            "  dictionary_beam: 64\n  sentence_beam: 8\n  result_budget: 12\n  display_sentences: 1\n" +
            "  spelling_penalty: 0\n  word_penalty: 8.0\n  correction_penalty: -9.2\n  whole_input_phrases: 16\n" +
            "  prefer_user_entries: true\n  ngram_model: t9_ngram\n" +
            "  ngram_sentence_start: true\n  ngram_eos_weight: 0\n  ngram_user_weight: 0.3\n" +
            "  ngram_dictionary_prior: 0.02\n  ngram_merge_segmentations: false\n"
        check(baseSchema.contains("cyime_neighbor_correction: true"))
        File(root, "rime_ice.schema.yaml").writeText(baseSchema)
        val doublePinyin = File(root, "double_pinyin_flypy.schema.yaml")
        doublePinyin.writeText(doublePinyin.readText().replace("\ntranslator:\n",
            "\ntranslator:\n  cyime_neighbor_correction: true\n")
            .replace("  filters:\n", "  filters:\n    - cyime_candidate_policy\n")
            .replace("enable_correction: false", "enable_correction: true"))
        File(root, "pinyin_qwjrtk.schema.yaml").writeText(baseSchema
            .replace("schema_id: rime_ice", "schema_id: pinyin_qwjrtk")
            .replace("name: 雾凇拼音", "name: QWJRTK（双拇指）"))
        File(root, "pinyin_cyletix10.schema.yaml").writeText(baseSchema
            .replace("schema_id: rime_ice", "schema_id: pinyin_cyletix10")
            .replace("name: 雾凇拼音", "name: Cyletix10（实验）"))

        // Same IME vocabulary as CyIME-Windows data/rime/cyime_terms.dict.yaml; imported after upstream
        // tables so upstream weights win on duplicates.
        File(root, "cyime_terms.dict.yaml").writeText(
            "# CyIME vocabulary shared with CyIME-Windows.\n---\nname: cyime_terms\nversion: \"2026-10-01\"\nsort: by_weight\n...\n" +
            listOf("浮窗\tfu chuang\t10000", "悬浮工具栏\txuan fu gong ju lan\t5000", "语音上屏\tyu yin shang ping\t5000",
                "自动互联\tzi dong hu lian\t5000", "候选窗\thou xuan chuang\t5000", "候选框\thou xuan kuang\t5000",
                "流式识别\tliu shi shi bie\t5000", "语音转写\tyu yin zhuan xie\t5000", "九宫格输入\tjiu gong ge shu ru\t5000",
                "数字小键盘\tshu zi xiao jian pan\t5000",
                // Key and keyboard names the IME itself uses (2026-10-19: yuyanjian had no 语言键, so 语言见 won).
                "语言键\tyu yan jian\t5000", "换行键\thuan hang jian\t5000", "表情键\tbiao qing jian\t5000",
                "中英键\tzhong ying jian\t5000", "上档键\tshang dang jian\t5000", "大小写键\tda xiao xie jian\t5000",
                "逗号键\tdou hao jian\t5000", "句号键\tju hao jian\t5000", "剪贴板键\tjian tie ban jian\t5000",
                "收起键\tshou qi jian\t5000", "全选键\tquan xuan jian\t5000", "撤销键\tche xiao jian\t5000",
                "拼音键\tpin yin jian\t5000", "手写键\tshou xie jian\t5000", "地球键\tdi qiu jian\t5000",
                "搜索键\tsou suo jian\t5000", "十四键\tshi si jian\t5000", "悬浮键盘\txuan fu jian pan\t5000",
                "单手键盘\tdan shou jian pan\t5000", "键盘高度\tjian pan gao du\t5000").joinToString("\n", postfix = "\n"))
        // rime-ice reads the adverbial 地 in 慢慢地/默默地 only as de; many people type it as di (manmandi), which
        // matched nothing and gave 慢慢滴. Offer every such entry under di too, at the same weight: a larger
        // (whole 的/地/得 family) weight was tried and pushed 有效地 over 有效的 for the initials yxd.
        val diVariants = listOf("base", "ext").flatMap { name ->
            var body = false
            File(root, "cn_dicts/$name.dict.yaml").readLines().mapNotNull { line ->
                if (line.trim() == "...") body = true
                val fields = line.split('\t')
                if (body && fields.size == 3 && fields[0].length >= 2 && fields[0].endsWith("地") && fields[1].endsWith(" de"))
                    fields[0] + "\t" + fields[1].dropLast(2) + "di\t" + fields[2] else null
            }
        }.distinct()
        check(diVariants.size > 500) { "Adverbial 地 entries not found in rime_ice base/ext" }
        File(root, "cyime_di.dict.yaml").writeText(
            "# Adverbial 地 typed as di (generated from rime_ice base/ext).\n---\nname: cyime_di\nversion: \"2026-10-19\"\nsort: by_weight\n...\n" +
            diVariants.joinToString("\n", postfix = "\n"))
        val iceDict = File(root, "rime_ice.dict.yaml")
        val othersImport = "  - cn_dicts/others   # 一些杂项\n"
        check(iceDict.readText().split(othersImport).size == 2) { "Upstream rime_ice import list changed" }
        iceDict.writeText(iceDict.readText().replace(othersImport, othersImport + "  - cyime_terms\n  - cyime_di\n"))
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareChineseDictionaries) }
