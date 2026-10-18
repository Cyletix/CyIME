package com.kingzcheung.xime.handwriting.training

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** 训练模式题目类型，写入样本的 `type` 字段。 */
enum class TrainingPromptType(val key: String, val title: String) {
    Kana("kana", "假名"),
    Latin("latin", "英文字母"),
    Symbol("symbol", "数学符号"),
    Formula("formula", "公式"),
    Japanese("japanese", "日语"),
    English("english", "英语"),
    Chinese("hanzi", "中文汉字"),
    Custom("custom", "自定义"),
}

/**
 * 出题用的题库，按菜单顺序排列，第一个是首次进入的默认题库。
 * 与样本的 [TrainingPromptType] 分开：几个公式题库写出的样本类型都是 formula。
 */
enum class TrainingBank(val id: String, val title: String, val inMenu: Boolean = true) {
    /** 真实公式和推导；用户导入的公式（CSV）也合并在这里出题。 */
    MathFormula("formula_math", "公式"),
    Japanese("japanese", "日语句子"),
    English("english", "英语"),
    Kana("kana", "假名"),
    Latin("latin", "英文字母"),
    Symbol("symbol", "数学符号"),
    Chinese("hanzi", "中文汉字"),
    /** 识别检查认错、还没写到认对就跳过的题；认对一次后自动移出。 */
    Missed("missed", "认错的字"),
    /** 已合并进“公式”，不再单独列出；保留以兼容旧的进度记录。 */
    ImportedFormula("formula", "导入公式", inMenu = false),
    /** 随机生成的公式，暂不在菜单里出现，代码保留以后可再开。 */
    RandomFormula("formula_random", "随机公式", inMenu = false),
}

/**
 * 一道训练题。
 *
 * - [label]：书写目标。公式题为去掉 `$$` 定界符后的 LaTeX，其它题为字符本身；
 * - [latex]：可渲染的 LaTeX。公式题同 [label]，符号题为对应命令（如 `\alpha`），假名/字母为 null；
 * - [source]：`builtin`、`generated:L2:seed…`（随机公式）或导入来源（如 `annotations.csv:133710.jpg`）。
 */
data class TrainingPrompt(
    val id: String,
    val type: TrainingPromptType,
    val label: String,
    val latex: String? = null,
    val source: String = "builtin",
    /** 导入时修复过坏字节（已删掉无法解码的字符），训练侧可选择剔除。 */
    val repaired: Boolean = false,
    /** 题目下方的小字：出处、意思、语法点，写的时候顺便复习。 */
    val hint: String? = null,
    /** 整段写的长题（推导、长句），画题区域加高。 */
    val long: Boolean = false,
    /** 题库内细分：formula / derivation / grammar / sentence / word / phrase。 */
    val kind: String? = null,
)

object BuiltinTrainingPrompts {

