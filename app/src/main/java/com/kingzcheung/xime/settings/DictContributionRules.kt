package com.kingzcheung.xime.settings

/**
 * 「贡献词库」从本机用户词库里挑词的规则和文件格式。纯函数，不碰 Android，单测见 DictContributionRulesTest。
 *
 * 用户词库（Rime 的 .userdb.txt 快照）每行是 `拼音码<TAB>文字<TAB>c=次数 d=权重 t=时间`。Rime 会把一次上屏的
 * 整句也当一条记下来，所以原件里有整句原文。这里只留：
 * - 2～[MAX_CHARS] 个字、全是汉字的词：整句、长短语、带数字字母符号的（电话、门牌、账号、网址）都不要；
 * - 选过至少 [MIN_COUNT] 次的：只打过一两次的名字、地址片段不出去；
 * - 不在 [select] 的 `own` 里的：用户自己加的短语和个人词库多半是名字、地址、签名；
 * - 不像人名的：姓开头（或小、老、阿开头）的 2～3 字词、复姓开头的 3～4 字词，方案词库里没有就当人名去掉；
 * - 不是全由数字字（一二三、零、两、百千万、幺）组成的：卡号、电话念出来的片段不要。
 * 密码框、敏感输入框、无痕模式本来就不学，用户词库里没有。
 * 不带权重、时间、设备编号和文件头；次数封顶 [MAX_COUNT]，最多 [MAX_WORDS] 个，按次数从多到少。
 */
object DictContributionRules {
    const val FORMAT = "cyime-dict-contribute"
    const val VERSION = 1
    const val MIN_CHARS = 2
    const val MAX_CHARS = 4
    const val MIN_COUNT = 3
    const val MAX_COUNT = 999
    const val MAX_WORDS = 3000

    /** [pinyin] 是空格分开的小写拼音；词库里的编码不是纯拼音时为空。 */
    data class Word(val text: String, val pinyin: String, val count: Int)

    /** 挑出来的词，以及各类去掉了多少条（页面上一行说明）。单字不算词，不计入。 */
    data class Selection(
        val words: List<Word>,
        val tooLong: Int = 0,
        val notHanzi: Int = 0,
        val tooFew: Int = 0,
        val own: Int = 0,
        val overLimit: Int = 0,
        val names: Int = 0,
        val numbers: Int = 0,
    ) {
        val dropped get() = tooLong + notHanzi + tooFew + own + overLimit + names + numbers
    }

    /**
     * 常见单姓（约占人口九成）和复姓。合并脚本 tools/dict_contribute/aggregate.py 直接读这两个字符串，
     * 改这里两边一起变。
     */
    const val SURNAMES = "王李张刘陈杨黄赵吴周徐孙马朱胡郭何高林罗郑梁谢宋唐许韩冯邓曹彭曾肖田董袁潘于蒋蔡余杜叶程苏魏吕丁" +
        "任沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付傅方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤常" +
        "温康施文牛樊葛邢安齐易乔伍庞颜倪庄聂章鲁岳翟殷詹申欧耿关兰焦俞左柳甘祝包宁尚符舒阮柯纪梅童凌毕单季裴霍涂成苗" +
        "谷盛曲翁冉骆蓝路游辛靳管柴蒙鲍华喻祁蒲房滕屈饶解牟艾尤阳时穆农司卓古吉缪简车项连芦麦褚娄窦戚岑景党宫费卜冷晏" +
        "席卫米柏宗瞿桂全佟应臧闵苟邬边卞姬师和仇栾隋商刁沙荣巫寇桑郎甄丛仲虞敖巩明佘池查麻苑迟邝"
    const val COMPOUND_SURNAMES = "欧阳 司马 诸葛 上官 东方 皇甫 慕容 令狐 司徒 夏侯 尉迟 长孙 宇文 端木 公孙 西门 独孤 南宫 轩辕 呼延"
    private const val NICKNAME_PREFIXES = "小老阿"
    private const val NUMBER_CHARS = "零〇一二三四五六七八九十两百千万亿幺壹贰叁肆伍陆柒捌玖拾佰仟"

