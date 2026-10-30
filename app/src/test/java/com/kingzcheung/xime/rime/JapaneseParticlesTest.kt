package com.kingzcheung.xime.rime

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * 助词按发音输入：wa→は、e→へ、o→を。上游词库只有 ha/he/wo 的写法，
 * 所以单打 wa 没有は，整句也只会拼成 私わ。补充表作为方案的 translator pack 加载。
 */
class JapaneseParticlesTest {
    private fun asset(name: String): File {
        val path = "src/main/assets/rime_japanese/$name"
        return listOf(File(path), File("app/$path")).first { it.isFile }
    }

    private fun entries(name: String) = asset(name).readLines()
        .map { it.split('\t') }.filter { it.size == 3 && it[2].toDoubleOrNull() != null }

    @Test fun `every japanese schema loads the particle pack`() {
        listOf("japanese", "japanese_kana", "jaroomaji").forEach { schema ->
            val text = asset("$schema.schema.yaml").readText().replace("\r\n", "\n")
            assertTrue(schema, Regex("translator:\n  dictionary: jaroomaji\n(  #.*\n)?  packs:\n    - cyime_japanese_particles\n")
                .containsMatchIn(text))
        }
        assertTrue(asset("cyime_japanese_particles.dict.yaml").readText().contains("\nname: cyime_japanese_particles\n"))
    }

    @Test fun `pronounced particles outrank the same-code kana but keep their usual spelling`() {
        val particles = entries("cyime_japanese_particles.dict.yaml")
        val kana = entries("jaroomaji.kana_kigou.dict.yaml")
        fun weight(rows: List<List<String>>, text: String, code: String) =
            rows.single { it[0] == text && it[1] == code }[2].toDouble()
        mapOf("は" to ("wa" to "わ"), "へ" to ("e" to "え"), "を" to ("o" to "お")).forEach { (particle, pair) ->
            val (code, kanaText) = pair
            assertTrue("$particle $code", weight(particles, particle, code) > weight(kana, kanaText, code))
        }
        // 原有写法不变：ha/he/wo 仍是は/へ/を，wa/e/o 的假名仍在
        assertEquals(80000.0, weight(kana, "は", "ha"), 0.0)
        assertEquals(80000.0, weight(kana, "わ", "wa"), 0.0)
        // 不是助词的は不读 wa：只收这里列出的固定搭配
        assertTrue(particles.all { it[0].endsWith("は") || it[0].startsWith("へ") || it[0] in setOf("を", "ではない") })
    }

    @Test fun `pack codes only use syllables the main dictionary spells`() {
        val syllables = entries("jaroomaji.kana_kigou.dict.yaml").flatMap { it[1].split(' ') }.toSet()
        entries("cyime_japanese_particles.dict.yaml").forEach { row ->
            row[1].split(' ').forEach { assertTrue("${row[0]}: $it", it in syllables) }
        }
    }
}
