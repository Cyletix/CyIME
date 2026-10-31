package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
 * 开着时所有字母键都不画上下角标（没有提示的 a、e 键和 Z X C V 的撤销、剪切、复制、粘贴也一样，见 [hidesCornerLabels]），
 * 滑动输入符号、ZXCV 快捷操作照常。打开「和角标一起显示」时角标不藏，和提示分层排开，见 [fitDoublePinyinWithCorners]。
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

/**
 * 开着键位提示时这个键不画上下角标：所有字母键（和微软双拼的分号键）一样，不管键上有没有提示。
 * 只有一部分键藏角标时，a、e 还留着角标、别的键没有，看着像漏了（用户 10-31 走查）。
 */
internal fun Map<Char, DoublePinyinKeyHint>.hidesCornerLabels(key: String): Boolean =
    key.singleOrNull()?.lowercaseChar()?.let { it in 'a'..'z' || it in this } == true

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
                                   fontScale: Float, charWidthEm: Float = CHAR_WIDTH_EM): DoublePinyinFinalsLayout? {
    if (finals.isEmpty() || widthDp <= 0f || heightDp <= 0f) return null
    fun size(lines: List<String>): Float {
        val longest = lines.maxOf { it.length }.coerceAtLeast(1)
        val byWidth = widthDp / (longest * charWidthEm * fontScale)
        val byHeight = (heightDp - BOTTOM_PADDING_DP) / (lines.size * LINE_HEIGHT_EM * fontScale)
        return minOf(baseSp, byWidth, byHeight)
    }
    val one = listOf(finals.joinToString(" "))
    val oneSize = size(one)
    val lines = if (finals.size == 1 || oneSize >= baseSp * 0.85f) one else {
        val half = (finals.size + 1) / 2
        val two = listOf(finals.take(half).joinToString(" "), finals.drop(half).joinToString(" "))
        if (size(two) > oneSize) two else one
    }
    // 两种排法都要缩到 6sp 以下时按未截断的字号比，选真正更放得下的那种（窄键上的 iang uang 排两行）。
    val font = size(lines).coerceAtLeast(MIN_FONT_SP)
    return DoublePinyinFinalsLayout(lines, font, lines.size * font * LINE_HEIGHT_EM * fontScale + BOTTOM_PADDING_DP)
}

/**
 * 「和角标一起显示」（双拼方案页，默认关）时一个键的排法。键面分三层，各层互不重叠：
 * - 上层：左边 zh/ch/sh，右边上角标；
 * - 中层：字母，居中；
 * - 下层：左边下角标，韵母在右边（没有下角标时居中）。
 * 三层的高度只看键帽大小和字号，一排键的字母一样大、一样高；放不下的字缩小，不小于 6sp（字母不小于 [MIN_LETTER_SP]）。
 * [letterOffsetDp] 是字母中心相对键中心的竖直偏移。
 */
internal data class DoublePinyinCornerLayout(
    val topSp: Float,
    val topBandDp: Float,
    val letterSp: Float,
    val letterOffsetDp: Float,
    val finals: DoublePinyinFinalsLayout?,
    val finalsAtEnd: Boolean,
    val downSp: Float,
    val bottomBandDp: Float,
)

/** 上下层左右两边离键边的距离，比单独画角标时（5dp）收一点，小键上给中间多留地方。 */
internal const val CORNER_SIDE_PADDING_DP = 3f
/** 上下层两段文字之间至少空开的宽度。 */
private const val CORNER_GAP_DP = 3f
/** 三层估宽用的字宽：拉丁字母按 0.62em（比韵母单独排时更保守），数字 0.6em，汉字和全角符号 1em。 */
private const val CORNER_LATIN_EM = 0.62f
internal const val MIN_LETTER_SP = 10f
/** 字母上下各和上层、下层空开的距离：三层正好排满时，按像素取整会让字母框和角标框叠 1px（10-31 出图测试）。 */
internal const val LETTER_CLEARANCE_DP = 1f
private const val TOP_PADDING_DP = 2f

internal fun cornerTextEm(text: String?): Float = text.orEmpty().sumOf { c ->
    when {
        c.code >= 0x2E80 -> 1.0
        c.isDigit() -> 0.6
        else -> CORNER_LATIN_EM.toDouble()
    }
}.toFloat()

/**
 * 算「和角标一起显示」的三层排法；这个键帽放不下（最小字号也会重叠）时返回 null，调用方退回只画键位提示、不画角标。
 * [upLabel]、[downLabel] 是键面上实际要写的角标（已截短），[hintSp] 是角标字号，[labelSp] 是字母原字号。
 */
