package com.kingzcheung.xime.handwriting.training

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingTrainingTest {

    @Test fun csvParsesQuotedFieldsStripsDelimitersAndRepairsBadBytes() {
        val csv = "file_name,LaTeX\r\n" +
            "1.jpg,\$\$a^3+b^3\$\$\r\n" +
            "2.jpg,\"\$\$M_0(x_0,y_0)\$\$\"\r\n" +
            "3.jpg,\"\$\$\\text{\"\"q\"\"}\$\$\"\r\n"
        val bytes = csv.toByteArray(Charsets.UTF_8) +
            "4.jpg,\$\$\\text{n".toByteArray() + byteArrayOf(0x82.toByte(), 0x80.toByte()) + "}=1\$\$\r\n".toByteArray()
        val result = FormulaCsvParser.parse(bytes, "annotations.csv")
        assertEquals(listOf("a^3+b^3", "M_0(x_0,y_0)", "\\text{\"q\"}", "\\text{n}=1"), result.prompts.map { it.label })
        assertEquals(1, result.repairedRows)
        assertTrue(result.prompts.last().repaired)
        assertFalse(result.prompts.first().repaired)
        assertEquals("annotations.csv:2.jpg", result.prompts[1].source)
        assertEquals(result.prompts.size, result.prompts.map { it.id }.toSet().size)
    }

    @Test fun duplicateFileNamesGetDistinctIds() {
        val result = FormulaCsvParser.parse("file_name,LaTeX\nx.jpg,a\nx.jpg,b\n".toByteArray(), "c.csv")
        assertEquals(2, result.prompts.map { it.id }.toSet().size)
    }

    @Test fun builtinBanksAreNonEmptyAndUnique() {
        for (type in listOf(TrainingPromptType.Kana, TrainingPromptType.Latin, TrainingPromptType.Symbol)) {
            val prompts = BuiltinTrainingPrompts.prompts(type)
            assertTrue(type.key, prompts.isNotEmpty())
            assertEquals(type.key, prompts.size, prompts.map { it.id }.toSet().size)
        }
    }

    @Test fun sampleEncodesAsSingleJsonLineWithRawPoints() {
        val prompt = TrainingPrompt("symbol:α", TrainingPromptType.Symbol, "α", "\\alpha")
        val sample = TrainingSample(
            prompt = prompt,
            canvasWidthPx = 1000f,
            canvasHeightPx = 800f,
            density = 2.5f,
            strokes = listOf(
                TrainingStroke(listOf(TrainingPoint(1.234f, 2f, 0), TrainingPoint(3f, 4f, 16)), "stylus"),
                TrainingStroke(listOf(TrainingPoint(5f, 6f, 400)), "touch"),
            ),
            createdAtMs = 1_000L,
            id = "fixed",
        )
        val line = TrainingSampleCodec.encode(sample, "1.0", "dev")
        assertFalse(line.contains('\n'))
        val o = JSONObject(line)
        assertEquals("cyime-handwriting-training", o.getString("format"))
        assertEquals("symbol", o.getString("type"))
        assertEquals("\\alpha", o.getString("latex"))
        val strokes = o.getJSONArray("strokes")
        assertEquals(2, strokes.length())
        assertEquals("stylus", strokes.getJSONObject(0).getString("tool"))
        val p = strokes.getJSONObject(0).getJSONArray("points").getJSONArray(0)
        assertEquals(1.23, p.getDouble(0), 1e-9)
        assertEquals(400L, strokes.getJSONObject(1).getJSONArray("points").getJSONArray(0).getLong(2))
        assertEquals(2.5, o.getJSONObject("canvas").getDouble("density"), 1e-9)
        assertFalse(o.has("recognized"))
        assertFalse(o.has("matched"))
    }

    @Test fun checkedSampleRecordsRecognitionAndWhetherItMatched() {
        val prompt = TrainingPrompt("hanzi:还", TrainingPromptType.Chinese, "还")
        val stroke = listOf(TrainingStroke(listOf(TrainingPoint(0f, 0f, 0))))
        fun encode(top: List<String>) = JSONObject(TrainingSampleCodec.encode(
            TrainingSample(prompt, 10f, 10f, 1f, stroke, 0L, recognized = top), "1.0", "dev"))
        val wrong = encode(listOf("迅", "迩", "还"))
        assertEquals("迅", wrong.getJSONArray("recognized").getString(0))
        assertFalse(wrong.getBoolean("matched"))
        assertTrue(encode(listOf("还", "迅")).getBoolean("matched"))
    }

    @Test fun eraserRemovesWholeTouchedStrokesOnly() {
        val horizontal = TrainingStroke(listOf(TrainingPoint(0f, 0f, 0), TrainingPoint(100f, 0f, 50)))
        val far = TrainingStroke(listOf(TrainingPoint(0f, 200f, 300), TrainingPoint(100f, 200f, 350)))
        val dot = TrainingStroke(listOf(TrainingPoint(50f, 100f, 500)))
        val strokes = listOf(horizontal, far, dot)
        assertEquals(listOf(far, dot), TrainingInk.erase(strokes, listOf(50f to 10f), 12f))
        assertEquals(listOf(horizontal, far), TrainingInk.erase(strokes, listOf(55f to 105f), 12f))
        assertEquals(strokes, TrainingInk.erase(strokes, listOf(50f to 50f), 12f))
    }

    @Test fun fastEraserPathErasesStrokesItCrossesBetweenSamples() {
        val vertical = TrainingStroke(listOf(TrainingPoint(100f, 0f, 0), TrainingPoint(100f, 200f, 50)))
        val far = TrainingStroke(listOf(TrainingPoint(500f, 0f, 0), TrainingPoint(500f, 200f, 50)))
        // 橡皮只有两个采样点，都离竖线很远，但两点连线穿过竖线
        assertEquals(listOf(far), TrainingInk.erase(listOf(vertical, far), listOf(0f to 100f, 300f to 100f), 10f))
        // 只看采样点时（单点）不会擦到
        assertEquals(2, TrainingInk.erase(listOf(vertical, far), listOf(0f to 100f), 10f).size)
    }

    @Test fun rebaseStartsSavedSampleAtZeroAfterEarlierStrokesWereErased() {
        val rebased = TrainingInk.rebase(listOf(
            TrainingStroke(listOf(TrainingPoint(0f, 0f, 800), TrainingPoint(1f, 1f, 816))),
            TrainingStroke(listOf(TrainingPoint(2f, 2f, 1200))),
        ))
        assertEquals(listOf(0L, 16L, 400L), rebased.flatMap { s -> s.points.map { it.t } })
    }

    @Test fun generatedFormulasAreDistinctBalancedAndDeterministic() {
        val prompts = FormulaGenerator.prompts()
        assertEquals(3 * FormulaGenerator.PER_LEVEL, prompts.size)
        assertEquals(prompts.size, prompts.map { it.id }.toSet().size)
        assertEquals(prompts.map { it.label }, FormulaGenerator.prompts().map { it.label })
        for (p in prompts) {
            assertEquals(p.label, p.label.count { it == '{' }, p.label.count { it == '}' })
            assertTrue(p.source.startsWith("generated:L"))
            assertEquals(TrainingPromptType.Formula, p.type)
        }
        // 三档交错：前 3 题分别来自 L1、L2、L3
        assertEquals(listOf("L1", "L2", "L3"), prompts.take(3).map { it.source.split(':')[1] })
        // 导出给电脑上的 KaTeX 校验（tools/handwriting_training 里用 node 检查是否能渲染）
        java.io.File("build/handwriting-training").apply { mkdirs() }
            .resolve("generated_formulas.json")
            .writeText(org.json.JSONArray(prompts.map { it.label }).toString(), Charsets.UTF_8)
    }

    @Test fun bankFilesParseHintsLongFlagsAndStableIds() {
        val lim = "\\lim_{x \\to 0} \\frac{\\sin x}{x}=1"
        val lines = sequenceOf(
            "formula\t0\t$lim\t极限 › 重要极限 · 第一个重要极限\t微积分/极限.md#重要极限",
            "bad line",
            "formula\t0\t\t\ta.md",
            "derivation\t1\t\\begin{gathered} a=b \\\\ b=c \\end{gathered}\t\tb.md",
        )
        val prompts = PromptBankFile.Math.parse(lines)
        assertEquals(2, prompts.size)
        assertEquals(lim, prompts[0].label)
        assertEquals(lim, prompts[0].latex)
        assertEquals("math:微积分/极限.md#重要极限", prompts[0].source)
        assertEquals("极限 › 重要极限 · 第一个重要极限", prompts[0].hint)
        assertFalse(prompts[0].long)
        assertTrue(prompts[1].long)
        assertEquals("derivation", prompts[1].kind)
        assertEquals(null, prompts[1].hint)
        val again = PromptBankFile.Math.parse(sequenceOf("formula\t0\t$lim\t\t其它.md")).single()
        assertEquals(prompts[0].id, again.id)
        assertEquals(null, PromptBankFile.Japanese.parse(sequenceOf("sentence\t0\t私は学生です。\tN5\t基本文")).single().latex)
    }

    @Test fun generatedBankFilesAreLargeEnoughAndWellFormed() {
        // 外置题库在本机 D:/CyIME-Data 下生成，不进仓库；其它电脑上没有就跳过
        val dir = java.io.File("D:/CyIME-Data/handwriting_training/banks")
        assertEquals(PromptBankFile.Math, PromptBankFile.byFileName("MATH_FORMULAS.tsv"))
        assertEquals(null, PromptBankFile.byFileName("annotations.csv"))
        for (bank in PromptBankFile.entries) {
            val file = dir.resolve(bank.fileName)
            if (!file.exists()) continue
            val prompts = file.bufferedReader(Charsets.UTF_8).useLines { bank.parse(it) }
            assertTrue("${bank.fileName} 只有 ${prompts.size} 题", prompts.size > 1000)
            assertTrue(prompts.all { it.type == bank.type && it.source.startsWith(bank.prefix + ":") })
            // 前 8 题里至少 6 道短题：默认先出短题，长题偶尔穿插
            assertTrue(bank.fileName, prompts.take(8).count { !it.long } >= 6)
        }
    }

    @Test fun pastedTextBecomesCustomPromptAndLatexIsDetected() {
        val plain = BuiltinTrainingPrompts.custom("  手写 ")
        assertEquals("手写", plain.label)
        assertEquals(TrainingPromptType.Custom, plain.type)
        assertEquals(null, plain.latex)
        assertEquals("clipboard", plain.source)
        assertEquals("\\frac{a}{b}", BuiltinTrainingPrompts.custom("\$\\frac{a}{b}\$").latex)
        assertEquals("x^2+1", BuiltinTrainingPrompts.custom("x^2+1").latex)
        assertEquals(null, BuiltinTrainingPrompts.custom("Ω").latex)
    }
}
