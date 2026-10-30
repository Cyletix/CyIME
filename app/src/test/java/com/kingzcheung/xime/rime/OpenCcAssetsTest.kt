package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 简/繁开关要真的能转：每个方案引用的 OpenCC 配置和它用到的 .ocd2 表都要随包，
 * 否则 librime 加载失败、静默不转（1.15.6 之前 rime_ice 的「繁」就是这样）。
 * 引擎实测见 tools/ime_lab/host_replay/TraditionalOutputProbe.java。
 */
class OpenCcAssetsTest {
    private val opencc = File("src/main/assets/rime_chinese/opencc")
    private val configPattern = Regex("""^\s*opencc_config:\s*(\S+\.json)""", RegexOption.MULTILINE)
    private val tablePattern = Regex(""""file":\s*"([^"]+)"""")

    private fun configsOf(text: String) = configPattern.findAll(text).map { it.groupValues[1] }.toList()

    @Test fun everyTraditionalizeConfigShipsWithItsTables() {
        val schemas = File("src/main/assets/rime_chinese").listFiles { f -> f.name.endsWith(".schema.yaml") }!!.toList()
        val referenced = schemas.flatMap { configsOf(it.readText()) }.toSet()
        assertTrue("九键、14 键应引用台湾正体配置", "cyime_s2tw.json" in referenced)
        // 26 键和双拼由 tasks-chinese.gradle.kts 从 rime-ice 生成，同样改成这张表。
        assertTrue(File("build-logic/tasks-chinese.gradle.kts").readText().contains("opencc_config: cyime_s2tw.json"))
        for (config in referenced + setOf("s2t.json", "s2tw.json", "s2hk.json", "cyime_s2tw.json")) {
            val file = File(opencc, config)
            assertTrue("缺少 $config", file.isFile)
            val tables = tablePattern.findAll(file.readText()).map { it.groupValues[1] }.toList()
            assertTrue("$config 没有引用任何表", tables.isNotEmpty())
            tables.forEach { assertTrue("$config 用到的 $it 没有随包", File(opencc, it).isFile) }
        }
    }

    @Test fun taiwanFormsComeLastInTheChain() {
        val chain = File(opencc, "cyime_s2tw.json").readText()
        assertTrue(chain.indexOf("CyimeTWPhrases.ocd2") > chain.indexOf("TWVariants.ocd2"))
        val forms = File("../tools/opencc/cyime_tw_phrases.txt").readLines().associate { it.substringBefore('\t') to it.substringAfter('\t') }
        assertEquals("台", forms["臺"])
        assertEquals("計畫", forms["計劃"])
        assertEquals("了解", forms["瞭解"])
    }
}
