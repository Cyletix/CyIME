package com.kingzcheung.xime.rime

import com.kingzcheung.xime.rime.CorrectionMark.Extra
import com.kingzcheung.xime.rime.CorrectionMark.Gap
import com.kingzcheung.xime.rime.CorrectionMark.Keep
import com.kingzcheung.xime.rime.CorrectionMark.Missing
import com.kingzcheung.xime.rime.CorrectionMark.Replace
import com.kingzcheung.xime.rime.CorrectionMark.Swap
import org.junit.Assert.assertEquals
import org.junit.Test

class CorrectionMarksTest {
    private fun keep(s: String) = s.map { Keep(it) }

    @Test
    fun `missing letter is added`() {
        // chengong -> 成功: "chen" stands for "cheng".
        assertEquals(keep("chen") + Missing('g') + Gap + keep("gong"), CorrectionMarks.parse("chen=cheng gong"))
    }

    @Test
    fun `extra letter is struck`() {
        assertEquals(keep("c") + Extra('h') + keep("heng"), CorrectionMarks.align("chheng", "cheng"))
        assertEquals(keep("ch") + Extra('x') + keep("eng"), CorrectionMarks.align("chxeng", "cheng"))
    }

    @Test
    fun `wrong letter is replaced`() {
        assertEquals(keep("zh") + Replace('i', 'o') + keep("ng"), CorrectionMarks.align("zhing", "zhong"))
    }

    @Test
    fun `swapped neighbours are one swap`() {
        assertEquals(keep("zh") + Swap("on") + keep("g"), CorrectionMarks.align("zhnog", "zhong"))
        assertEquals(keep("l") + Swap("un"), CorrectionMarks.align("lnu", "lun"))
    }

    @Test
    fun `uncorrected input has no marks`() {
        assertEquals(emptyList<CorrectionMark>(), CorrectionMarks.parse(""))
        assertEquals(keep("ni") + Gap + keep("hao"), CorrectionMarks.parse("ni hao"))
    }
}
