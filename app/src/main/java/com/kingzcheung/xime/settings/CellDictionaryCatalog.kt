package com.kingzcheung.xime.settings

/** Curated links verified against Sogou's public index on 2026-10-02; no word data bundled. */
data class CellDictionaryOffer(val sourceId: Int, val name: String, val category: String, val entryCount: Int, val downloadBytes: Int) {
    val id: String get() = "sogou_$sourceId"
    val sourceUrl: String get() = "https://pinyin.sogou.com/dict/detail/index/$sourceId"
    val downloadUrl: String get() = "https://pinyin.sogou.com/d/dict/download_cell.php?id=$sourceId&name=$id"
}

object CellDictionaryCatalog {
    const val SOURCE_URL = "https://pinyin.sogou.com/dict/"
    val offers = listOf(
        CellDictionaryOffer(807, "全国省市区县地名", "城市信息大全", 2595, 77674),
        CellDictionaryOffer(11508, "风景名胜精选", "城市信息大全", 1279, 53140),
        CellDictionaryOffer(73918, "王者荣耀", "电子游戏", 740, 33810),
        CellDictionaryOffer(53593, "英雄联盟", "电子游戏", 1173, 45970),
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
