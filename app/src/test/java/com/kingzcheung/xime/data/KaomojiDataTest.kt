package com.kingzcheung.xime.data

import org.junit.Assert.*
import org.junit.Test

class KaomojiDataTest {
    @Test fun catalogHasDistinctCompleteUnicodeStringsAndIndependentHistory() {
        val faces = KaomojiData.categories.flatMap { it.faces }
        assertEquals(12, KaomojiData.categories.size)
        assertEquals(144, faces.size)
        assertEquals(faces.size, faces.distinct().size)
        assertTrue(faces.all { it.isNotBlank() && '\n' !in it && '\uFFFD' !in it })
        assertNotEquals(RecentUsageStore.KEY_RECENT_EMOJIS, RecentUsageStore.KEY_RECENT_KAOMOJI)
        val face = "(づ｡◕‿‿◕｡)づ"
        assertEquals(listOf(face, "(T_T)"), RecentUsageStore.record(listOf("(T_T)", face), face))
    }
}