internal fun fitDoublePinyinWithCorners(
    initial: String?, finals: List<String>, upLabel: String?, downLabel: String?,
    widthDp: Float, heightDp: Float, hintSp: Float, labelSp: Float, fontScale: Float,
): DoublePinyinCornerLayout? {
    val fs = fontScale.coerceAtLeast(0.1f)
    val inner = widthDp - 2 * CORNER_SIDE_PADDING_DP
    if (inner <= 0f || heightDp <= 0f) return null
    // 上层：声母 + 上角标并排，宽度不够时两个一起缩小。
    val topEm = cornerTextEm(initial) + cornerTextEm(upLabel)
    val topGap = if (!initial.isNullOrEmpty() && !upLabel.isNullOrEmpty()) CORNER_GAP_DP else 0f
    val topSp = if (topEm <= 0f) hintSp else minOf(hintSp, (inner - topGap) / (topEm * fs))
    if (topSp < MIN_FONT_SP) return null
    val topBand = TOP_PADDING_DP + hintSp * 1.2f * fs
    // 下层：至少放得下两行最小字号的韵母，至多两行原字号；同一键盘每个键一样高。
    val bottomBand = (heightDp * 0.34f).coerceIn(BOTTOM_PADDING_DP + 2 * MIN_FONT_SP * LINE_HEIGHT_EM * fs,
        BOTTOM_PADDING_DP + 2 * hintSp * LINE_HEIGHT_EM * fs)
    val middle = heightDp - topBand - bottomBand - 2 * LETTER_CLEARANCE_DP
    val letterSp = minOf(labelSp, middle / (1.2f * fs))
    if (letterSp < MIN_LETTER_SP) return null
    // 下角标先按角标字号，韵母放不下时下角标一起缩，最小 6sp。
    val downEm = cornerTextEm(downLabel)
    val maxDownSp = minOf(hintSp, (bottomBand - BOTTOM_PADDING_DP) / (1.2f * fs))
    var downSp = maxDownSp
    while (true) {
        val downWidth = downEm * downSp * fs
        val finalsWidth = if (downEm > 0f) inner - downWidth - CORNER_GAP_DP else inner
        if (downEm > 0f && downSp < MIN_FONT_SP) return null
        val fit = if (finals.isEmpty()) null
            else fitDoublePinyinFinals(finals, finalsWidth, bottomBand, hintSp, fs, CORNER_LATIN_EM)
        val fits = if (fit == null) downWidth <= inner + 0.01f else finalsWidth > 0f &&
            fit.lines.all { cornerTextEm(it) * fit.fontSp * fs <= finalsWidth + 0.01f } &&
            fit.heightDp <= bottomBand + 0.01f
        if (fits) return DoublePinyinCornerLayout(topSp, topBand, letterSp, (topBand - bottomBand) / 2f, fit,
            finalsAtEnd = downEm > 0f && fit != null, downSp = if (downEm > 0f) downSp else hintSp, bottomBandDp = bottomBand)
        if (downEm <= 0f) return null
        downSp -= 0.5f
    }
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

/**
 * 「和角标一起显示」的键面（字母由 KeyButton 按 [DoublePinyinCornerLayout.letterSp] 和偏移画在中层）：
 * 上层左边声母、右边上角标；下层左边下角标、韵母在右边（没有下角标时居中）。
 */
@Composable
internal fun DoublePinyinCornerFace(
    hint: DoublePinyinKeyHint?,
    layout: DoublePinyinCornerLayout,
    upCorner: String?,
    downCorner: String?,
    initialColor: Color,
    finalsColor: Color,
    upColor: Color,
    downColor: Color,
    fontFamily: FontFamily?,
) {
    val side = CORNER_SIDE_PADDING_DP.dp
    Box(Modifier.fillMaxSize()) {
        hint?.initial?.let { CornerText(it, layout.topSp, initialColor, FontWeight.Medium, fontFamily,
            Modifier.align(Alignment.TopStart).padding(top = TOP_PADDING_DP.dp, start = side)) }
        upCorner?.let { CornerText(it, layout.topSp, upColor, FontWeight.Medium, fontFamily,
            Modifier.align(Alignment.TopEnd).padding(top = TOP_PADDING_DP.dp, end = side)) }
        downCorner?.let { CornerText(it, layout.downSp, downColor, FontWeight.Normal, fontFamily,
            Modifier.align(Alignment.BottomStart).padding(bottom = BOTTOM_PADDING_DP.dp, start = side)) }
        val finals = layout.finals
        if (finals != null) Column(
            Modifier.align(if (layout.finalsAtEnd) Alignment.BottomEnd else Alignment.BottomCenter)
                .padding(bottom = BOTTOM_PADDING_DP.dp, end = if (layout.finalsAtEnd) side else 0.dp),
            horizontalAlignment = if (layout.finalsAtEnd) Alignment.End else Alignment.CenterHorizontally) {
            finals.lines.forEach { line ->
                Text(line, color = finalsColor, fontSize = finals.fontSp.sp, lineHeight = (finals.fontSp * LINE_HEIGHT_EM).sp,
                    fontWeight = FontWeight.Normal, textAlign = if (layout.finalsAtEnd) TextAlign.End else TextAlign.Center,
                    maxLines = 1, softWrap = false, fontFamily = fontFamily)
            }
        }
    }
}

@Composable
private fun CornerText(text: String, size: Float, color: Color, weight: FontWeight, fontFamily: FontFamily?, modifier: Modifier) {
    // 角标框固定为 1.2 倍字号高、超出的裁掉，字居中：～、全角符号和汉字会落到行高更大的备用字体上，
    // 文字框比 1.2 倍高出不少（10-31 出图测试：9sp 的「～」框高 14dp），不固定就会伸进字母那一层。
    val lineDp = (size * 1.2f * LocalDensity.current.fontScale).dp
    Box(modifier.height(lineDp).clipToBounds().testTag(DOUBLE_PINYIN_CORNER_TAG), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = size.sp, lineHeight = (size * 1.2f).sp, fontWeight = weight,
            maxLines = 1, softWrap = false, fontFamily = fontFamily, modifier = Modifier.wrapContentHeight(unbounded = true))
    }
}

/** 「和角标一起显示」里角标框的测试标签（出图测试按框查重叠）。 */
internal const val DOUBLE_PINYIN_CORNER_TAG = "double-pinyin-corner"

