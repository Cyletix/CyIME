package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 「删字后给同音字」（用户反馈 10-31，默认关）：删掉的字排第一，后面同音字；不挡接着删。 */
class DeletionRecoveryTest {
    private val index by lazy {
        File("src/main/assets/handwriting/hanzi_pinyin.tsv").bufferedReader(Charsets.UTF_8).useLines { pinyin ->
            File("src/main/assets/handwriting/hanzi_frequency.tsv").bufferedReader(Charsets.UTF_8).useLines { frequency ->
                HomophoneIndex.parse(pinyin, frequency)
            }
        }
    }

    @Test fun homophonesOfACommonTypoComeFirst() {
        val zai = index.homophones("在")
        assertTrue(zai.toString(), "再" in zai.take(3))
        assertFalse("在" in zai)
        assertTrue(zai.size <= DeletionRecovery.HOMOPHONE_LIMIT)
        // 的/地/得：同音不同调也给，同调的排前面
        val de = index.homophones("的")
        assertTrue(de.toString(), "地" in de && "得" in de)
    }

    @Test fun sameToneBeforeOtherTones() {
        val words = index.homophones("再") // zài
        val zai4 = words.indexOf("在")
        val zai1 = words.indexOf("灾") // zāi
        assertTrue(words.toString(), zai4 >= 0 && zai1 >= 0 && zai4 < zai1)
    }

    @Test fun umlautIsNotMixedWithU() {
        assertEquals("lü", HomophoneIndex.toneless("lǜ"))
        assertEquals("lu", HomophoneIndex.toneless("lù"))
        assertFalse(index.homophones("绿").contains("路"))
    }

    @Test fun nonHanOrUnknownHasNoHomophones() {
        assertTrue(index.homophones("a").isEmpty())
        assertTrue(index.homophones("😀").isEmpty())
        assertTrue(index.homophones("你好").isEmpty())
    }

    @Test fun consecutiveDeletesBuildTheRunFromTheFront() {
        val first = DeletionRecovery.nextRun(null, "吗")
        assertEquals("吗", first)
        assertEquals("好吗", DeletionRecovery.nextRun(first, "好"))
        assertNull(DeletionRecovery.nextRun("好吗", " "))
        assertNull(DeletionRecovery.nextRun(null, "\n"))
        assertNull(DeletionRecovery.nextRun("字".repeat(DeletionRecovery.MAX_RUN), "多"))
        assertEquals("𠀀字", DeletionRecovery.nextRun("字", "𠀀"))
    }

    @Test fun restoredTextLeadsTheList() {
        assertEquals(listOf("在", "再", "载"), DeletionRecovery.words("在", listOf("再", "载")))
        assertEquals(listOf("好吗", "号", "好"), DeletionRecovery.words("好吗", listOf("号", "好")))
    }
}
