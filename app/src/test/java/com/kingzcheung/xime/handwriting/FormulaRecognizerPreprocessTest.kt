package com.kingzcheung.xime.handwriting

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * 手机端公式预处理必须和训练脚本一致。期望值由 tools/handwriting_training 的
 * mathwriting_baseline.featurize + formula_arch_ablation.collate_segments 对同一组合成笔画算出
 * （训练数据存成 float16，所以容差取 2e-3）。
 */
class FormulaRecognizerPreprocessTest {

    private val meta = FormulaRecognizer.Meta(segLen = 16, segFeat = 8, maxPoints = 1024, maxTokens = 200,
        minStep = 0.015f, wideRatio = 8f, bos = 1, eos = 2)

    private fun strokes(): List<List<Pair<Float, Float>>> {
        val circle = (0..10).map { t ->
            val a = t / 10.0 * 6.283
            (100 + 30 * cos(a)).toFloat() to (50 + 30 * sin(a)).toFloat()
        }
        val line = (0..40).map { i -> (60 + i * 7.5f) to 120f }
        val dot = listOf(250f to 40f, 251f to 41.5f)
        return listOf(circle, line, dot)
    }

    @Test fun segmentsMatchTrainingScript() {
        val feats = FormulaRecognizer.featurize(strokes(), meta)!!
        val segs = FormulaRecognizer.segments(feats, meta)
        assertEquals(listOf(11, 14, 13, 14, 2), segs.map { it.points.size })
        val expectedC = listOf(
            floatArrayOf(0.406f, 0.2896f, 0.6089f, 0.5791f, 1f, 1f, 0f, 1f),
            floatArrayOf(0.4949f, 1f, 0.9897f, 0f, 1f, 0f, 0.5f, 1f),
            floatArrayOf(1.5225f, 1f, 0.9141f, 0f, 0f, 0f, 0.5f, 1f),
            floatArrayOf(2.5498f, 1f, 0.9902f, 0f, 0f, 1f, 0.5f, 1f),
            floatArrayOf(1.9336f, 0.1957f, 0.0098f, 0.0153f, 1f, 1f, 1f, 1f),
        )
        expectedC.forEachIndexed { i, row -> assertArrayEquals("段 $i", row, segs[i].feat, 2e-3f) }
        val expectedP0 = listOf(
            floatArrayOf(0.3045f, 0f, 0f, 0f, 0f, 1f),
            floatArrayOf(0.2464f, 0.179f, -0.0581f, 0.179f, 0f, 0f),
            floatArrayOf(0.094f, 0.2896f, -0.1522f, 0.1106f, 0f, 0f),
            floatArrayOf(-0.0942f, 0.2896f, -0.1881f, 0f, 0f, 0f),
            floatArrayOf(-0.2463f, 0.179f, -0.1522f, -0.1106f, 0f, 0f),
            floatArrayOf(-0.3045f, 0f, -0.0582f, -0.179f, 0f, 0f),
            floatArrayOf(-0.2463f, -0.1789f, 0.0581f, -0.179f, 0f, 0f),
            floatArrayOf(-0.0942f, -0.2895f, 0.1522f, -0.1106f, 0f, 0f),
            floatArrayOf(0.094f, -0.2896f, 0.1881f, 0f, 0f, 0f),
            floatArrayOf(0.2464f, -0.179f, 0.1522f, 0.1106f, 0f, 0f),
            floatArrayOf(0.3045f, 0f, 0.0582f, 0.179f, 1f, 0f),
        )
        expectedP0.forEachIndexed { j, row -> assertArrayEquals("第 0 段第 $j 点", row, segs[0].points[j], 2e-3f) }
    }

    @Test fun detokenizeSpacesOnlyBetweenCommandAndLetter() {
        val table = listOf("<pad>", "<bos>", "<eos>", "<unk>", "\\alpha", "x", "^", "{", "2", "}", "\\frac", "\\cdot")
        // \alpha x^{2}，\frac{x}，\cdot 2
        assertEquals("\\alpha x^{2}", FormulaRecognizer.detokenize(listOf(4, 5, 6, 7, 8, 9), table))
        assertEquals("\\frac{x}", FormulaRecognizer.detokenize(listOf(10, 7, 5, 9), table))
        assertEquals("\\cdot2", FormulaRecognizer.detokenize(listOf(11, 8), table))
    }

    @Test fun sparseStrokesAreFilledInButDenseWritingIsLeftAlone() {
        // 只有 3 个采样点的“L”：补到与正常书写相近的密度
        val sparse = listOf(listOf(650f to 120f, 650f to 450f, 850f to 450f))
        val filled = FormulaRecognizer.densify(sparse, meta).single()
        assertEquals(650f to 120f, filled.first())
        assertEquals(850f to 450f, filled.last())
        assertEquals(true, filled.size > 50)
        // 正常书写的点本来就密（这里每 2 像素一个点）：一点不变
        val written = listOf((0..165).map { 650f to 120f + it * 2 } + (1..100).map { 650f + it * 2 to 450f })
        assertEquals(written, FormulaRecognizer.densify(written, meta))
    }
    @Test fun longFormulaOverThePointLimitIsSampledMoreCoarselyInsteadOfDropped() {
        // 一行很长的公式：按训练时的点距约 1800 个点，超过 1024；放宽点距后仍能送进模型，左右顺序不变
        val strokes = (0 until 60).map { c ->
            (0..100).map { k -> c * 30f to k.toFloat() }
        }
        val rows = FormulaRecognizer.featurize(strokes, meta)
        assertTrue(rows != null && rows.sumOf { it.size } <= meta.maxPoints)
        assertEquals(60, rows!!.size)
        assertTrue(rows.first().first()[0] < rows.last().first()[0])
    }
}
