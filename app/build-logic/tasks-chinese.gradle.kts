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
    inputs.property("colemakPreset", 1)
    inputs.property("typewisePreset", 1)
    inputs.property("t9EnglishIndex", 9)
    inputs.file(file("src/main/assets/english.txt"))
    inputs.property("measuredNeighborCorrection", 3)
    inputs.property("candidatePolicy", 3)
    inputs.property("pinyin26WordModel", 5)
    inputs.property("candidateComments", 1)
    inputs.property("cyimeTerms", 1)
    inputs.property("taiwanTraditional", 1)
    inputs.property("rareCharacters", 1)
    inputs.property("datetimeHints", 1)
    val extWeights = file("data/rime_ice_ext_weights.tsv.gz")
    inputs.file(extWeights)
    val baseWeights = file("data/rime_ice_base_weights.tsv.gz")
    inputs.file(baseWeights)
    val charWeights = file("data/rime_ice_8105_weights.tsv.gz")
    inputs.file(charWeights)
    val tencentWeights = file("data/rime_ice_tencent_weights.tsv.gz")
    inputs.file(tencentWeights)
    val domainTerms = file("data/cyime_domain_terms.tsv")
    inputs.file(domainTerms)
    val userFixes = file("data/cyime_user_fixes.tsv")
    inputs.file(userFixes)
    val removedWords = file("data/cyime_removed_words.txt.gz")
    inputs.file(removedWords)
    val acronymFilter = file("data/cyime_acronym_filter.lua")
    inputs.file(acronymFilter)
    val englishMemory = file("data/cyime_en_memory.lua")
    inputs.file(englishMemory)
    val singleSyllable = file("data/cyime_single_syllable.lua")
    inputs.file(singleSyllable)
    val mixedInput = file("data/cyime_mixed_input.lua")
    inputs.file(mixedInput)
    val rareChars = file("data/cyime_rare_chars.lua")
    inputs.file(rareChars)
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
                // text, code, weight; tencent has no code column (text, weight) and is keyed by text alone.
                val key = if (fields.size == 3) fields[0] + "\t" + fields[1] else fields[0]
                val weight = if (body && fields.size in 2..3) estimated[key] else null
                if (weight == null) line else { reweighted++; key + "\t" + weight }
            })
            check(reweighted > estimated.size * 99 / 100) { "$name.dict.yaml no longer matches ${table.name}" }
        }
        // Upstream ext.dict.yaml gives every entry the placeholder weight 100, so its phrases tie in
        // arbitrary order. Use corpus estimates on base.dict's count scale (tools/ime_lab/ext_weights.py).
        reweight("ext", extWeights)
        // base.dict.yaml weights are counts from written text (核心 over 好像 for hx); blend them (70% written, 30% chat)
        // with LCCC chat counts (tools/ime_lab/chat_word_counts.py + chat_base_weights.py, beta 0.3).
        // Where the blend flipped a code's first word and wiki + subtitles + LCCC agree with rime-ice (美国 over 没过:
        // LCCC drops 美国), that one entry goes back on top (tools/ime_lab/general_corpus_fix.py, 567 entries).
        reweight("base", baseWeights)
        // Single characters (8105.dict.yaml) also had written-text weights: 把 3.8M over 吧 2.4M although chat uses
        // 吧 seven times as often, so 下个月吧 came out as 下个月把. Same blend as base (chat_base_weights.py --dict 8105).
        reweight("8105", charWeights)
        // tencent.dict.yaml (about a million phrases) also gives every entry the placeholder 100, so 被嫌弃 and 被掀起
        // tied and the sentence took either. Same corpus estimate as ext, spread less (ext_weights.py tencent).
        reweight("tencent", tencentWeights)
        // IME vocabulary (app/data/cyime_domain_terms.tsv) gets at least its listed weight: the chat counts above
        // rate the IME's own words near zero (上屏 43, 出词 11), so 只输入声母 came out 只输入圣母 and 每个键
        // 每个见. Raise the existing rime-ice entries here; missing ones are added to cyime_terms below.
        // The user's must-pass words (app/data/cyime_user_fixes.tsv) use the same floor, one code each.
        val domainFloors = (domainTerms.readLines() + userFixes.readLines()).filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }.associate { (it[0] + "\t" + it[1]) to it[2].toInt() }.toMutableMap()
        // Sentence pattern verb + 完了/好了 + 吗/没 (user 2026-10-30: 测完了吗 came out 测验了吗 on nine-key): these
        // phrases sat near 200, below 洗澡了吗 and 吃药了吗 on the same keys; each one present gets at least 1000
        // (没 500). Generating the missing ones was tried and dropped: 上传完了吗 then read 上啊换完了吗.
        val resultQuestion = Regex("(.{1,3})(完了|好了)(吗|没)")
        for (name in listOf("base", "ext")) {
            var body = false
            for (line in File(root, "cn_dicts/$name.dict.yaml").readLines()) {
                if (line.trim() == "...") { body = true; continue }
                val fields = line.split('\t')
                if (!body || fields.size != 3) continue
                if (fields[2].toIntOrNull() == null) continue
                val text = fields[0]
                if (resultQuestion.matches(text)) {
                    domainFloors.merge(text + "\t" + fields[1], if (text.endsWith("吗")) 1000 else 500, ::maxOf)
                }
            }
        }
        val domainTexts = domainFloors.keys.associateBy { it.substringBefore('\t') }
        val domainPresent = mutableSetOf<String>()
        // Web phrases that never occur in the subtitle, chat or wiki corpora (2026-10-30: 173,590, mostly from
        // tencent.dict: 玻璃打砂机, 未满十八岁勿进, 训练家) are dropped: nine-key 600 fresh SMS clauses 397 → 399 right,
        // 26-key unchanged. List built by D:/CyIME-Data/eval/junk-sweep-20261030 (count_all.py, remove-A.txt).
        val removed = java.util.zip.GZIPInputStream(removedWords.inputStream()).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.toHashSet()
        }
        for (name in listOf("base", "ext", "tencent")) {
            val dict = File(root, "cn_dicts/$name.dict.yaml")
            var header = true
            val kept = dict.readLines().filter { line ->
                if (line.trim() == "...") header = false
                header || line.substringBefore('\t') !in removed
            }
            var body = false
            dict.writeText(kept.joinToString("\n", postfix = "\n") { line ->
                if (line.trim() == "...") body = true
                val fields = line.split('\t')
                // tencent has no code column (text, weight).
                val key = if (fields.size == 3) fields[0] + "\t" + fields[1] else domainTexts[fields[0]]
                val floor = if (body && fields.size in 2..3) key?.let(domainFloors::get) else null
                if (floor != null) domainPresent += key!!
                val weight = fields.last().toIntOrNull()
                if (floor == null || weight == null || weight >= floor) line
                else fields.dropLast(1).joinToString("\t") + "\t" + floor
            })
        }
        // The upstream corrector clears ordinary spelling annotations after the
        // translator has generated them. Let the host retain that real spelling
        // for responsive candidate UI without disabling pronunciation corrections.
        // Lowercase short acronyms (kfc → KFC): upstream derives lowercase codes only from four letters up, so
        // a two/three-letter acronym needed its capitals. Derive them too; cyime_acronym_filter keeps such
        // an acronym out of the first four candidates (kfc is also the Chinese abbreviation 开发出).
        val meltEng = File(root, "melt_eng.schema.yaml")
        val lowerFour = "  - derive/^.{4,}\$/\\L\$0/\n"
        check(meltEng.readText().split(lowerFour).size == 2) { "Upstream melt_eng lowercase derivation changed" }
        meltEng.writeText(meltEng.readText().replace(lowerFour, lowerFour + "  - derive/^[A-Z]{2,3}\$/\\L\$0/\n"))
        acronymFilter.copyTo(File(root, "lua/cyime_acronym_filter.lua"), overwrite = true)
        // English words the user typed by hand come back as candidates (EnglishWordMemory.kt).
        englishMemory.copyTo(File(root, "lua/cyime_en_memory.lua"), overwrite = true)
        // One complete syllable (an, ai, en) keeps its single characters ahead of initial-letter words.
        singleSyllable.copyTo(File(root, "lua/cyime_single_syllable.lua"), overwrite = true)
        // 中英混输（nihaohello → 你好hello），Rime 选项 cyime_mixed_input 打开才出候选；设置默认关。
        mixedInput.copyTo(File(root, "lua/cyime_mixed_input.lua"), overwrite = true)
        // 生僻字（瀺 chan…），Rime 选项 cyime_rare_chars 打开才出候选；设置默认关。表在下面生成。
        rareChars.copyTo(File(root, "lua/cyime_rare_chars.lua"), overwrite = true)
        val corrector = File(root, "lua/corrector.lua")
        val source = corrector.readText()
        val commentGate = "if env.keep_comment then"
        check(source.split(commentGate).size == 2) { "Upstream corrector comment gate changed" }
        corrector.writeText(source.replace(commentGate,
            "if env.keep_comment or env.engine.context:get_option(\"_cyime_candidate_comments\") then"))
        // Re-index the pinned English word sources; never lowercase the displayed text.
        // Exact whole-word digits only: no abbreviation/completion collisions with Chinese.
        // Words of the bundled frequency-ordered english.txt, upstream's curated internet/brand list and
        // two-letter capitals are offered in Chinese T9, weighted by frequency rank. Beyond the 3000 most
        // common only words of five letters and more, at weight 1, so they never come before a commoner
        // word on the same keys and short ones (zoo, xi) do not sit among Chinese candidates of short
        // inputs (terminal used to be missing on nine keys).
        val commonEnglish = file("src/main/assets/english.txt").readLines().map { it.trim().lowercase(java.util.Locale.ROOT) }
            .filter { it.isNotEmpty() }.withIndex().filter { it.index < 3000 || it.value.length >= 5 }
            .associate { it.value to maxOf(1, 3000 - it.index) }
        val english = linkedMapOf<String, String>()
        val english14 = linkedMapOf<String, String>()
        val digits = "22233344455566677778889999"
        // 14 keys: letter pairs share a key sent as its capital (qw → Q …); l and m are keys of their own.
        val keys14 = "ABCDEDGGUJJlmBOOQEATUCQZTZ"
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
                                // 14 keys have no digit keys in letter mode: letters only.
                                val letters = spelling.lowercase(java.util.Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }
                                if (letters.all { it in 'a'..'z' }) {
                                    val code14 = letters.map { keys14[it - 'a'] }.joinToString("")
                                    english14.putIfAbsent(fields[0] + "\t" + code14, fields[0] + "\t" + code14 + "\t" + weight)
                                }
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
        File(root, "cyime_k14_english.dict.yaml").writeText(
            "# Generated from bundled rime-ice en_dicts; licenses retained alongside sources.\n" +
            "---\nname: cyime_k14_english\nversion: \"1\"\nsort: by_weight\n...\n" +
            english14.values.joinToString("\n", postfix = "\n"))

        // Same engine, dictionary and options as Chinese26; only the keyboard arrangement differs.
        // A sentence-final particle (吧, 呗, 呢…, app/data/sentence_final_particles.txt) is penalised as the first
        // word of a composed sentence, and sentences may choose among four single-character homophones per
        // syllable (only one before, so 把地址给我 became 吧地址给我 once 吧 outweighed 把). Patched Poet /
        // ScriptTranslator, see docs/development/chat-base-weights-20261018.md.
        // Measure words (个, 只…, app/data/sentence_measure_words.txt) never start a sentence either (哥你好 → 个你好).
        val particles = (finalParticles.readLines() + measureWords.readLines()).map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        check(particles.isNotEmpty()) { "No sentence-final particles in ${finalParticles.name}" }
        // 简/繁 converts to Taiwan everyday forms (台灣, 計畫, 了解): assets/rime_chinese/opencc/cyime_s2tw.json,
        // built by tools/opencc/build_cyime_opencc.py. Upstream's s2t.json gives 臺灣 and was never bundled.
        // 时间, 日期 and 星期 are followed by the current time or date (librime-t9 datetime_filter.h), the same
        // filter as the nine-key and 14-key schemas; before traditionalize so 周六 converts with the rest.
        fun withDatetimeHints(schema: String) = schema.replace("\n    - simplifier@traditionalize",
            "\n    - cyime_datetime\n    - simplifier@traditionalize")
            .also { check(it.split("\n    - cyime_datetime\n").size == 2) { "Upstream traditionalize filter changed" } }
        fun taiwanForms(schema: String) = schema.replace("  opencc_config: s2t.json ", "  opencc_config: cyime_s2tw.json ")
            .also { check(it.contains("  opencc_config: cyime_s2tw.json ")) { "Upstream traditionalize config changed" } }
        val baseSchema = taiwanForms(File(root, "rime_ice.schema.yaml").readText()).replace("\ntranslator:\n",
            "\ntranslator:\n  enable_correction: true\n  cyime_neighbor_correction: true\n" +
            // A doubled letter typed once where syllables meet (chengong → 成功 second, after the typed 陈宫), shown
            // only when the corrected word is 20 times as common as the typed reading (docs/development/typo-correction-20261021.md).
            "  cyime_doubled_letter_correction: true\n" +
            // Two neighbouring letters typed swapped (lnu → 论) read as one edit; full pinyin only, not double pinyin.
            "  cyime_transposition_correction: true\n" +
            // One letter typed too many inside a syllable (zhonng → 中, shhi → 是), only as an extra key press
            // (same key twice or a neighbour); same 20x gate, never first (docs/development/typo-correction-20261021.md).
            "  cyime_extra_letter_correction: true\n" +
            // Typed letters that read only with an initial standing alone before the last syllable (zhe'b'zhe'b'g), when
            // neighbour corrections read them in full (zhen'zheng): such an initial weighs as a slip and the corrected
            // sentence comes first; long input is corrected from its start (docs/development/typo-correction-20261021.md).
            "  cyime_stray_initial_correction: true\n" +
            "  sentence_char_homophones: 4\n  sentence_position_penalty: -2.0\n" +
            "  sentence_position_final_only: [${particles.joinToString(", ")}]\n" +
            // 把/被 open a verb phrase and never end a sentence: shuoshuobei is 说说呗, not 说说被.
            "  sentence_position_initial_only: [把, 被]\n" +
            // Four sentences for t9/sentence_rescore to choose from (the menu still shows one); the cutoff
            // keeps Rime from dropping alternatives whose weight is a word penalty away.
            "  max_sentences: 4\n  sentence_cutoff_threshold: 100\n")
            .lineSequence().filterNot { it.contains("- lua_filter@*long_word_filter") }
            .flatMap { if (it.contains("- lua_filter@*autocap_filter")) sequenceOf(it, "    - lua_filter@*cyime_acronym_filter",
                "    - lua_filter@*cyime_single_syllable", "    - lua_filter@*cyime_en_memory")
                else if (it == "    - script_translator") sequenceOf(it, "    - lua_translator@*cyime_mixed_input",
                    "    - lua_translator@*cyime_rare_chars") else sequenceOf(it) }
            .joinToString("\n")
            .replace("  filters:\n", "  filters:\n    - cyime_candidate_policy\n")
            .let { withDatetimeHints(it) } +
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
            // Since 2026-10-20 the word model only reorders Rime's four sentences (sentence_rescore): the
            // joint decoder above cost +40% per key, the rescoring +5% for most of the sentence gain. 26-key
            // never passes the committed text, so the first word is not scored as a sentence start.
            "\nt9:\n  sentence_rescore: true\n  joint_decoder: false\n  joint_requires_ngram: true\n  source_budget: 16\n  pool_budget: 16\n" +
            "  dictionary_beam: 64\n  sentence_beam: 8\n  result_budget: 12\n  display_sentences: 1\n" +
            "  spelling_penalty: 0\n  word_penalty: 8.0\n  correction_penalty: -9.2\n  whole_input_phrases: 16\n" +
            "  prefer_user_entries: true\n  ngram_model: t9_ngram\n" +
            "  ngram_sentence_start: false\n  ngram_eos_weight: 0\n  ngram_user_weight: 0.3\n" +
            "  ngram_dictionary_prior: 0.02\n  ngram_merge_segmentations: false\n" +
            // CyLM (CyIME-Windows tools/cylm) puts its best sentence first when its files are in the Rime user
            // directory. Only the 装到我的设备 package carries them; without them the open fails once and Rime's
            // sentence stays (cyime_rime/t9_cylm_sentence.cc). Same files and settings as t9_pinyin.schema.yaml.
            // The lexicon is not named *.bin: Rime's deploy (CleanupTrash) moves every *.bin in the user directory to trash/.
            // Phone-fast tier "D" (cy1-p1e-7, no reranker, beam 8) with the trimmed 45 MB lexicon. On the tablet the
            // light tier with its reranker missed the 12 ms budget on 97% of keys, the half-size full model on all
            // of them, and D/E with the 114 MB lexicon added ~5 ms per key and ~280 MB over CyLM off (2026-10-26).
            // cylm_adapt.tsv is the user's own trigram layer (only in 装到我的设备 packages); nbest 16 and the typo
            // readings let the replacement rule in t9_cylm_sentence.cc compare Rime's sentence with CyLM's.
            "\ncylm:\n  lexicon: cylm_lexicon.cylx\n  model: cylm_model.lm\n  adapt: cylm_adapt.tsv\n" +
            "  params: \"mu=0.04;eos=0.6;abbrev_full=-4;incremental=1;beam=8;per_span=8;per_span_abbrev=16;per_span1=24;" +
            "nbest=4;t9_beam=6;t9_per_span=6;skip_inner=1;hit_cap=5000;word_cap=50000;id_cap=50000;dec_cap=25000;" +
            "nbest=16;typo=-3;typo_first=8.4;adapt_add=1.0\"\n" +
            // With CyLM started while Rime builds its sentence most keys find it done; a key waits at most 6 ms.
            "  budget_ms: 6\n"
        check(baseSchema.contains("cyime_neighbor_correction: true"))
        check(baseSchema.contains("    - lua_translator@*cyime_mixed_input\n    - lua_translator@*cyime_rare_chars\n")) {
            "Upstream rime_ice translator list changed"
        }
        // The 14-key and 注音 schemas (assets/rime_chinese) repeat these sentence rules; keep them the same list.
        for (schema in listOf("pinyin_14jian", "cyime_zhuyin")) check(file("src/main/assets/rime_chinese/$schema.schema.yaml").readText()
            .contains("  sentence_position_final_only: [${particles.joinToString(", ")}]\n")) {
            "$schema.schema.yaml sentence_position_final_only differs from app/data particle lists"
        }
        File(root, "rime_ice.schema.yaml").writeText(baseSchema)
        val doublePinyin = File(root, "double_pinyin_flypy.schema.yaml")
        doublePinyin.writeText(taiwanForms(doublePinyin.readText()).replace("\ntranslator:\n",
            "\ntranslator:\n  cyime_neighbor_correction: true\n")
            .replace("  filters:\n", "  filters:\n    - cyime_candidate_policy\n")
            .let { withDatetimeHints(it) }
            .replace("enable_correction: false", "enable_correction: true"))
        // Built-in letter arrangements (settings/BuiltinLetterLayouts.kt): same schema, own ID and name.
        for ((id, name) in listOf("pinyin_qwjrtk" to "QWJRTK（双拇指）", "pinyin_cyletix10" to "Cyletix10（实验）",
                "pinyin_colemak" to "Colemak", "pinyin_typewise" to "Typewise")) {
            File(root, "$id.schema.yaml").writeText(baseSchema
                .replace("schema_id: rime_ice", "schema_id: $id")
                .replace("name: 雾凇拼音", "name: $name"))
        }

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
                "单手键盘\tdan shou jian pan\t5000", "键盘高度\tjian pan gao du\t5000").joinToString("\n", postfix = "\n") +
            // Only words rime-ice lacks: a second copy of an existing entry is just more to look up.
            (domainFloors.keys - domainPresent).joinToString("\n", postfix = "\n") { it + "\t" + domainFloors.getValue(it) })
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
        // Rare characters (user 2026-10-30: 瀺 chan had no candidate). 8105.dict.yaml is only the 通用规范汉字表;
        // upstream's large table 41448.dict.yaml (Unihan kMandarin + zdic readings) ships disabled. Importing all of
        // it cost the nine-key replay +140% dictionary entries per key at the same first candidates, so the
        // characters 8105 lacks go to cyime_rare.txt instead, read by lua/cyime_rare_chars.lua for full-pinyin 26 keys
        // only while its switch is on (default off). Only the BMP (URO and Ext A, about 18,000): most Android fonts
        // have no glyphs for Ext B and beyond, which would show as empty boxes. Only readings 8105 already has (drops
        // 黁 nun). The large table itself is no longer bundled: the generated list is about half its size.
        val common = HashSet<String>()
        val commonCodes = HashSet<String>()
        var commonBody = false
        File(root, "cn_dicts/8105.dict.yaml").forEachLine { line ->
            if (!commonBody) { commonBody = line.trimEnd() == "..."; return@forEachLine }
            val fields = line.split('\t')
            if (!line.startsWith("#") && fields.size >= 2) { common += fields[0]; commonCodes += fields[1] }
        }
        val largeTable = File(root, "cn_dicts/41448.dict.yaml")
        var largeBody = false
        val rare = largeTable.readLines().filter { line ->
            if (!largeBody) { largeBody = line.trimEnd() == "..."; return@filter false }
            val fields = line.split('\t')
            val text = fields[0]
            !line.startsWith("#") && fields.size == 2 && text.codePointCount(0, text.length) == 1 &&
                text.codePointAt(0) < 0x10000 && Character.isIdeographic(text.codePointAt(0)) && text !in common &&
                fields[1] in commonCodes
        }
        check(rare.any { it == "瀺\tchan" } && rare.size > 15000) { "Upstream 41448.dict.yaml changed" }
        // Within a reading the large table goes by code point, Ext A (㙴) first; the URO ones (瀺) are the likelier.
        File(root, "cyime_rare.txt").writeText(rare.sortedBy { if (it.codePointAt(0) < 0x4E00) 1 else 0 }
            .joinToString("\n", postfix = "\n"))
        largeTable.delete()
        val iceDict = File(root, "rime_ice.dict.yaml")
        val othersImport = "  - cn_dicts/others   # 一些杂项\n"
        check(iceDict.readText().split(othersImport).size == 2) { "Upstream rime_ice import list changed" }
        iceDict.writeText(iceDict.readText().replace(othersImport, othersImport + "  - cyime_terms\n  - cyime_di\n"))
        // Minority readings for the joint decoder (t9/reading_table), same rule as tools/ime_lab/reading_table.py:
        // ln(count / count of the text's main reading) when below -1. The word model scores words, not readings,
        // so 的 (also di, as in 目的) took momodi from 默默地. The first imported table wins, as in Rime;
        // tencent has one reading per word and is skipped.
        val firstWeight = HashMap<String, HashMap<String, Double>>()
        val spelled = Regex("[a-z]+( [a-z]+)*")
        Regex("(?m)^  - ([^\\s#]+)").findAll(iceDict.readText().substringAfter("import_tables:").substringBefore("\n..."))
            .map { it.groupValues[1] }.filter { it != "cn_dicts/tencent" }.forEach { name ->
                var body = false
                File(root, "$name.dict.yaml").forEachLine { line ->
                    if (!body) { body = line.trimEnd() == "..."; return@forEachLine }
                    val fields = line.split('\t')
                    if (line.startsWith("#") || fields.size < 2 || !spelled.matches(fields[1])) return@forEachLine
                    firstWeight.getOrPut(fields[0]) { HashMap(2) }
                        .putIfAbsent(fields[1], fields.getOrNull(2)?.trim()?.toDoubleOrNull() ?: 0.0)
                }
            }
        val readings = firstWeight.flatMap { (text, codes) ->
            val top = codes.values.maxOrNull() ?: 0.0
            if (codes.size < 2 || top <= 0) emptyList() else codes.mapNotNull { (code, weight) ->
                val share = kotlin.math.ln(maxOf(weight, 1.0) / top)
                if (share < -1.0) "$text\t$code\t" + String.format(java.util.Locale.ROOT, "%.3f", share) else null
            }
        }.sorted()
        check(readings.any { it.startsWith("的\tdi\t") }) { "Minority reading 的 di not found" }
        File(root, "cyime_readings.txt").writeText(readings.joinToString("\n", postfix = "\n"))
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareChineseDictionaries) }
