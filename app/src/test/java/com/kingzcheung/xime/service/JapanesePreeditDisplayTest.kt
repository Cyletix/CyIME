package com.kingzcheung.xime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日语罗马音显示的快路径判定：只有末尾还没拼完的输入才需要问引擎。
 * 完整音节由元音（a/i/u/e/o）或长音符收尾，其余（声母、n、x…）交给引擎判定。
 */
class JapanesePreeditDisplayTest {
    @Test fun vowelOrLongMarkEndingNeedsNoProbe() {
        listOf("ka", "kana", "KATAKANA", "ka-", "-").forEach {
            assertTrue("$it 已是完整音节，不该再探测引擎", japaneseSyllableClosedByVowel(it))
        }
    }

    @Test fun consonantEndingNeedsProbe() {
        listOf("k", "n", "nn", "kk", "kak", "kas", "xt", "K", "NN").forEach {
            assertFalse("$it 末尾是声母，需要引擎判定是否拼完", japaneseSyllableClosedByVowel(it))
        }
    }

    @Test fun emptyInputCountsAsClosed() {
        assertTrue(japaneseSyllableClosedByVowel(""))
    }
}
