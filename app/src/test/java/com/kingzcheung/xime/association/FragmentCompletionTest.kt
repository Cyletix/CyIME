package com.kingzcheung.xime.association

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 模型切出的半个词（夸、预）按占优的下一个字补成完整词；独立单字和分散分布不拼长。 */
class FragmentCompletionTest {
    private fun c(text: String, score: Float) = AssociationCandidate(text, score)

    private val next = mapOf(
        "谢谢你的夸" to listOf(c("奖", 9f), c("赞", 5f), c("了", 4f)),
        "谢谢你的夸奖" to listOf(c("！", 6f), c("和", 5.5f), c("，", 5f)),
        "谢谢你的关" to listOf(c("心", 7f), c("注", 6.5f), c("照", 5f)),
        "谢谢你的帮" to listOf(c("助", 10f), c("忙", 4f)),
        "天气预" to listOf(c("报", 8f), c("警", 3f)),
        "天气预报" to listOf(c("说", 3f), c("显", 2.8f)),
        "我很" to listOf(c("好", 3f), c("开", 2.9f), c("喜", 2.7f)),
    )
    private fun complete(context: String, candidates: List<AssociationCandidate>) = runBlocking {
        FragmentCompletion.complete(context, candidates) { text, _ -> next[text].orEmpty() }
    }

    @Test fun dominantNextCharacterCompletesTheWord() {
        val result = complete("谢谢你的", listOf(c("夸", 6f), c("帮助", 5f), c("关", 4f)))
        // 夸→夸奖（下一步是标点，停止）；关的下一字心/注接近，保留原样。
        assertEquals(listOf("夸奖", "帮助", "关"), result.map { it.text })
        assertEquals(6f, result[0].score, 0f)
    }

    @Test fun completionStopsAfterTheWordEnds() {
        assertEquals(listOf("预报"), complete("天气", listOf(c("预", 3f))).map { it.text })
    }

    @Test fun standaloneCharactersWithSpreadContinuationsStay() {
        assertEquals(listOf("很"), complete("我", listOf(c("很", 3f))).map { it.text })
        assertEquals(listOf("很好"), complete("我", listOf(c("很好", 3f))).map { it.text })
    }

    @Test fun completedDuplicateKeepsTheHigherRankedCandidate() {
        val result = complete("谢谢你的", listOf(c("帮助", 5f), c("帮", 4f), c("夸", 3f)))
        assertEquals(listOf("帮助", "夸奖"), result.map { it.text })
    }

    @Test fun completionStopsAtTheTimeBudget() {
        var now = 0L
        var peeks = 0
        val result = runBlocking {
            FragmentCompletion.complete("谢谢你的", listOf(c("夸", 6f), c("关", 4f)), nanoTime = { now }) { text, _ ->
                peeks++
                now += FragmentCompletion.BUDGET_NANOS + 1 // 第一次推理就用完预算
                next[text].orEmpty()
            }
        }
        // 只补了第一个碎片，第二个不再追加推理。
        assertEquals(1, peeks)
        assertEquals(listOf("夸奖", "关"), result.map { it.text })
    }

    @Test fun punctuationAndAbsentPeeksNeverExtend() {
        assertNull(FragmentCompletion.dominantNext(listOf(c("！", 9f), c("，", 1f))))
        assertNull(FragmentCompletion.dominantNext(emptyList()))
        assertEquals("助", FragmentCompletion.dominantNext(listOf(c("助", 9f))))
        assertFalse(FragmentCompletion.isHanFragment("ok"))
        assertFalse(FragmentCompletion.isHanFragment("帮助"))
        assertTrue(FragmentCompletion.isHanFragment("夸"))
    }
}
