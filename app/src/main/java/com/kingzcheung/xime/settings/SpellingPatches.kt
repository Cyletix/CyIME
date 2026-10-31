package com.kingzcheung.xime.settings

import android.content.Context
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import java.io.File

/**
 * 模糊音（中文全拼 26 键；另开「模糊音也用于双拼」时双拼 26 键及其自建合并键布局版本）和双拼邻键纠错（双拼 26 键）。改的是方案的拼写规则或翻译器配置，只能写进 `<方案>.custom.yaml` 再部署；
 * 这里只增删自己标记的一段，个人词库、分类词库、短语等其他补丁原样保留。
 * 别人提的需求（B 站评论 10-31），默认全关；全关时不写任何文件，九键、14 键、注音不碰。
 */
object SpellingPatches {
    const val FUZZY_KEY = "fuzzy_pinyin"
    /** 模糊音也用于双拼 26 键：单独的开关，默认关，原来开着模糊音的人双拼照旧（用户 10-31）。 */
    const val FUZZY_DOUBLE_PINYIN_KEY = "fuzzy_pinyin_double_pinyin"
    /** 与 KeyboardInputPreferences.doublePinyinCorrection 同一个键：键盘按它决定是否把键位邻接表交给纠错。 */
    const val DOUBLE_PINYIN_CORRECTION_KEY = "double_pinyin_neighbor_correction"
    /** 设置页写好补丁后递增；输入法服务据此重新部署并切回当前方案。 */
    const val REVISION = "spelling_patches_revision"
    private const val START = "# BEGIN CYIME SPELLING"
    private const val END = "# END CYIME SPELLING"
    private fun YamlMap.node(key: String): YamlNode? = entries.entries.firstOrNull { it.key.content == key }?.value

    /**
     * 一组模糊音：两个方向都派生（打 z 也出 zh 的字，打 zh 也出 z 的字），写法同雾凇方案注释里的模糊音。
     * an/ang 不碰 ian、uan，它们各有一组。
     */
    enum class Fuzzy(val id: String, val title: String, val example: String, val rules: List<String>) {
        Z("z_zh", "z 和 zh", "zi 也出“知”，zhi 也出“字”", listOf("derive/^zh/z/", "derive/^z([^h])/zh\$1/")),
        C("c_ch", "c 和 ch", "ci 也出“吃”，chi 也出“次”", listOf("derive/^ch/c/", "derive/^c([^h])/ch\$1/")),
        S("s_sh", "s 和 sh", "si 也出“是”，shi 也出“四”", listOf("derive/^sh/s/", "derive/^s([^h])/sh\$1/")),
        NL("n_l", "n 和 l", "nan 也出“蓝”，lan 也出“南”", listOf("derive/^n/l/", "derive/^l/n/")),
        FH("f_h", "f 和 h", "fu 也出“湖”，hu 也出“福”", listOf("derive/^f/h/", "derive/^h/f/")),
        RL("r_l", "r 和 l", "ruo 也出“落”，luo 也出“弱”", listOf("derive/^r/l/", "derive/^l/r/")),
        AN("an_ang", "an 和 ang", "fan 也出“方”，fang 也出“饭”", listOf("derive/(^|[^iu])ang$/\$1an/", "derive/(^|[^iu])an$/\$1ang/")),
        EN("en_eng", "en 和 eng", "chen 也出“成”，cheng 也出“陈”", listOf("derive/eng$/en/", "derive/en$/eng/")),
        IN("in_ing", "in 和 ing", "xin 也出“星”，xing 也出“新”", listOf("derive/ing$/in/", "derive/in$/ing/")),
        IAN("ian_iang", "ian 和 iang", "xian 也出“想”，xiang 也出“先”", listOf("derive/iang$/ian/", "derive/ian$/iang/")),
        UAN("uan_uang", "uan 和 uang", "zhuan 也出“装”，zhuang 也出“专”", listOf("derive/uang$/uan/", "derive/uan$/uang/")),
    }

    fun fuzzy(context: Context): Set<Fuzzy> {
        val ids = SettingsPreferences.getPrefsPublic(context).getStringSet(FUZZY_KEY, null).orEmpty()
        return Fuzzy.entries.filterTo(linkedSetOf()) { it.id in ids }
    }

    fun setFuzzy(context: Context, item: Fuzzy, enabled: Boolean) {
        val ids = fuzzy(context).mapTo(HashSet()) { it.id }
        if (enabled) ids += item.id else ids -= item.id
        SettingsPreferences.getPrefsPublic(context).edit().putStringSet(FUZZY_KEY, ids).apply()
    }

