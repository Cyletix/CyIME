package com.kingzcheung.xime.settings

/** 面向用户的是布局；词典依赖继续安装/编译，但不重复占用语言菜单。 */
object CyimeInputDefaults {
    val recommended = listOf("rime_ice", "t9_pinyin")
    val legacyDefaults = setOf("wubi86", "wubi86_pinyin", "wubi86_trad", "wubi86_trad_pinyin")
    val dependencies = setOf("cyime_t9_english", "melt_eng", "radical_pinyin", "numbers", "handwriting")

    /**
     * 方案 × 布局的组合配置：所需的方案和布局都启用时自动启用，不作为独立条目单独开关。
     * 例如启用了小鹤双拼和 14 键，就同时提供 14 键小鹤双拼。
     */
    val companions = mapOf("double_pinyin_flypy_14jian" to setOf("double_pinyin_flypy", "pinyin_14jian"))

    fun withCompanions(ids: List<String>, available: Set<String>): List<String> {
        val base = ids.filterNot { it in companions }
        val implied = companions.filter { (id, needs) -> id in available && needs.all { it in base } }.keys
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
