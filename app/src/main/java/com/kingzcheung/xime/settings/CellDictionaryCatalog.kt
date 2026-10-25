package com.kingzcheung.xime.settings

/** Curated links verified against Sogou's public index on 2026-10-02; no word data bundled. */
data class CellDictionaryOffer(val sourceId: Int, val name: String, val category: String, val entryCount: Int, val downloadBytes: Int) {
    val id: String get() = "sogou_$sourceId"
    val sourceUrl: String get() = "https://pinyin.sogou.com/dict/detail/index/$sourceId"
    val downloadUrl: String get() = "https://pinyin.sogou.com/d/dict/download_cell.php?id=$sourceId&name=$id"
}

/**
 * Dictionaries shipped in the APK: assets/cell_dictionaries/cell_<id>.dict.yaml, and cell_<id>.en.txt for
 * their English words. Only [defaultOn] ones are switched on by themselves (once).
 */
data class BuiltinCellDictionary(
    val id: String, val name: String, val category: String, val sourceUrl: String = "", val defaultOn: Boolean = false,
) {
    val asset: String get() = "cell_dictionaries/cell_$id.dict.yaml"
    val englishAsset: String get() = "cell_dictionaries/cell_$id.en.txt"
}

object CellDictionaryCatalog {
    const val BUILTIN_CATEGORY = "内置词库"
    const val AI_CATEGORY = "编程与 AI"
    // Built-ins share the big categories of the Sogou offers, so a category lists both.
    val builtins = listOf(
        BuiltinCellDictionary("builtin_arknights", "明日方舟", "电子游戏", "https://wiki.arkrec.com/", defaultOn = true),
        // tools/dictionaries/topics/build_topic_dict.py; Chinese words with their English names and IDs.
        BuiltinCellDictionary("builtin_dota2", "Dota2", "电子游戏"),
        BuiltinCellDictionary("builtin_cs2", "CS2 / CSGO", "电子游戏"),
        // Gacha-game slang shared by all of them; each game's own words are its Sogou dictionary below.
        BuiltinCellDictionary("builtin_gacha", "二次元手游通用（抽卡、保底、歪了…）", "电子游戏"),
        BuiltinCellDictionary("builtin_genshin", "原神", "电子游戏"),
        BuiltinCellDictionary("builtin_hsr", "崩坏：星穹铁道", "电子游戏"),
        BuiltinCellDictionary("builtin_wuwa", "鸣潮", "电子游戏"),
        BuiltinCellDictionary("builtin_endfield", "明日方舟：终末地", "电子游戏"),
        BuiltinCellDictionary("builtin_zzz", "绝区零", "电子游戏"),
        BuiltinCellDictionary("builtin_honkai3", "崩坏3", "电子游戏"),
        BuiltinCellDictionary("builtin_bluearchive", "蔚蓝档案", "电子游戏"),
        BuiltinCellDictionary("builtin_onmyoji", "阴阳师", "电子游戏"),
        BuiltinCellDictionary("builtin_programming", "编程常用", AI_CATEGORY),
        BuiltinCellDictionary("builtin_llm", "大模型", AI_CATEGORY),
        BuiltinCellDictionary("builtin_ml", "机器学习", AI_CATEGORY),
        BuiltinCellDictionary("builtin_math", "数学", "自然科学"),
        BuiltinCellDictionary("builtin_wenshi", "文史", "人文科学"),
        BuiltinCellDictionary("builtin_movie", "电影", "娱乐"),
        BuiltinCellDictionary("builtin_anime", "动漫", "娱乐"),
    )
    const val SOURCE_URL = "https://pinyin.sogou.com/dict/"
    val offers = listOf(
        CellDictionaryOffer(807, "全国省市区县地名", "城市信息大全", 2595, 77674),
        CellDictionaryOffer(11508, "风景名胜精选", "城市信息大全", 1279, 53140),
        CellDictionaryOffer(73918, "王者荣耀", "电子游戏", 740, 33810),
        CellDictionaryOffer(53593, "英雄联盟", "电子游戏", 1173, 45970),
        // One dictionary per game, so only the games someone plays are switched on (counts and sizes from each
        // detail page, downloads checked, 2026-10-24). Topics the built-ins cover are not offered twice.
        CellDictionaryOffer(78390, "碧蓝航线", "电子游戏", 3375, 128008),
        CellDictionaryOffer(83081, "少女前线", "电子游戏", 433, 23892),
        CellDictionaryOffer(169094, "恋与深空", "电子游戏", 385, 22206),
        CellDictionaryOffer(171456, "无限暖暖", "电子游戏", 961, 44810),
        CellDictionaryOffer(158615, "光·遇", "电子游戏", 378, 21480),
        CellDictionaryOffer(83711, "第五人格", "电子游戏", 326, 19556),
        CellDictionaryOffer(135643, "永劫无间", "电子游戏", 119, 13178),
        CellDictionaryOffer(177368, "三角洲行动", "电子游戏", 323, 19484),
        CellDictionaryOffer(174142, "守望先锋：归来", "电子游戏", 596, 29308),
        CellDictionaryOffer(172302, "金铲铲之战", "电子游戏", 464, 25162),
        CellDictionaryOffer(72606, "炉石传说", "电子游戏", 2032, 80850),
        CellDictionaryOffer(171005, "魔兽世界", "电子游戏", 48687, 1796992),
        CellDictionaryOffer(61481, "我的世界（Minecraft）", "电子游戏", 1576, 64688),
        CellDictionaryOffer(15203, "物理词汇大全", "自然科学", 13107, 455528),
        CellDictionaryOffer(15205, "化学化工词汇大全", "自然科学", 13264, 479284),
        CellDictionaryOffer(15097, "成语俗语", "人文科学", 46785, 1645056),
        CellDictionaryOffer(2, "古诗词名句", "人文科学", 16948, 632054),
        CellDictionaryOffer(15128, "法律词汇大全", "社会科学", 4560, 171542),
        CellDictionaryOffer(15127, "财经金融词汇大全", "社会科学", 11379, 408968),
        CellDictionaryOffer(15117, "计算机词汇大全", "工程与应用科学", 10300, 363014),
        CellDictionaryOffer(15118, "建筑词汇大全", "工程与应用科学", 7479, 260250),
        CellDictionaryOffer(15149, "农业词汇大全", "农林渔畜", 8874, 289030),
        CellDictionaryOffer(1345, "林业树种名词库", "农林渔畜", 4111, 143186),
        CellDictionaryOffer(15125, "医学词汇大全", "医学", 90047, 3747198),
        CellDictionaryOffer(20664, "中医中药大全", "医学", 28428, 994272),
        CellDictionaryOffer(15141, "绘画美术词汇大全", "艺术", 6317, 199374),
        CellDictionaryOffer(20649, "摄影大全", "艺术", 19627, 711412),
        CellDictionaryOffer(15191, "篮球", "运动休闲", 2237, 89004),
        CellDictionaryOffer(15188, "足球", "运动休闲", 8929, 333554),
        CellDictionaryOffer(15201, "饮食大全", "生活", 6918, 251884),
        CellDictionaryOffer(15183, "旅游词汇大全", "生活", 2902, 110862),
        CellDictionaryOffer(20656, "日剧、动漫大全", "娱乐", 15544, 539902),
        CellDictionaryOffer(15153, "汽车词汇大全", "娱乐", 2401, 93744),
    )
}