    private const val HIRAGANA =
        "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん" +
            "がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽぁぃぅぇぉっゃゅょゎ"
    private const val KATAKANA =
        "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン" +
            "ガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポァィゥェォッャュョヮヴー"
    private const val LATIN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    /** 常用数学符号：字符 → LaTeX。 */
    private val SYMBOLS: List<Pair<String, String>> = listOf(
        "+" to "+", "−" to "-", "×" to "\\times", "÷" to "\\div", "=" to "=", "≠" to "\\neq",
        "≈" to "\\approx", "≡" to "\\equiv", "<" to "<", ">" to ">", "≤" to "\\leq", "≥" to "\\geq",
        "±" to "\\pm", "∞" to "\\infty", "√" to "\\sqrt{}", "∑" to "\\sum", "∏" to "\\prod",
        "∫" to "\\int", "∮" to "\\oint", "∂" to "\\partial", "∇" to "\\nabla", "′" to "'",
        "°" to "^{\\circ}", "!" to "!", "(" to "(", ")" to ")", "[" to "[", "]" to "]",
        "{" to "\\{", "}" to "\\}", "|" to "|", "/" to "/", "," to ",", "." to ".", "⋅" to "\\cdot",
        "…" to "\\ldots", "∈" to "\\in", "∉" to "\\notin", "⊂" to "\\subset", "⊆" to "\\subseteq",
        "∪" to "\\cup", "∩" to "\\cap", "∅" to "\\emptyset", "∀" to "\\forall", "∃" to "\\exists",
        "¬" to "\\neg", "∧" to "\\wedge", "∨" to "\\vee", "→" to "\\rightarrow", "⇒" to "\\Rightarrow",
        "⇔" to "\\Leftrightarrow", "↦" to "\\mapsto", "∝" to "\\propto", "∠" to "\\angle",
        "⊥" to "\\perp", "∥" to "\\parallel", "△" to "\\triangle",
        "α" to "\\alpha", "β" to "\\beta", "γ" to "\\gamma", "δ" to "\\delta", "ε" to "\\varepsilon",
        "ζ" to "\\zeta", "η" to "\\eta", "θ" to "\\theta", "λ" to "\\lambda", "μ" to "\\mu",
        "ν" to "\\nu", "ξ" to "\\xi", "π" to "\\pi", "ρ" to "\\rho", "σ" to "\\sigma", "τ" to "\\tau",
        "φ" to "\\varphi", "χ" to "\\chi", "ψ" to "\\psi", "ω" to "\\omega", "Γ" to "\\Gamma",
        "Δ" to "\\Delta", "Θ" to "\\Theta", "Λ" to "\\Lambda", "Π" to "\\Pi", "Σ" to "\\Sigma",
        "Φ" to "\\Phi", "Ψ" to "\\Psi", "Ω" to "\\Omega",
        "ℝ" to "\\mathbb{R}", "ℕ" to "\\mathbb{N}", "ℤ" to "\\mathbb{Z}", "ℚ" to "\\mathbb{Q}",
        "ℂ" to "\\mathbb{C}",
    )

    fun prompts(type: TrainingPromptType): List<TrainingPrompt> = when (type) {
        TrainingPromptType.Kana -> (HIRAGANA + KATAKANA).map { TrainingPrompt("kana:$it", type, it.toString()) }
        TrainingPromptType.Latin -> LATIN.map { TrainingPrompt("latin:$it", type, it.toString()) }
        TrainingPromptType.Symbol -> SYMBOLS.map { (ch, tex) -> TrainingPrompt("symbol:$ch", type, ch, tex) }
        TrainingPromptType.Formula -> FormulaGenerator.prompts()
        // 句子类和真实公式题库来自外置文件，见 PromptBankFile
        TrainingPromptType.Japanese, TrainingPromptType.English, TrainingPromptType.Chinese,
        TrainingPromptType.Custom -> emptyList()
    }

    /** 粘贴的任意文字作为一道自定义题；看起来像 LaTeX 的用 KaTeX 渲染。 */
    fun custom(text: String): TrainingPrompt {
        val label = text.trim()
        val latex = FormulaCsvParser.stripDelimiters(label).takeIf { looksLikeLatex(label) }
        return TrainingPrompt("custom:$label", TrainingPromptType.Custom, label, latex, source = "clipboard")
    }

    internal fun looksLikeLatex(text: String): Boolean =
        text.startsWith("$") || text.contains('\\') || Regex("[\\^_]\\{|[A-Za-z0-9)][\\^_][A-Za-z0-9]").containsMatchIn(text)
}

/**
 * 外置题库文件：不进安装包，放在 App 外部私有目录 `Android/data/<包名>/files/handwriting_training/`。
 * 电脑上用 tools/handwriting_training/build_banks.py 生成并推到设备，也可在训练模式里“导入题库文件”。
 * 每行 `类别\t长题(0/1)\t题目\t提示\t来源`；文件顺序就是出题顺序（短题为主，每隔几题穿插一道长题）。
 * id 由题目内容决定，题库重新生成后同一题的计数不变。
 */
