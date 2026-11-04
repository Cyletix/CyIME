package com.kingzcheung.xime.ui.settings

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** 配置高低刻度下的低配、中配、高配文字：挨着的刻度错开两行，各自仍对准自己的刻度。 */
class StaggerLabelsTest {
    @Test fun `labels that fit stay on one row where their ticks are`() {
        val lefts = intArrayOf(0, 300, 600)
        assertArrayEquals(intArrayOf(0, 0, 0), staggerLabels(lefts, intArrayOf(60, 60, 60), 800, 16))
        assertArrayEquals(intArrayOf(0, 300, 600), lefts)
    }

    @Test fun `close ticks put the second label on a second row without moving it`() {
        // 11-04 渲染：低配、中配的刻度几乎在同一处，高配在右边。
        val lefts = intArrayOf(0, 20, 600)
        assertArrayEquals(intArrayOf(0, 1, 0), staggerLabels(lefts, intArrayOf(60, 60, 60), 800, 16))
        assertArrayEquals(intArrayOf(0, 20, 600), lefts)
    }

    @Test fun `three labels on one tick use both rows and push only the third`() {
        val lefts = intArrayOf(100, 100, 100)
        assertArrayEquals(intArrayOf(0, 1, 0), staggerLabels(lefts, intArrayOf(60, 60, 60), 800, 16))
        assertArrayEquals(intArrayOf(100, 100, 176), lefts)
    }

    @Test fun `a pushed label stays inside the row`() {
        val lefts = intArrayOf(740, 740, 740)
        staggerLabels(lefts, intArrayOf(60, 60, 60), 800, 16)
        assertArrayEquals(intArrayOf(740, 740, 740), lefts)
    }
}
