package com.kingzcheung.xime.settings

import org.junit.Assert.*
import org.junit.Test

class JapaneseSchemasTest {
    @Test fun upgradingOnlyAddsJapaneseWithoutRestoringOtherDisabledSchemas() {
        assertEquals(listOf("user_schema", "japanese", "japanese_kana"), JapaneseSchemas.addMissing(listOf("user_schema")))
    }

    @Test fun alreadyEnabledJapaneseKeepsOrderAndHasNoDuplicate() {
        assertEquals(listOf("japanese_kana", "user_schema", "japanese"),
            JapaneseSchemas.addMissing(listOf("japanese_kana", "user_schema")))
    }
}
