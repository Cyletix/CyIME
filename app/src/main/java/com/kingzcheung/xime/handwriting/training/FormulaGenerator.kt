package com.kingzcheung.xime.handwriting.training

import kotlin.random.Random

/**
 * 内置随机公式题：分式、根式、上下标、求和、积分、极限、导数、矩阵、分段函数随机组合。
 * 固定种子，同一版本每次生成同样的题目顺序，进度位置因此稳定。
 * 难度：1 单一结构；2 两三层嵌套与常见算子；3 矩阵、分段、多重积分、偏导等。
 */
object FormulaGenerator {

    const val SEED = 20261012
    const val PER_LEVEL = 200

    fun prompts(perLevel: Int = PER_LEVEL, seed: Int = SEED): List<TrainingPrompt> {
        val out = mutableListOf<TrainingPrompt>()
        for (level in 1..3) {
            val rnd = Random(seed * 31 + level)
            val seen = HashSet<String>()
            var i = 0
            var attempts = 0
            while (i < perLevel && attempts < perLevel * 20) {
                attempts++
                val tex = Gen(rnd).formula(level)
                // 太长的一屏写不下，丢掉重抽
                if (tex.length < 4 || tex.length > 100 || !seen.add(tex)) continue
                out += TrainingPrompt(
                    id = "formula:generated:L$level:$i",
                    type = TrainingPromptType.Formula,
                    label = tex,
                    latex = tex,
                    source = "generated:L$level:seed$seed",
                )
                i++
            }
        }
        // 三档交错出题，避免一上来连写 200 道最简单的
        return out.withIndex().sortedBy { (idx, _) -> (idx % perLevel) * 3 + idx / perLevel }.map { it.value }
    }

    private class Gen(val r: Random) {
        val vars = listOf("x", "y", "z", "a", "b", "c", "n", "k", "t", "m", "p", "q", "u", "v")
        val greek = listOf("\\alpha", "\\beta", "\\gamma", "\\theta", "\\lambda", "\\mu", "\\pi", "\\sigma", "\\omega", "\\varphi")
        val funcs = listOf("\\sin", "\\cos", "\\tan", "\\ln", "\\log", "\\arctan", "\\exp")
        val rel = listOf("=", "=", "=", "<", ">", "\\leq", "\\geq", "\\neq", "\\approx")

        fun <T> pick(list: List<T>): T = list[r.nextInt(list.size)]
        fun chance(p: Double) = r.nextDouble() < p
        fun num(max: Int = 9) = (1 + r.nextInt(max)).toString()
        fun v() = if (chance(0.15)) pick(greek) else pick(vars)

        fun atom(): String = when (r.nextInt(6)) {
            0 -> num()
            1 -> if (chance(0.3)) "${num()}${v()}" else v()
            2 -> "${v()}^{${pick(listOf("2", "3", "n", "k", "-1", "\\frac{1}{2}"))}}"
            3 -> "${v()}_{${pick(listOf("0", "1", "2", "n", "i", "k", "n+1"))}}"
            4 -> "${pick(funcs)} ${v()}"
            else -> v()
        }

        fun sum(depth: Int): String {
            val n = 1 + r.nextInt(if (depth > 0) 3 else 2)
            return (0 until n).joinToString("") { i ->
                val t = term(depth)
                if (i == 0) t else pick(listOf(" + ", " - ", " ")) .let { op -> if (op == " ") " \\cdot $t" else "$op$t" }
            }
        }

        fun term(depth: Int): String {
            if (depth <= 0) return atom()
            return when (r.nextInt(9)) {
                0 -> "\\frac{${sum(depth - 1)}}{${sum(depth - 1)}}"
                1 -> "\\sqrt{${sum(depth - 1)}}"
                2 -> "\\sqrt[${pick(listOf("3", "n"))}]{${sum(depth - 1)}}"
                3 -> "\\left(${sum(depth - 1)}\\right)^{${pick(listOf("2", "3", "n", "-1"))}}"
                4 -> "e^{${sum(depth - 1)}}"
                5 -> "${pick(funcs)}\\left(${sum(depth - 1)}\\right)"
                6 -> "\\left|${sum(depth - 1)}\\right|"
                else -> atom()
            }
        }

        fun operator(depth: Int): String {
            val x = pick(listOf("x", "t", "u"))
            val k = pick(listOf("k", "n", "i"))
            return when (r.nextInt(7)) {
                0 -> "\\sum_{$k=${pick(listOf("0", "1"))}}^{${pick(listOf("n", "\\infty", "N"))}} ${sum(depth)}"
                1 -> "\\prod_{$k=1}^{n} ${sum(depth)}"
                2 -> "\\int_{${pick(listOf("0", "a", "-1", "-\\infty"))}}^{${pick(listOf("1", "b", "x", "\\infty"))}} ${sum(depth)} \\, d$x"
                3 -> "\\int ${sum(depth)} \\, d$x"
                4 -> "\\lim_{$x \\to ${pick(listOf("0", "\\infty", "1", "a"))}} ${term(depth)}"
                5 -> "\\frac{d}{d$x} ${term(depth)}"
                else -> "\\frac{d^{2}y}{d$x^{2}}"
            }
        }

        fun matrix(): String {
            val env = pick(listOf("pmatrix", "bmatrix", "vmatrix"))
            val rows = 2 + r.nextInt(2)
            val cols = 2 + r.nextInt(2)
            val body = (0 until rows).joinToString(" \\\\ ") {
                (0 until cols).joinToString(" & ") { if (chance(0.4)) num() else atom() }
            }
            return "\\begin{$env} $body \\end{$env}"
        }

        fun cases(): String {
            val x = pick(listOf("x", "t"))
            val rows = 2 + r.nextInt(2)
            val conds = listOf("$x < 0", "$x \\geq 0", "$x = 0", "$x > ${num()}", "0 < $x < 1", "\\text{otherwise}")
            val body = (0 until rows).joinToString(" \\\\ ") { "${sum(1)}, & ${pick(conds)}" }
            return "f($x) = \\begin{cases} $body \\end{cases}"
        }

        fun formula(level: Int): String = when (level) {
            1 -> when (r.nextInt(4)) {
                0 -> "${atom()} ${pick(rel)} ${sum(0)}"
                1 -> term(1)
                2 -> "${v()} ${pick(rel)} ${term(1)}"
                else -> sum(1)
            }
            2 -> when (r.nextInt(4)) {
                0 -> "${sum(1)} ${pick(rel)} ${sum(2)}"
                1 -> "${operator(1)} ${pick(rel)} ${term(1)}"
                2 -> "f(${pick(vars)}) = ${sum(2)}"
                else -> operator(1)
            }
            else -> when (r.nextInt(6)) {
                0 -> "${pick(listOf("A", "B", "M"))} = ${matrix()}"
                1 -> cases()
                2 -> "\\iint_{D} ${sum(1)} \\, dx \\, dy ${pick(rel)} ${term(2)}"
                3 -> "\\frac{\\partial ${pick(listOf("f", "u", "z"))}}{\\partial ${pick(listOf("x", "y", "t"))}} = ${sum(2)}"
                4 -> "${operator(2)} = ${term(2)}"
                else -> "${sum(2)} ${pick(rel)} ${operator(1)}"
            }
        }
    }
}
