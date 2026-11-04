package com.kingzcheung.xime.contacts

/**
 * 通讯录插件的查找表（只在内存里，不写文件，不进备份和同步）。
 *
 * 人名按拼音找：全拼（zhangsan）、三个字以上的名字还能用首字母（ouyangxiaoming 或 oyxm）；
 * 九键按数字找（94264726）。英文名按字母找。一次最多给 [MAX_HITS] 个人。
 */
class ContactIndex private constructor(
    private val byLetters: Map<String, List<Entry>>,
    private val byDigits: Map<String, List<Entry>>,
    private val names: Set<String>,
    private val phones: Set<String>,
) {
    /** 一个联系人：上屏的名字和号码（号码已去掉空格、横线，+86 的手机号去掉 +86）。 */
    data class Entry(val name: String, val phones: List<String>)

    /** 读通讯录得到的原样数据，号码按「手机优先」排好。 */
    data class RawContact(val name: String, val phones: List<String>)

    val size: Int get() = names.size

    /** [letters]：去掉分隔符的拼音或字母；[digits]：九键数字。都对不上返回空表。 */
    fun lookup(letters: String?, digits: String?): List<Entry> {
        val a = letters?.let { byLetters[it] }.orEmpty()
        val b = digits?.let { byDigits[it] }.orEmpty()
        if (b.isEmpty()) return a.take(MAX_HITS)
        if (a.isEmpty()) return b.take(MAX_HITS)
        return (a + b).distinct().take(MAX_HITS)
    }

    /** 这段字就是某个联系人的名字或号码（剪贴板历史和同步用它把联系人挡在外面）。 */
    fun containsText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 40) return false
        if (trimmed in names) return true
        val phone = normalizePhone(trimmed) ?: return false
        return phone in phones
    }

    companion object {
        const val MAX_HITS = 3
        const val MAX_PHONES = 2
        private const val MAX_READINGS_PER_CHAR = 3
        private const val MAX_COMBOS = 16
        private const val MIN_HAN = 2
        private const val MAX_HAN = 6
        /** 首字母要三个字以上才用：两个字母（zs、ww）太常见，会挤掉平时打的词。 */
        private const val MIN_INITIALS = 3
        private const val MIN_LATIN = 3

        /** 多音字作姓时的读法。 */
        private val SURNAME_READINGS = mapOf(
            '曾' to "zeng", '单' to "shan", '解' to "xie", '区' to "ou", '仇' to "qiu", '朴' to "piao",
            '查' to "zha", '盖' to "ge", '华' to "hua", '秘' to "bi", '召' to "shao", '种' to "chong",
            '缪' to "miao", '覃' to "qin", '翟' to "zhai", '乐' to "yue", '繁' to "po", '能' to "nai",
            '句' to "gou", '员' to "yun", '黑' to "he", '隗' to "wei", '尉' to "yu", '万' to "wan",
            '长' to "chang", '重' to "chong", '沈' to "shen", '藏' to "zang", '宁' to "ning", '纪' to "ji",
            '那' to "na", '燕' to "yan", '应' to "ying", '石' to "shi", '强' to "qiang", '薄' to "bo",
            '柏' to "bai", '贾' to "jia", '任' to "ren", '过' to "guo", '叶' to "ye", '车' to "che",
            '冯' to "feng", '和' to "he", '朝' to "chao", '阚' to "kan", '晟' to "sheng", '洗' to "xian",
        )
        private val T9 = IntArray(26).also { map ->
            "22233344455566677778889999".forEachIndexed { i, c -> map[i] = c - '0' }
        }
        private val BRACKETS = Regex("[(（\\[【<《].*?[)）\\]】>》]")
        private val SPACES = Regex("\\s+")

        fun build(contacts: List<RawContact>, readings: (Int) -> String?): ContactIndex {
            val byLetters = HashMap<String, MutableList<Entry>>()
            val byDigits = HashMap<String, MutableList<Entry>>()
            val names = HashSet<String>()
            val phones = HashSet<String>()
            val seen = HashSet<Pair<String, List<String>>>()
            for (raw in contacts) {
                val name = cleanName(raw.name) ?: continue
                val numbers = raw.phones.mapNotNull(::normalizePhone).distinct()
                val entry = Entry(name, numbers.take(MAX_PHONES))
                if (!seen.add(entry.name to entry.phones)) continue
                names += name
                phones += numbers
                fun add(map: HashMap<String, MutableList<Entry>>, key: String) {
                    val list = map.getOrPut(key) { ArrayList(1) }
                    if (entry !in list) list += entry
                }
                for (key in letterKeys(name, readings)) {
                    add(byLetters, key.letters)
                    if (key.t9) add(byDigits, toT9(key.letters))
                }
            }
            return ContactIndex(byLetters, byDigits, names, phones)
        }

        /** 去掉括号里的备注（「张三(公司)」→「张三」）和多余空格；剩下空的返回 null。 */
        internal fun cleanName(raw: String): String? =
            raw.replace(BRACKETS, "").replace(SPACES, " ").trim().takeIf { it.isNotEmpty() }

        /** 号码只留数字和开头的 +；+86/0086 开头的 11 位手机号去掉区号；太短的不算号码。 */
        internal fun normalizePhone(raw: String): String? {
            val sb = StringBuilder(raw.length)
            for ((i, c) in raw.withIndex()) {
                when {
                    c in '0'..'9' -> sb.append(c)
                    c == '+' && sb.isEmpty() && i == raw.indexOfFirst { !it.isWhitespace() } -> sb.append(c)
                    c == ' ' || c == '-' || c == '(' || c == ')' || c == '.' || c == ' ' -> Unit
                    else -> return null
                }
            }
            var s = sb.toString()
            for (prefix in listOf("+86", "0086")) {
                if (s.startsWith(prefix) && s.length == prefix.length + 11 && s[prefix.length] == '1') s = s.substring(prefix.length)
            }
            return s.takeIf { it.count(Char::isDigit) >= 3 }
        }

        internal data class LetterKey(val letters: String, val t9: Boolean)

        internal fun letterKeys(name: String, readings: (Int) -> String?): List<LetterKey> {
            val compact = name.filterNot { it == ' ' || it == '·' || it == '•' || it == '・' }
            if (compact.isEmpty()) return emptyList()
            if (compact.all { it in 'a'..'z' || it in 'A'..'Z' || it == '-' || it == '.' || it == '\'' }) {
                val letters = compact.lowercase().filter { it in 'a'..'z' }
                return if (letters.length >= MIN_LATIN) listOf(LetterKey(letters, t9 = true)) else emptyList()
            }
            val codePoints = compact.codePoints().toArray()
            if (codePoints.size !in MIN_HAN..MAX_HAN || !codePoints.all(::isHan)) return emptyList()
            val perChar = codePoints.mapIndexed { i, cp ->
                // 名字的头一个字多半是姓：姓的读法排在前面，字典里的读法照样留着（「乐乐」还是 lele）。
                val surname = if (i == 0 && Character.charCount(cp) == 1) SURNAME_READINGS[cp.toChar()] else null
                val table = readings(cp)?.split('/')?.map(::plainPinyin)?.filter { it.isNotEmpty() }.orEmpty()
                (listOfNotNull(surname) + table).distinct().take(MAX_READINGS_PER_CHAR + 1)
                    .takeIf { it.isNotEmpty() } ?: return emptyList()
            }
            var combos = listOf(emptyList<String>())
            for (options in perChar) {
                combos = combos.flatMap { prefix -> options.map { prefix + it } }.take(MAX_COMBOS)
            }
            val keys = LinkedHashSet<LetterKey>()
            for (combo in combos) keys += LetterKey(combo.joinToString(""), t9 = true)
            if (perChar.size >= MIN_INITIALS) {
                for (combo in combos) keys += LetterKey(combo.joinToString("") { it.first().toString() }, t9 = true)
            }
            return keys.toList()
        }

        private fun isHan(cp: Int): Boolean =
            Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN

        /** 带声调的拼音 → 不带声调的小写字母，ü 写成 v。 */
        internal fun plainPinyin(toned: String): String {
            val sb = StringBuilder(toned.length)
            for (c in toned.trim()) {
                sb.append(when (c) {
                    'ā', 'á', 'ǎ', 'à' -> 'a'
                    'ē', 'é', 'ě', 'è', 'ê' -> 'e'
                    'ī', 'í', 'ǐ', 'ì' -> 'i'
                    'ō', 'ó', 'ǒ', 'ò' -> 'o'
                    'ū', 'ú', 'ǔ', 'ù' -> 'u'
                    'ǖ', 'ǘ', 'ǚ', 'ǜ', 'ü' -> 'v'
                    'ń', 'ň', 'ǹ' -> 'n'
                    'ḿ' -> 'm'
                    else -> if (c in 'a'..'z') c else if (c in 'A'..'Z') c.lowercaseChar() else return ""
                })
            }
            return sb.toString()
        }

        internal fun toT9(letters: String): String = buildString(letters.length) {
            for (c in letters) append(('0' + T9[c - 'a']))
        }

        /** 打字时的编码 → 查表用的字母：去掉音节分隔符；带汉字、数字等别的东西的返回 null。 */
        fun lettersOf(text: String): String? {
            if (text.isEmpty()) return null
            val sb = StringBuilder(text.length)
            for (c in text) {
                when (c) {
                    in 'a'..'z' -> sb.append(c)
                    in 'A'..'Z' -> sb.append(c.lowercaseChar())
                    '\'', ' ' -> Unit
                    else -> return null
                }
            }
            return sb.toString().takeIf { it.isNotEmpty() }
        }

        /** 九键编码 → 查表用的数字（只认 2–9，分隔符去掉）。 */
        fun digitsOf(text: String): String? {
            if (text.isEmpty()) return null
            val sb = StringBuilder(text.length)
            for (c in text) {
                when (c) {
                    in '2'..'9' -> sb.append(c)
                    '\'', ' ' -> Unit
                    else -> return null
                }
            }
            return sb.toString().takeIf { it.isNotEmpty() }
        }
    }
}
