package com.kingzcheung.xime.settings

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Plain-text word lists as other input methods export them, one word per line:
 * `词 拼音`, `拼音 词` (Sogou/QQ exports, `'ni'hao 你好`), `词<TAB>pin yin<TAB>权重` (Rime). Fields are
 * separated by tabs, spaces or commas; pinyin may be written with spaces, apostrophes or run
 * together (nihao). A count column is ignored: every word gets the same weight as SCEL imports.
 * Lines without pinyin are skipped and counted. UTF-8, UTF-16 (with BOM) and GB18030 are read.
 * A line without Chinese characters is an English word (`Ame`, `Team Spirit`, `Ame<TAB>ame<TAB>100`): its text
 * before the first tab, kept as written, goes to the English candidates of every keyboard.
 */
object TxtCellDictionary {
    private val word = Regex("[㐀-䶿一-鿿]{2,32}")
    private val letters = Regex("[a-zv' ]+")

    fun parse(bytes: ByteArray): ScelDictionary.Parsed {
        require(bytes.size <= ScelDictionary.MAX_BYTES) { "词库超过 16 MB" }
        var text = decode(bytes)
        // A Rime .dict.yaml: only the entries after its header.
        if (text.contains("\n...\n")) text = text.substringAfter("\n...\n")
        val entries = LinkedHashMap<Pair<String, String>, DictEntry>()
        val english = LinkedHashSet<String>()
        var skipped = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val latin = englishWord(line)
            if (latin != null) { english += latin; continue }
            val entry = parseLine(line)
            if (entry == null) { skipped++; continue }
            entries.putIfAbsent(entry.word to entry.code, entry)
            require(entries.size + english.size <= ScelDictionary.MAX_ENTRIES) { "词库超过 25 万条词语" }
        }
        require(entries.isNotEmpty() || english.isNotEmpty()) { "没有读到带拼音的词条（每行“词 拼音”或“拼音 词”）" }
        return ScelDictionary.Parsed("", entries.values.toList(), skipped, english.toList())
    }

    private val latin = Regex("[A-Za-z0-9][A-Za-z0-9 '._&+-]{0,62}")

    /** The English word of a line without Chinese characters, or null. A bare number or one letter is no word. */
    internal fun englishWord(line: String): String? {
        if (line.any { it.code > 0x7f }) return null
        val text = line.substringBefore('\t').trim().replace(Regex(" +"), " ")
        if (!latin.matches(text) || text.none(Char::isLetter)) return null
        return text.takeIf { CellDictionaryEnglish.letters(it).length >= 2 }
    }

    internal fun parseLine(line: String): DictEntry? {
        val fields = line.split('\t', ' ', ',', '，').filter { it.isNotEmpty() }
        val text = fields.firstOrNull { word.matches(it) } ?: return null
        val pinyin = fields.filter { it != text && it.any(Char::isLetter) }
            .joinToString(" ").lowercase().replace('ü', 'v').replace("u:", "v")
        if (pinyin.isEmpty() || !letters.matches(pinyin)) return null
        val code = syllables(pinyin, text.length) ?: return null
        return DictEntry(text, code, 100)
    }

    /** Space-separated syllables, one per character, or null when the pinyin does not spell them. */
    internal fun syllables(pinyin: String, count: Int): String? {
        // Rime spells 略/虐 lve/nve.
        val given = pinyin.split(' ', '\'').filter { it.isNotEmpty() }
            .map { if (it == "lue" || it == "nue") it.replace('u', 'v') else it }
        if (given.size == count && given.all { it in SYLLABLES }) return given.joinToString(" ")
        // Run together (nihao) or partly separated: split the letters into exactly `count` syllables.
        val run = given.joinToString("")
        val split = Array(count + 1) { arrayOfNulls<List<String>>(run.length + 1) }
        split[0][0] = emptyList()
        for (n in 0 until count) for (start in 0 until run.length) {
            val head = split[n][start] ?: continue
            for (end in start + 1..minOf(run.length, start + 6)) {
                val syllable = run.substring(start, end)
                if (syllable in SYLLABLES && split[n + 1][end] == null) split[n + 1][end] = head + syllable
            }
        }
        return split[count][run.length]?.joinToString(" ")
    }

    private fun decode(bytes: ByteArray): String {
        fun strict(charset: Charset, offset: Int) = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        val b = bytes.map { it.toInt() and 255 }
        return when {
            b.size >= 2 && b[0] == 0xFF && b[1] == 0xFE -> strict(Charsets.UTF_16LE, 2)
            b.size >= 2 && b[0] == 0xFE && b[1] == 0xFF -> strict(Charsets.UTF_16BE, 2)
            b.size >= 3 && b[0] == 0xEF && b[1] == 0xBB && b[2] == 0xBF -> strict(Charsets.UTF_8, 3)
            else -> runCatching { strict(Charsets.UTF_8, 0) }.getOrElse { strict(Charset.forName("GB18030"), 0) }
        }.replace("\r\n", "\n").replace('\r', '\n')
    }

    private val SYLLABLES = ("a ai an ang ao ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu ca cai can " +
        "cang cao ce cen ceng cha chai chan chang chao che chen cheng chi chong chou chu chua chuai chuan chuang chui " +
        "chun chuo ci cong cou cu cuan cui cun cuo da dai dan dang dao de dei den deng di dia dian diao die ding diu " +
        "dong dou du duan dui dun duo e ei en eng er fa fan fang fei fen feng fiao fo fou fu ga gai gan gang gao ge gei " +
        "gen geng gong gou gu gua guai guan guang gui gun guo ha hai han hang hao he hei hen heng hong hou hu hua huai " +
        "huan huang hui hun huo ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun ka kai kan kang kao ke kei " +
        "ken keng kong kou ku kua kuai kuan kuang kui kun kuo la lai lan lang lao le lei leng li lia lian liang liao lie " +
        "lin ling liu lo long lou lu luan lun luo lv lve ma mai man mang mao me mei men meng mi mian miao mie min ming " +
        "miu mo mou mu na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu nuan nun nuo " +
        "nv nve o ou pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu qi qia qian qiang qiao qie qin " +
        "qing qiong qiu qu quan que qun ran rang rao re ren reng ri rong rou ru rua ruan rui run ruo sa sai san sang sao " +
        "se sen seng sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan shuang shui shun shuo si " +
        "song sou su suan sui sun suo ta tai tan tang tao te tei teng ti tian tiao tie ting tong tou tu tuan tui tun tuo " +
        "wa wai wan wang wei wen weng wo wu xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun ya yan yang " +
        "yao ye yi yin ying yo yong you yu yuan yue yun za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao " +
        "zhe zhei zhen zheng zhi zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo")
        .split(' ').toHashSet()
}
