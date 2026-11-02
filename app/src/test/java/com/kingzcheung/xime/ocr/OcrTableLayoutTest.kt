package com.kingzcheung.xime.ocr

import com.kingzcheung.xime.ocr.OcrTableLayout.Box
import com.kingzcheung.xime.ocr.OcrTableLayout.Format
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OcrTableLayoutTest {
    private fun box(left: Int, top: Int, right: Int, text: String, height: Int = 36) =
        Box.rect(left.toDouble(), top.toDouble(), right.toDouble(), (top + height).toDouble(), text)

    private fun grid(boxes: List<Box>, rows: DoubleArray = doubleArrayOf(), columns: DoubleArray = doubleArrayOf()) =
        OcrTableLayout.grid(boxes, rows, columns)

    /** Left text, right-aligned numbers, a title above and an empty cell: no lines at all. */
    private val borderless = listOf(
        box(60, 10, 260, "十月采购清单"),
        box(70, 80, 130, "商品"), box(450, 80, 510, "数量"), box(640, 80, 700, "单价"),
        box(70, 150, 130, "苹果"), box(475, 150, 510, "12"), box(630, 150, 700, "3.50"),
        box(70, 220, 160, "矿泉水"), box(490, 220, 510, "6"), box(615, 220, 700, "12.50"),
        box(70, 290, 130, "合计"), box(615, 290, 700, "98.00"),
    )

    @Test fun borderlessColumnsComeFromGapsNoRowCrosses() {
        assertEquals(listOf(
            listOf("十月采购清单", "", ""),
            listOf("商品", "数量", "单价"),
            listOf("苹果", "12", "3.50"),
            listOf("矿泉水", "6", "12.50"),
            listOf("合计", "", "98.00"),
        ), grid(borderless))
    }

    @Test fun markdownKeepsTheTitleOutOfTheHeader() {
        assertEquals("""
            十月采购清单

            | 商品 | 数量 | 单价 |
            | --- | --- | --- |
            | 苹果 | 12 | 3.50 |
            | 矿泉水 | 6 | 12.50 |
            | 合计 |  | 98.00 |
        """.trimIndent(), OcrTableLayout.format(grid(borderless), Format.MARKDOWN))
    }

    @Test fun csvAndSpacesKeepEveryRow() {
        assertEquals("十月采购清单,,\n商品,数量,单价\n苹果,12,3.50\n矿泉水,6,12.50\n合计,,98.00",
            OcrTableLayout.format(grid(borderless), Format.CSV))
        assertEquals("十月采购清单\n商品 数量 单价\n苹果 12 3.50\n矿泉水 6 12.50\n合计 98.00",
            OcrTableLayout.format(grid(borderless), Format.SPACES))
    }

    @Test fun cellTextIsEscapedForEachFormat() {
        val cells = listOf(listOf("a|b", "x, y"), listOf("say \"hi\"", " lead"))
        assertEquals("| a\\|b | x, y |\n| --- | --- |\n| say \"hi\" |  lead |", OcrTableLayout.format(cells, Format.MARKDOWN))
        assertEquals("a|b,\"x, y\"\n\"say \"\"hi\"\"\",\" lead\"", OcrTableLayout.format(cells, Format.CSV))
    }

    @Test fun ruledGridKeepsAWrappedCellTogetherAndDropsFrameColumns() {
        // Outer frame at 50/750, a doubled line at 300/304, bands at 60/130/230/300.
        val boxes = listOf(
            box(70, 77, 130, "项目"), box(320, 77, 380, "说明"), box(560, 77, 620, "状态"),
            box(70, 162, 130, "登录"), box(320, 140, 520, "支持短信验证码"), box(320, 182, 460, "和密码登录"), box(560, 162, 620, "完成"),
            box(70, 247, 130, "导出"), box(320, 247, 430, "导出 CSV"), box(560, 247, 650, "进行中"),
        )
        val columns = doubleArrayOf(50.0, 300.0, 304.0, 540.0, 750.0)
        val rows = doubleArrayOf(60.0, 130.0, 230.0, 300.0)
        assertEquals(listOf(
            listOf("项目", "说明", "状态"),
            listOf("登录", "支持短信验证码和密码登录", "完成"),
            listOf("导出", "导出 CSV", "进行中"),
        ), grid(boxes, rows, columns))
    }

    @Test fun innerLinesWithoutAFrameStillBoundTheOuterColumns() {
        val boxes = listOf(box(70, 80, 130, "姓名"), box(330, 80, 390, "年龄"), box(520, 80, 580, "城市"),
            box(70, 150, 130, "张三"), box(330, 150, 370, "28"), box(520, 150, 580, "北京"))
        assertEquals(listOf(listOf("姓名", "年龄", "城市"), listOf("张三", "28", "北京")),
            grid(boxes, doubleArrayOf(130.0), doubleArrayOf(320.0, 500.0, 900.0)))
    }

    @Test fun horizontalRulesAloneDoNotMergeRows() {
        // A three-line table: top rule, header rule, bottom rule, no vertical lines.
        val boxes = listOf(box(70, 80, 160, "Model"), box(400, 80, 500, "Score"),
            box(70, 150, 150, "Alpha"), box(430, 150, 500, "71.4"),
            box(70, 220, 140, "Beta"), box(430, 220, 500, "74.9"))
        assertEquals(listOf(listOf("Model", "Score"), listOf("Alpha", "71.4"), listOf("Beta", "74.9")),
            grid(boxes, doubleArrayOf(60.0, 135.0, 270.0)))
    }

    @Test fun aDuplicateReadingOfOneGlyphIsDropped() {
        val boxes = listOf(box(70, 80, 130, "单价"), box(300, 80, 360, "备注"),
            box(60, 150, 140, "11.25"), box(125, 150, 137, "5"), box(300, 150, 360, "促销"),
            box(60, 220, 140, "12.50"))
        assertEquals(listOf(listOf("单价", "备注"), listOf("11.25", "促销"), listOf("12.50", "")), grid(boxes))
    }

    @Test fun skewIsMeasuredAndLevelingRestoresTheRows() {
        val angle = Math.toRadians(3.0)
        fun rotated(b: Box): Box {
            val c = cos(angle); val s = sin(angle)
            return b.transformed(doubleArrayOf(c, -s, 0.0, s, c, 0.0))
        }
        val tilted = borderless.map(::rotated)
        val skew = OcrTableLayout.skewDegrees(tilted)
        assertEquals(3.0, skew, .01)
        val level = OcrTableLayout.straighten(skew, 800.0, 400.0)
        assertEquals(grid(borderless), grid(tilted.map { it.transformed(level) }))
        assertEquals(0.0, OcrTableLayout.skewDegrees(borderless), 0.0)
    }

    @Test fun projectionLinesMergeThickAndDoubledLinesAndIgnoreWeakRuns() {
        val profile = IntArray(200)
        for (i in 20..22) profile[i] = 600 // a 3 px border
        for (i in listOf(100, 104)) profile[i] = 590 // a doubled line
        profile[150] = 120 // an underline under one word
        profile[180] = 610
        assertArrayEquals(doubleArrayOf(21.0, 102.0, 180.0), OcrTableLayout.lines(profile, 90, 10.0), 0.0)
        assertEquals(0, OcrTableLayout.lines(IntArray(50) { 30 }, 90, 10.0).size)
    }

    @Test fun blankRunsSkipMarginsAndNarrowSpaces() {
        val ink = intArrayOf(0, 0, 5, 6, 0, 7, 8, 0, 0, 0, 0, 0, 9, 9, 0, 0)
        val runs = OcrTableLayout.blankRuns(ink, 0, 4)
        assertEquals(1, runs.size)
        assertArrayEquals(intArrayOf(7, 12), runs[0])
    }

    @Test fun unknownFormatNamesMeanPlainText() {
        assertNull(OcrTableLayout.Format.parse(null))
        assertNull(OcrTableLayout.Format.parse("XLSX"))
        assertEquals(Format.CSV, OcrTableLayout.Format.parse("CSV"))
        assertEquals("", OcrTableLayout.format(emptyList(), Format.MARKDOWN))
    }
}
