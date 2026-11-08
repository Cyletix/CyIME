package com.kingzcheung.xime.update

/**
 * 发布说明（docs/<x.y.z>.md）只用到很少几种写法：# 标题、整行 **加粗** 小标题、- 列表（缩进两格一层）、
 * 普通段落，行内有 **加粗**、`代码` 和 [文字](链接)。这里只认这几种，别的原样当文字显示，不引入整套 Markdown 库。
 */
object ReleaseNoteMarkdown {
    sealed interface Block
    /** level 1 是「# CyIME x.y.z」，2 是「## 小节」，整行加粗的那种算 3。 */
    data class Heading(val text: String, val level: Int) : Block
    data class Bullet(val text: String, val depth: Int) : Block
    data class Paragraph(val text: String) : Block

    sealed interface Span
    data class Text(val text: String, val bold: Boolean = false, val code: Boolean = false) : Span
    data class Link(val text: String, val url: String) : Span

    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val wholeBold = Regex("""^\*\*([^*]+)\*\*$""")

    fun blocks(markdown: String): List<Block> {
        val out = mutableListOf<Block>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotEmpty()) out += Paragraph(paragraph.toString())
            paragraph.clear()
        }
        for (raw in markdown.replace("\r\n", "\n").lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) { flush(); continue }
            val h = heading.matchEntire(line)
            val b = if (h == null) bullet.matchEntire(line) else null
            val w = if (h == null && b == null) wholeBold.matchEntire(line.trim()) else null
            when {
                h != null -> {
                    flush()
                    out += Heading(h.groupValues[2].trim(), h.groupValues[1].length.coerceAtMost(3))
                }
                b != null -> {
                    flush()
                    out += Bullet(b.groupValues[2].trim(), b.groupValues[1].replace("\t", "  ").length / 2)
                }
                w != null -> {
                    flush()
                    out += Heading(w.groupValues[1].trim(), 3)
                }
                else -> {
                    // 普通段落里连续几行合成一段；中文接中文不加空格。
                    val next = line.trim()
                    if (paragraph.isNotEmpty() && (paragraph.last().isLatinWord() || next.first().isLatinWord())) paragraph.append(' ')
                    paragraph.append(next)
                }
            }
        }
        flush()
        return out
    }

    private fun Char.isLatinWord() = this < '\u0080' && isLetterOrDigit()

    /** 去掉开头的「# CyIME x.y.z」：页面上每一版已经有自己的版本号标题。 */
    fun withoutTitle(blocks: List<Block>): List<Block> =
        if ((blocks.firstOrNull() as? Heading)?.level == 1) blocks.drop(1) else blocks

    private val inline = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)""")

    fun spans(text: String): List<Span> {
        val out = mutableListOf<Span>()
        var last = 0
        for (m in inline.findAll(text)) {
            if (m.range.first > last) out += Text(text.substring(last, m.range.first))
            val (bold, code, linkText, url) = m.destructured
            out += when {
                bold.isNotEmpty() -> Text(bold, bold = true)
                code.isNotEmpty() -> Text(code, code = true)
                else -> Link(linkText, url)
            }
            last = m.range.last + 1
        }
        if (last < text.length) out += Text(text.substring(last))
        return out
    }

    /** 折叠时显示在版本号下面的一句：正文第一段或第一条，去掉记号。 */
    fun summary(markdown: String, maxChars: Int = 60): String {
        val first = withoutTitle(blocks(markdown)).firstOrNull { it !is Heading } ?: return ""
        val text = when (first) {
            is Bullet -> first.text
            is Paragraph -> first.text
            is Heading -> first.text
        }
        val plain = spans(text).joinToString("") { span ->
            when (span) {
                is Text -> span.text
                is Link -> span.text
            }
        }
        return if (plain.length <= maxChars) plain else plain.take(maxChars).trimEnd() + "…"
    }
}
