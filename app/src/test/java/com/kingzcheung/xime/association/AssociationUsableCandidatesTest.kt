package com.kingzcheung.xime.association

import org.junit.Assert.assertEquals
import org.junit.Test

class AssociationUsableCandidatesTest {

    @Test
    fun `punctuation-only and single-letter predictions are dropped`() {
        val candidates = listOf("？\"", "。", "！！", "……", " ", "e", "呀", "好的。", "OK", "2", "😀")
            .map { AssociationCandidate(it, 1f) }
        assertEquals(listOf("呀", "好的。", "OK", "2", "😀"),
            AssociationManager.usableCandidates(candidates).map { it.text })
    }
}
