package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class LetterKeyboardFrameTest {
    private val standard = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").map { it.map(Char::toString) }
    private val fourteen = listOf(listOf("qw", "er", "ty", "ui", "op"),
        listOf("as", "df", "gh", "jk", "l"), listOf("zx", "cv", "bn", "m"))
    private val seventeen = listOf(listOf("q", "we", "rt", "yu", "i", "op"),
        listOf("a", "sd", "fg", "h", "jk", "l"), listOf("z", "xc", "v", "bn", "m"))
    private val eighteen = listOf(listOf("q", "we", "rt", "y", "u", "io", "p"), seventeen[1], seventeen[2])
    private val custom = listOf(listOf("qw", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "df", "g", "h", "j", "k", "l"), listOf("z", "x", "cv", "b", "n", "m"))
    private val cyletix = listOf("qwdrf;jkyp", "asetghliou", "zxcvbnm").map { it.map(Char::toString) }
    private val spareAtTop = listOf(standard[0].filterNot { it == "e" }, standard[1] + "e", standard[2])
    private val spareAtBottom = listOf(standard[0], standard[1] + "z", standard[2].filterNot { it == "z" })
    private val families = listOf(standard, fourteen, seventeen, eighteen, custom, standard.map { it.reversed() },
        cyletix, spareAtTop, spareAtBottom)

    @Test fun familyComesFromCompleteLettersAndRowSpansNotNumberOfButtonsOrSchemaName() {
        families.forEach { rows ->
            val before = rows.map { it.toList() }
            val frame = requireNotNull(letterKeyboardFrame(rows))
            assertEquals(10f, frame.columns, 0f)
            assertEquals(rows.map { row -> row.sumOf(String::length) }, frame.rowUnits)
            assertEquals(before, rows) // inspection cannot reorder user letters or rewrite groups
            assertEquals(10f, letterKeyboardColumns(rows, false), 0f)
            assertEquals(QwertyBottomRowWeights.Standard, letterKeyboardBottomRow(rows, false))
        }
        for (other in listOf(List(3) { List(3) { "abc" } }, listOf(listOf("あ", "か", "さ")),
            listOf(listOf("1", "2", "3")), standard.dropLast(1), standard.map { row -> row.map { "a" } },
            listOf(standard[0].drop(2), standard[1] + standard[0].take(2), standard[2]),
            listOf(standard[0] + "a", standard[1].drop(1), standard[2]),
            listOf(standard[0], standard[1] + "_", standard[2]))) {
            assertNull(letterKeyboardFrame(other))
            assertNull(standardLetterRowGeometry(other, 98.4f, true, false, 98.4f))
        }
    }

    @Test fun narrowTransitionAndWideFramesHaveContinuousOffsetsAndBoundedRows() {
        for (rows in families) {
            var previousStart = Float.POSITIVE_INFINITY
            var previousShift = Float.POSITIVE_INFINITY
            for (step in 200..1600) {
                val cell = step / 10f
                val frame = letterKeyboardFrame(rows)!!
                val g = standardLetterRowGeometry(rows, cell, true, false, 98.4f)!!
                val start = g.middleRowInsetDp / cell
                assertTrue(start <= previousStart + .00001f)
                assertTrue(g.outerKeyWeight <= previousShift + .00001f)
                if (previousShift.isFinite()) assertTrue(previousShift - g.outerKeyWeight < .01f)
                assertEquals((10 - frame.rowUnits[1]).toFloat(),
                    (g.middleRowInsetDp + g.middleRowEndInsetDp) / cell, .0001f)
                assertEquals(frame.rowUnits[2].toFloat(), g.bottomLetterWeight, 0f)
                assertEquals(10f, g.outerKeyWeight + g.bottomLetterWeight + g.deleteKeyWeight, .0001f)
                assertTrue(g.outerKeyWeight > 0f && g.deleteKeyWeight > 0f && g.bottomLetterWeight > 0f)
                assertTrue(g.middleRowInsetDp >= 0f && g.middleRowEndInsetDp >= 0f)
                if (cell <= 68.8f) {
                    assertEquals((10 - frame.rowUnits[2]) * .5f, g.outerKeyWeight, 0f)
                    assertEquals((10 - frame.rowUnits[1]) * .5f, start, .0001f)
                } else if (cell >= 98.4f) {
                    assertEquals((10 - frame.rowUnits[2]) * .25f, g.outerKeyWeight, 0f)
                    assertEquals((10 - frame.rowUnits[2]) * .75f, g.deleteKeyWeight, 0f)
                    assertEquals((10 - frame.rowUnits[1]) * .25f, start, .0001f)
                }
                previousStart = start; previousShift = g.outerKeyWeight
            }
            assertNull(standardLetterRowGeometry(rows, 100f, true, true, 98.4f))
        }
    }

    @Test fun fourteenMMatchesQwertyOnPhonesAndWidensOnlyFromStaggerSurplus() {
        val phone = standardLetterRowGeometry(fourteen, 40f, false, false, 98.4f)!!
        assertEquals(0f, fourteenMBorrowedWeight(phone.outerKeyWeight, phone.deleteKeyWeight), 0f)
        val tablet = standardLetterRowGeometry(fourteen, 98.4f, false, false, 98.4f)!!
        val borrowed = fourteenMBorrowedWeight(tablet.outerKeyWeight, tablet.deleteKeyWeight)
        assertEquals(.75f, borrowed, .0001f)
        // 删除键不窄于手机上的宽度。
        assertEquals(phone.deleteKeyWeight, tablet.deleteKeyWeight - borrowed, .0001f)
    }

    @Test fun defaultAndTallResizedFramesShareAllGapsRowsAndBottomBoundaries() {
        for (width in listOf(220f, 360f, 800f, 1000f, 1600f))
            for (height in listOf(180f, 260f, 500f, 900f)) for (floating in listOf(false, true)) {
                val policy = KeyVisualPolicy.Qwerty.copy(maxKeyWidth = Float.MAX_VALUE)
                val expected = keyboardGridMetrics(policy, width, height, 10f,
                    allowShrink = floating, growthSpacing = null to null)
                val referenceBottom = candidateQwertyGeometry(standard, false, false, false)
                for (rows in families) {
                    val actual = keyboardGridMetrics(policy, width, height, letterKeyboardColumns(rows, false),
                        allowShrink = floating, growthSpacing = null to null)
                    assertEquals(expected, actual)
                    val bottom = candidateQwertyGeometry(rows, false, true, false)
                    assertEquals(referenceBottom, bottom)
                    assertEquals(.17f * (width - actual.gutterX * 2),
                        bottom.deleteWidth(width - actual.gutterX * 2), .001f)
                }
            }
    }
}
