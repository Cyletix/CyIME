package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.Cyletix10Layout
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.LetterLayouts
import com.kingzcheung.xime.settings.TypewiseLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Typewise's honeycomb: seven columns, the 6-key rows offset half a key, Shift/delete in the third row's margins. */
class HoneycombGeometryTest {
    @After fun clear() = LetterLayouts.setForTest(InputLanguage.ENGLISH, null)

    @Test fun honeycombUsesSevenColumnsAndHalfKeyMargins() {
        val rows = TypewiseLayout.preset.typingRows()
        assertTrue(isHoneycomb(rows))
        assertFalse(isHoneycomb(Cyletix10Layout.preset.typingRows()))
        assertEquals(7f, letterKeyboardColumns(rows, false))
        assertEquals(QwertyBottomRowWeights.Standard, letterKeyboardBottomRow(rows, false))
        val geometry = standardLetterRowGeometry(rows, 50f, true, false)!!
        assertEquals(0f, geometry.middleRowInsetDp)
        assertEquals(7f, geometry.outerKeyWeight + rows[2].size + geometry.deleteKeyWeight)
        assertEquals(geometry.outerKeyWeight, geometry.deleteKeyWeight)
        assertNull(standardLetterRowGeometry(rows, 50f, true, isSplit = true))
    }

    @Test fun honeycombIsNeverSplit() {
        assertTrue(supportsSplitKeyboard("rime_ice", true))
        LetterLayouts.setForTest(InputLanguage.ENGLISH, TypewiseLayout.ID)
        assertFalse(supportsSplitKeyboard("rime_ice", true))
        assertFalse(supportsSplitKeyboard(TypewiseLayout.ID, false))
    }
}