    private val compoundSurnames = COMPOUND_SURNAMES.split(' ').toSet()

    /** 形状像人名：姓（或小、老、阿）＋1～2 字，复姓＋1～2 字。是不是真名要再查方案词库。 */
    fun looksLikeName(text: String): Boolean {
        if (text.length !in 2..4) return false
        if (text.length >= 3 && text.take(2) in compoundSurnames) return true
        if (text.length > 3) return false
        val first = text[0]
        return first in SURNAMES || (text.length == 2 && first in NICKNAME_PREFIXES)
    }

    fun isNumberWord(text: String) = text.all { it in NUMBER_CHARS }

    private val PINYIN = Regex("[a-z]+( [a-z]+)*")

    /** 中日韩统一汉字（基本区、扩展 A 和 B 以后各区）；〇、々和兼容汉字不算，和接收端、合并脚本一致。 */
    private fun isHan(codePoint: Int) = codePoint in 0x3400..0x9FFF || codePoint in 0x20000..0x3FFFF

    /** 「c=3 d=0.9 t=12」里的次数；删掉的词是负数，读不出来为 null。 */
    internal fun commitsOf(value: String): Int? =
        value.split(' ').firstOrNull { it.startsWith("c=") }?.removePrefix("c=")?.toIntOrNull()

    /**
     * [inDictionary]：这个词在方案词库里有没有（不算用户词库）。只对像人名的词查；查不到就当人名去掉。
     */
    fun select(userdbRows: Sequence<String>, own: Set<String>, inDictionary: (String) -> Boolean = { true }): Selection {
        val counts = LinkedHashMap<Pair<String, String>, Int>()
        var tooLong = 0
        var notHanzi = 0
        var tooFew = 0
        var ownWords = 0
        var names = 0
        var numbers = 0
        for (line in userdbRows) {
            if (line.isBlank() || line.startsWith("#")) continue
            val cols = line.split('\t')
            if (cols.size < 3) continue
            val text = cols[1]
            val commits = commitsOf(cols[2]) ?: continue
            if (commits <= 0 || text.isEmpty()) continue
            val chars = text.codePointCount(0, text.length)
            when {
                !text.codePoints().allMatch(::isHan) -> notHanzi++
                chars > MAX_CHARS -> tooLong++
                chars < MIN_CHARS -> Unit
                text in own -> ownWords++
                isNumberWord(text) -> numbers++
                commits < MIN_COUNT -> tooFew++
                looksLikeName(text) && !inDictionary(text) -> names++
                else -> {
                    val pinyin = cols[0].trim().takeIf { PINYIN.matches(it) }.orEmpty()
                    counts.merge(text to pinyin, commits, Int::plus)
                }
            }
        }
        val sorted = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }.thenBy { it.key.first })
        val words = sorted.take(MAX_WORDS).map { Word(it.key.first, it.key.second, it.value.coerceAtMost(MAX_COUNT)) }
        return Selection(words, tooLong, notHanzi, tooFew, ownWords, (sorted.size - MAX_WORDS).coerceAtLeast(0), names, numbers)
    }

    /** 分享或上传的文件：四行 `#` 开头的说明，之后每行 `词<TAB>拼音<TAB>次数`。 */
    fun encode(words: List<Word>, contributor: String, schema: String, appVersion: String): String = buildString {
        append("# ").append(FORMAT).append(' ').append(VERSION).append('\n')
        append("# contributor\t").append(contributor).append('\n')
        append("# schema\t").append(schema).append('\n')
        append("# app\t").append(appVersion).append('\n')
        words.forEach { append(it.text).append('\t').append(it.pinyin).append('\t').append(it.count).append('\n') }
    }
}
