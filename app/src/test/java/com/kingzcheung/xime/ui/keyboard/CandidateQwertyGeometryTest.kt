package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class CandidateQwertyGeometryTest {
    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").map { it.map(Char::toString) }

    @Test fun standardRailsUseTheModeDeleteAndEnterWeightsOfTheTypingKeyboard() {
        val geometry = candidateQwertyGeometry(rows, false, false, false)
        assertEquals(10f, geometry.columns, 0f)
        for (width in listOf(224f, 344f, 640f, 1024f)) {
            assertEquals(width * 1.2f / 10f, width * geometry.modeFraction, .001f)
            assertEquals(width * 1.5f / 10f, geometry.deleteWidth(width), .001f)
            assertEquals(width * 1.7f / 10f, width * geometry.enterFraction, .001f)
        }
    }

    @Test fun mergedAndCustomRowsKeepTheirRealBottomAndSideWeights() {
        val merged = candidateQwertyGeometry(listOf(listOf("qw", "er", "ty", "ui", "op")), true, false, false)
        assertEquals(10f, merged.columns, 0f)
        assertEquals(QwertyBottomRowWeights.Standard.mode / QwertyBottomRowWeights.Standard.total, merged.modeFraction, 0f)
        assertEquals(1.4f / 9.8f, merged.deleteFraction, .0001f)
        val custom = candidateQwertyGeometry(listOf(List(12) { "a" }), false, true, false)
        assertEquals(12f, custom.columns, 0f)
        assertEquals(QwertyBottomRowWeights.Legacy.mode / QwertyBottomRowWeights.Legacy.total, custom.modeFraction, 0f)
    }

    @Test fun splitRowsIncludePanelFractionAndDeleteStaggerWithoutUsingScreenWidth() {
        val split = candidateQwertyGeometry(rows, false, false, true)
        assertEquals(.9f, split.widthFraction, 0f)
        assertEquals(4f, split.innerInsetDp, 0f)
        for (width in listOf(224f, 344f, 800f)) {
            assertEquals(width * .45f * 16f / 45f, width * split.modeFraction, .001f)
            assertEquals((width * .45f - 12f) / 5f, split.deleteWidth(width), .001f)
            assertEquals(width * .45f * 1.2f / (2f + 1.2f + .8f + 1.2f), width * split.enterFraction, .001f)
            assertTrue(split.deleteWidth(width) + split.deleteEndInsetDp < width / 2f)
        }
    }
}
