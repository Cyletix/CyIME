package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.settings.DoublePinyinScheme

/**
 * 双拼键位提示（设置 › 双拼方案，默认关）：双拼 26 键每个字母键上写出这个键代表的翘舌声母和韵母，
 * 内容与双拼方案页的键位图（DoublePinyinKeyMap）同源，都来自 [DoublePinyinScheme.keyLabels]。
 * 开着时这些键不画上下角标（同开着滑行输入时），滑动输入符号照常。
 */
internal data class DoublePinyinKeyHint(
    /** zh/ch/sh，写在右上角（上角标的位置）。 */
    val initial: String?,
    /** 韵母，写在字母下面；和键字母一样的韵母（a 键的 a）不重复写，v 键的 ü 照写。 */
    val finals: List<String>,
)

/** 当前键盘的双拼键位提示（字母小写 → 提示）；只在开着开关的双拼 26 键提供，其余键盘为 null。 */
internal val LocalDoublePinyinKeyHints = staticCompositionLocalOf<Map<Char, DoublePinyinKeyHint>?> { null }

/** 韵母按键上的写法：v 写作 ü（lü、lüe），与常见双拼键位图、双拼方案页一致。 */
internal fun doublePinyinFinalLabel(final: String) = final.replace('v', 'ü')

internal fun doublePinyinKeyHints(scheme: DoublePinyinScheme): Map<Char, DoublePinyinKeyHint> =
    scheme.keyLabels().mapNotNull { (key, labels) ->
        val finals = labels.second.map(::doublePinyinFinalLabel).filter { it != key.toString() }
        if (labels.first == null && finals.isEmpty()) null else key to DoublePinyinKeyHint(labels.first, finals)
    }.toMap()

/** 这个键的提示：单个字母（或微软双拼的分号）键才有，大小写都认。 */
internal fun Map<Char, DoublePinyinKeyHint>.forKey(key: String): DoublePinyinKeyHint? =
    key.singleOrNull()?.lowercaseChar()?.let(::get)

/** 字母下面的韵母排成几行、多大字号；[heightDp] 是这几行连同底边距占的高度。 */
internal data class DoublePinyinFinalsLayout(val lines: List<String>, val fontSp: Float, val heightDp: Float)

private const val CHAR_WIDTH_EM = 0.58f
private const val LINE_HEIGHT_EM = 1.15f
private const val BOTTOM_PADDING_DP = 2f
private const val MIN_FONT_SP = 6f

/**
 * 韵母先排一行（iang uang 之间空一格）；一行要缩到原字号 85% 以下才放得下时分两行（iang / uang）。
 * 宽度按每字约 0.58em 估算，字号不超过角标字号 [baseSp]，也不小于 6sp。
 */
internal fun fitDoublePinyinFinals(finals: List<String>, widthDp: Float, heightDp: Float, baseSp: Float,
                                   fontScale: Float): DoublePinyinFinalsLayout? {
    if (finals.isEmpty() || widthDp <= 0f || heightDp <= 0f) return null
    fun size(lines: List<String>): Float {
        val longest = lines.maxOf { it.length }.coerceAtLeast(1)
        val byWidth = widthDp / (longest * CHAR_WIDTH_EM * fontScale)
        val byHeight = (heightDp - BOTTOM_PADDING_DP) / (lines.size * LINE_HEIGHT_EM * fontScale)
        return minOf(baseSp, byWidth, byHeight).coerceAtLeast(MIN_FONT_SP)
    }
    val one = listOf(finals.joinToString(" "))
    val oneSize = size(one)
    val lines = if (finals.size == 1 || oneSize >= baseSp * 0.85f) one else {
        val half = (finals.size + 1) / 2
        val two = listOf(finals.take(half).joinToString(" "), finals.drop(half).joinToString(" "))
        if (size(two) > oneSize) two else one
    }
    val font = size(lines)
    return DoublePinyinFinalsLayout(lines, font, lines.size * font * LINE_HEIGHT_EM * fontScale + BOTTOM_PADDING_DP)
}

/** 键面上的提示：翘舌声母在右上角，韵母在字母下面居中。颜色、字体同角标。 */
@Composable
internal fun DoublePinyinKeyHintFace(
    hint: DoublePinyinKeyHint,
    finals: DoublePinyinFinalsLayout?,
    initialSize: TextUnit,
    initialColor: Color,
    finalsColor: Color,
    fontFamily: FontFamily?,
) {
    Box(Modifier.fillMaxSize()) {
        hint.initial?.let { initial ->
            Text(initial, color = initialColor, fontSize = initialSize, lineHeight = (initialSize.value * 1.2f).sp,
                fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, fontFamily = fontFamily,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp))
        }
        if (finals != null) Column(Modifier.align(Alignment.BottomCenter).padding(bottom = BOTTOM_PADDING_DP.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            finals.lines.forEach { line ->
                Text(line, color = finalsColor, fontSize = finals.fontSp.sp, lineHeight = (finals.fontSp * LINE_HEIGHT_EM).sp,
                    fontWeight = FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                    fontFamily = fontFamily)
            }
        }
    }
}