    fun fuzzyDoublePinyin(context: Context): Boolean =
        SettingsPreferences.getPrefsPublic(context).getBoolean(FUZZY_DOUBLE_PINYIN_KEY, false)

    fun setFuzzyDoublePinyin(context: Context, enabled: Boolean) {
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(FUZZY_DOUBLE_PINYIN_KEY, enabled).apply()
    }

    fun doublePinyinCorrection(context: Context): Boolean =
        SettingsPreferences.getPrefsPublic(context).getBoolean(DOUBLE_PINYIN_CORRECTION_KEY, false)

    /**
     * 中文全拼 26 键：雾凇拼音、内置字母排列和自建布局。自建合并键布局把字母换成代表键的规则在拼写规则最后，
     * 模糊音排在最前，仍按拼音派生。14/17/18 键、九键各有自己的拼写规则，不加。
     */
    internal fun fullPinyinSchemas(context: Context): List<String> = (listOf("rime_ice") + BuiltinLetterLayouts.ids +
        runCatching { CustomKeyboardLayouts.load(context).map { it.id } }.getOrDefault(emptyList())).distinct()

    /**
     * 双拼 26 键：小鹤和其他内置、自定义双拼。九键、14 键版本不加（一个键不止一个字母）。
     * 小鹤方案构建时已带 cyime_neighbor_correction，其他双拼由它生成，同样带着；缺的只是 enable_correction。
     */
    internal fun doublePinyinSchemas(context: Context): List<String> = (DoublePinyinScheme.BUILTINS.map { it.id } +
        runCatching { DoublePinyinSchemes.load(context).map { it.id } }.getOrDefault(emptyList())).distinct()

    /**
     * 双拼在自建合并键布局（如 20 键）上的版本：拼写规则是 26 键双拼的规则再接合并键派生，模糊音照样排在最前。
     * 这些版本按设计不开邻键纠错（[DoublePinyinScheme.layoutSchema]），只写模糊音。
     */
    internal fun doublePinyinLayoutVariants(rimeDir: File): List<String> = rimeDir.listFiles().orEmpty()
        .mapNotNull { file -> file.name.removeSuffix(".schema.yaml").takeIf { file.isFile && file.name.endsWith(".schema.yaml") } }
        .filter { DoublePinyinScheme.splitLayoutVariant(it) != null }.sorted()

    /** 现在用的是双拼 26 键或它在自建布局上的版本：模糊音设置据此提醒打开「模糊音也用于双拼」。 */
    fun usingDoublePinyin(context: Context): Boolean {
        val id = SettingsPreferences.getCurrentSchema(context)
        return id in doublePinyinSchemas(context) || DoublePinyinScheme.splitLayoutVariant(id) != null
    }

    /** 设置页改完开关后在后台线程调用：写补丁，有改动就通知输入法重新部署。 */
    @Synchronized fun apply(context: Context) {
        if (!sync(context)) return
        // 输入法服务没在运行时，下次启动按部署 hash 发现补丁变了再部署，不走“已准备好”的捷径。
        SettingsPreferences.setDeploymentDone(context, false)
        val prefs = SettingsPreferences.getPrefsPublic(context)
        prefs.edit().putLong(REVISION, prefs.getLong(REVISION, 0L) + 1L).apply()
    }

    /**
     * 按当前开关写各方案的补丁；内容没变不写（不触发重新部署）。每次初始化也调用，
     * 新建的布局和随版本更新的方案拼写规则都跟上。返回是否改了文件。
     */
    @Synchronized fun sync(context: Context, rimeDir: File = SchemaManager.getRimeDir(context)): Boolean {
        val fuzzy = fuzzy(context)
        val correction = doublePinyinCorrection(context)
        val doublePinyinFuzzy = if (fuzzyDoublePinyin(context)) fuzzy else emptySet()
        var changed = false
        for (id in fullPinyinSchemas(context)) {
            changed = write(rimeDir, id) { schema -> listOf(fuzzyPart(schema, fuzzy) ?: return@write null) } || changed
        }
        // 双拼：拼写规则先按全拼派生模糊音，再换成双拼编码（同雾凇双拼方案注释里的位置），所以两段写在同一个文件里。
        for (id in doublePinyinSchemas(context)) {
            changed = write(rimeDir, id) { schema ->
                listOf(fuzzyPart(schema, doublePinyinFuzzy) ?: return@write null,
                    Part(CORRECTION, if (correction) listOf("\"$CORRECTION\": true") else emptyList()))
            } || changed
        }
        for (id in doublePinyinLayoutVariants(rimeDir)) {
            changed = write(rimeDir, id) { schema -> listOf(fuzzyPart(schema, doublePinyinFuzzy) ?: return@write null) } || changed
        }
        return changed
    }