enum class PromptBankFile(val fileName: String, val type: TrainingPromptType, val prefix: String, val isLatex: Boolean) {
    Math("math_formulas.tsv", TrainingPromptType.Formula, "math", true),
    Japanese("japanese.tsv", TrainingPromptType.Japanese, "japanese", false),
    English("english.tsv", TrainingPromptType.English, "english", false),
    /** 单个汉字，给内置中文手写模型（ochwpro）微调用：模型字表里的全部汉字，按使用频率从高到低。 */
    Chinese("chinese_chars.tsv", TrainingPromptType.Chinese, "hanzi", false);

    companion object {
        const val DIR = "handwriting_training"

        fun dir(context: android.content.Context): java.io.File =
            java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, DIR)

        fun byFileName(name: String): PromptBankFile? = entries.firstOrNull { it.fileName.equals(name, ignoreCase = true) }
    }

    fun parse(lines: Sequence<String>): List<TrainingPrompt> = lines.mapNotNull { line ->
        val cols = line.split('\t')
        if (cols.size < 5) return@mapNotNull null
        val text = cols[2].trim()
        if (text.isEmpty()) return@mapNotNull null
        TrainingPrompt(
            id = "${type.key}:$prefix:" + Integer.toHexString(text.hashCode()) + ":" + text.length,
            type = type,
            label = text,
            latex = text.takeIf { isLatex },
            source = "$prefix:" + cols[4].trim(),
            hint = cols[3].trim().ifEmpty { null },
            long = cols[1].trim() == "1",
            kind = cols[0].trim().ifEmpty { null },
        )
    }.distinctBy { it.id }.toList()
}

/**
 * 公式题库 CSV 解析：两列 `file_name,LaTeX`，首行为表头，字段可用双引号包裹（`""` 转义）。
 * 无法按 UTF-8 解码的字节会被丢弃并把该题标记为 repaired，不会让整个导入失败。
 */
object FormulaCsvParser {

    data class Result(val prompts: List<TrainingPrompt>, val skippedRows: Int, val repairedRows: Int)

    fun parse(bytes: ByteArray, sourceName: String): Result {
        val text = decodeLenient(bytes).removePrefix("﻿")
        val rows = splitCsv(text)
        val prompts = mutableListOf<TrainingPrompt>()
        var skipped = 0
        var repaired = 0
        val seenIds = HashSet<String>()
        for ((index, row) in rows.withIndex()) {
            if (index == 0 && row.firstOrNull()?.trim()?.equals("file_name", ignoreCase = true) == true) continue
            if (row.size < 2) {
                if (row.any { it.isNotBlank() }) skipped++
                continue
            }
            val fileName = row[0].trim()
            val rawLatex = row.subList(1, row.size).joinToString(",")
            val damaged = rawLatex.contains(REPLACEMENT) || fileName.contains(REPLACEMENT)
            val latex = stripDelimiters(rawLatex.replace(REPLACEMENT.toString(), ""))
            if (latex.isEmpty()) {
                skipped++
                continue
            }
            if (damaged) repaired++
            var id = "formula:$sourceName:${fileName.ifEmpty { "row$index" }}"
            if (!seenIds.add(id)) id = "$id#$index".also { seenIds.add(it) }
            prompts += TrainingPrompt(
                id = id,
                type = TrainingPromptType.Formula,
                label = latex,
                latex = latex,
                source = "$sourceName:$fileName",
                repaired = damaged,
            )
        }
        return Result(prompts, skipped, repaired)
    }

    private const val REPLACEMENT = '�'

    private fun decodeLenient(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    /** 去掉 `$$...$$`、`$...$`、`\[...\]` 定界符。 */
    internal fun stripDelimiters(value: String): String {
        var s = value.trim()
        for ((open, close) in listOf("$$" to "$$", "\\[" to "\\]", "$" to "$")) {
            if (s.length >= open.length + close.length && s.startsWith(open) && s.endsWith(close)) {
                s = s.substring(open.length, s.length - close.length).trim()
                break
            }
        }
        return s
    }

    /** 最小 RFC 4180 解析：支持引号字段、字段内逗号和 `""`，CRLF/LF 行尾。 */
    internal fun splitCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else when (c) {
                '"' -> if (field.isEmpty()) inQuotes = true else field.append(c)
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> Unit
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString()); rows.add(row)
        }
        return rows
    }
}
