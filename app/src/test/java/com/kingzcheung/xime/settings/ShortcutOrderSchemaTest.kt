package com.kingzcheung.xime.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 雾凇的时间日期快捷码（sj rq xq dt ts nl）和简拼同码（11-03 用户转来：打 sj 只出时间，不出 手机、收集）。
 * app/data/cyime_shortcut_order.lua 把第一个时间/日期放第 2 位（同九键 t9/date_translator/idx: 2），
 * 其余格式放在再往后 3 个词之后；只在整段输入是快捷码时运行，其他输入整个跳过。
 */
class ShortcutOrderSchemaTest {
    private val generated = File("build/generated/chinese-assets/rime_ice")
    private val filter = "    - lua_filter@*cyime_shortcut_order\n"

    @Test fun fullPinyinRunsTheFilterBeforeTheOtherReorderingFilters() {
        assertTrue(File(generated, "lua/cyime_shortcut_order.lua").isFile)
        val schema = File(generated, "rime_ice.schema.yaml").readText()
        assertTrue(schema.split(filter).size == 2)
        // 在简拼缩写、单音节、时间日期提示（cyime_datetime 看前 3 个候选里的 时间）之前，先把词放回前面。
        assertTrue(schema.indexOf("    - cyime_candidate_policy\n") < schema.indexOf(filter))
        assertTrue(schema.indexOf(filter) < schema.indexOf("    - lua_filter@*cyime_acronym_filter\n"))
        assertTrue(schema.indexOf(filter) < schema.indexOf("    - cyime_datetime\n"))
        assertTrue(schema.contains("    - lua_translator@*date_translator"))
    }

    @Test fun onlyShortcutCodesEnterTheFilter() {
        val lua = File("data/cyime_shortcut_order.lua").readText()
        assertTrue(lua.contains("local LEAD = 1"))
        assertTrue(lua.contains("local GAP = 3"))
        for (code in listOf("\"rq\"", "\"sj\"", "\"xq\"", "\"dt\"", "\"ts\"", "\"nl\"")) assertTrue(code, lua.contains(code))
        assertTrue(lua.contains("return { init = init, func = filter, tags_match = tags_match }"))
    }
}