    private const val ALGEBRA = "speller/algebra"
    private const val CORRECTION = "translator/enable_correction"

    /** 补丁里的一个键：[lines] 为空表示不写这个键。 */
    private class Part(val key: String, val lines: List<String>)

    /** 读不出方案的拼写规则时为 null（整个文件保持原样）。 */
    private fun fuzzyPart(schema: () -> String, fuzzy: Set<Fuzzy>): Part? =
        if (fuzzy.isEmpty()) Part(ALGEBRA, emptyList()) else algebraOf(schema())?.let { Part(ALGEBRA, fuzzyLines(it, fuzzy)) }

    /** 补丁内容：模糊音派生排在原有拼写规则前面（同雾凇注释的位置），整张规则表一起写。 */
    internal fun fuzzyLines(algebra: List<String>, items: Set<Fuzzy>): List<String> =
        listOf("\"speller/algebra\":") + (items.flatMap { it.rules } + algebra).map { "  - " + quote(it) }

    internal fun algebraOf(schema: String): List<String>? {
        val root = runCatching { SchemaManager.yaml.parseToYamlNode(schema) }.getOrNull() as? YamlMap ?: return null
        val list = ((root.node("speller") as? YamlMap)?.node("algebra") as? YamlList) ?: return null
        return list.items.map { (it as? YamlScalar)?.content ?: return null }
    }

    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /**
     * [parts] 返回 null 表示读不出方案，保持原样；全空表示去掉自己的那一段。
     * 方案自定义文件在我们这段之外已经改了同一个键时不写这个键，以用户自己写的为准；别的键照写。
     */
    private fun write(rimeDir: File, id: String, parts: (schema: () -> String) -> List<Part>?): Boolean {
        val schema = File(rimeDir, "$id.schema.yaml").takeIf { it.isFile } ?: return false
        val custom = File(rimeDir, "$id.custom.yaml")
        val original = if (custom.isFile) custom.readText() else ""
        val outside = withoutBlock(original)
        val wanted = (parts { schema.readText() } ?: return false)
            .filterNot { it.lines.isNotEmpty() && outside.contains(it.key) }.flatMap { it.lines }
        val updated = patch(original, wanted) ?: return false
        if (updated == original) return false
        if (updated.lineSequence().none { it.isNotBlank() && !it.trimStart().startsWith('#') && it.trim() != "patch:" }) {
            // 只剩空的 patch: 时 librime 会当成无效补丁：文件是我们写的这一段撑起来的，直接删掉。
            return custom.delete()
        }
        CellDictionaryStore.writeAtomic(custom, updated)
        return true
    }

    private val managed = Regex("(?m)^[ \\t]*${Regex.escape(START)}\\r?\\n[\\s\\S]*?^[ \\t]*${Regex.escape(END)}(?:\\r?\\n|$)")

    internal fun withoutBlock(original: String): String = original.replace(managed, "")

    /** 去掉旧的一段，[lines] 不空时紧接在 `patch:` 下面写新的一段。写出的不是合法 YAML 时返回 null。 */
    internal fun patch(original: String, lines: List<String>): String? {
        val starts = original.lineSequence().count { it.trim() == START }
        if (starts != original.lineSequence().count { it.trim() == END } || starts > 1) return null
        val base = withoutBlock(original)
        if (lines.isEmpty()) return base
        val newline = if (original.contains("\r\n")) "\r\n" else "\n"
        val patchLine = Regex("(?m)^patch:[ \\t]*(?:#[^\\r\\n]*)?(?:\\r?\\n|$)").find(base)
        val indentation = patchLine?.let { line ->
            Regex("(?m)^([ ]+)\\S").find(base.substring(line.range.last + 1))?.groupValues?.get(1)
        } ?: "  "
        val block = "$indentation$START$newline" + lines.joinToString("") { "$indentation$it$newline" } +
            "$indentation$END$newline"
        val output = if (patchLine == null) {
            val head = base.trimEnd().removeSuffix("...").trimEnd()
            (if (head.isEmpty()) "" else head + newline) + "patch:$newline$block"
        } else {
            val at = patchLine.range.last + 1
            val before = base.substring(0, at)
            (if (before.endsWith("\n")) before else before + newline) + block + base.substring(at)
        }
        return output.takeIf { runCatching { SchemaManager.yaml.parseToYamlNode(it) }.isSuccess }
    }
}
