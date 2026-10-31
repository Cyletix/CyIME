package com.kingzcheung.xime.settings

/** 面向用户的是布局；词典依赖继续安装/编译，但不重复占用语言菜单。 */
object CyimeInputDefaults {
    val recommended = listOf("rime_ice", "t9_pinyin")
    val legacyDefaults = setOf("wubi86", "wubi86_pinyin", "wubi86_trad", "wubi86_trad_pinyin")
    val dependencies = setOf("cyime_t9_english", "cyime_k14_english", "melt_eng", "radical_pinyin", "numbers", "handwriting")

    /**
     * 方案 × 布局的组合配置：所需的方案和布局都启用时自动启用，不作为独立条目单独开关。
     * 例如启用了小鹤双拼（或自然码、自定义双拼）和 14 键，就同时提供这个双拼方案的 14 键版本。
     * 自建的合并键布局（如自己排的 20 键）同样：这个双拼方案和那个布局都启用时提供双拼在那个布局上的版本。
     * 返回组合需要的方案（双拼方案在前）；不是组合配置时为 null。
     */
    fun companionNeeds(id: String): List<String>? = when {
        DoublePinyinSchemes.isK14(id) -> listOf(id.removeSuffix(DoublePinyinScheme.K14_SUFFIX), "pinyin_14jian")
        DoublePinyinSchemes.isOnLayout(id) -> listOf(DoublePinyinSchemes.baseId(id), DoublePinyinSchemes.layoutOf(id)!!)
        else -> null
    }

    fun isCompanion(id: String) = companionNeeds(id) != null

    /** 内置的 26 键双拼（小鹤、首道、自然码、智能ABC、拼音加加、微软）。 */
    val doublePinyin: List<String> get() = DoublePinyinScheme.BUILTINS.map { it.id }

    /**
     * 双拼默认显示在方案栏（用户 10-31：放在「其他已安装方案」里很多人找不到）：缺的内置双拼排在中文26键后面，
     * 已有顺序和当前方案不变。双拼方案文件还没生成时返回 null，留到下次再补。
     */
    fun withDoublePinyin(enabled: List<String>, available: Set<String>): List<String>? {
        if (!available.containsAll(doublePinyin)) return null
        val missing = doublePinyin.filterNot { it in enabled }
        val after = enabled.indexOf("rime_ice")
        val merged = if (after < 0) enabled + missing else enabled.take(after + 1) + missing + enabled.drop(after + 1)
        return withCompanions(merged, available)
    }

    fun withCompanions(ids: List<String>, available: Set<String>): List<String> {
        val base = ids.filterNot(::isCompanion)
        // 按双拼方案在列表里的顺序排列，结果不随已安装文件的枚举顺序变化。
        val implied = available.filter { id -> companionNeeds(id)?.all { it in base } == true }
            .sortedWith(compareBy({ base.indexOf(companionNeeds(it)!!.first()) }, { it }))
        return base + implied
    }

    fun canonicalIds(ids: List<String>, available: Set<String>): List<String> = ids.mapNotNull { id ->
        when {
            id in dependencies || id == InputModes.ENGLISH -> null
            id == "pinyin_simp" && "rime_ice" in available -> "rime_ice"
            id == "t9" && "t9_pinyin" in available -> "t9_pinyin"
            id == "jaroomaji" && "japanese" in available -> "japanese"
            else -> id
        }
    }.distinct()

    fun visibleSchema(id: String, available: Set<String>): Boolean =
        id !in dependencies && canonicalIds(listOf(id), available).singleOrNull() == id

    fun description(id: String): String? = when (id) {
        "t9_pinyin", "rime_ice", "pinyin_14jian", "double_pinyin_flypy" -> "默认雾凇词库 · 支持市场同名更新"
        "japanese", "japanese_kana" -> "共享日语词库 · 支持市场同名方案更新"
        else -> null
    }
}
