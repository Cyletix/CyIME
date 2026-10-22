package com.kingzcheung.xime.ui.keyboard

/** The physical ten-unit letter frame; key grouping and letter order belong to the layout. */
internal data class LetterKeyboardFrame(val rowUnits: List<Int>) {
    val columns: Float get() = 10f

    fun geometry(cellWidth: Float, stagger: Float): LetterRowGeometry {
        val progress = stagger.coerceIn(0f, 1f)
        val middleFree = columns - rowUnits[1]
        val middleStart = middleFree * (.5f - .25f * progress)
        val bottomFree = columns - rowUnits[2]
        val shift = bottomFree * (.5f - .25f * progress)
        return LetterRowGeometry(cellWidth * middleStart, shift,
            middleRowEndInsetDp = cellWidth * (middleFree - middleStart),
            deleteKeyWeight = bottomFree - shift, bottomLetterWeight = rowUnits[2].toFloat())
    }
}

/** Recognize actual row facts, not schema names: complete Latin alphabet, optionally one semicolon.
 * Merged keys retain their span; 14-key equal-width rendering is a separate layout property.
 * Numeric, kana-flick, stroke and arbitrary non-letter grids cannot enter this frame.
 */
internal fun letterKeyboardFrame(rows: List<List<String>>): LetterKeyboardFrame? {
    if (rows.size != 3 || rows.any { it.isEmpty() }) return null
    val keys = rows.flatten()
    if (keys.any { it.length !in 1..2 || it.any { c -> c !in 'a'..'z' && c != ';' } }) return null
    val letters = keys.joinToString("")
    if (letters.count { it == ';' } > 1 || letters.filter { it != ';' }.toList().sorted() != ('a'..'z').toList()) return null
    val units = rows.map { row -> row.sumOf { it.length } }
    // A custom editor's one spare cell may be moved to any row. typingRows removes it;
    // the complete alphabet check above limits the total missing capacity to one unit.
    return if (units[0] in 9..10 && units[1] in 9..10 && units[2] in 6..7)
        LetterKeyboardFrame(units) else null
}

/** 14 键末尾单字母 m 从删除键借的宽度。无错位时删除键与 Shift 等宽，m 不借，与 26 键 m 对齐；
 * 宽屏错位把删除键加宽后，m 分走富余的一半（最多一个键宽），删除键仍不窄于 Shift 与删除键的平均宽。 */
internal fun fourteenMBorrowedWeight(sideKeyWeight: Float, deleteKeyWeight: Float): Float =
    ((deleteKeyWeight - sideKeyWeight) / 2f).coerceIn(0f, 1f)

/** Typewise-style honeycomb: 6/7/6/7 single letters, the short rows offset by half a key. */
internal fun isHoneycomb(rows: List<List<String>>): Boolean =
    rows.map { it.size } == com.kingzcheung.xime.settings.CustomKeyboardLayout.HONEYCOMB_ROWS

/** Seven columns; the third row puts Shift and delete in the two half-key margins. */
internal val HoneycombRowGeometry = LetterRowGeometry(0f, .5f, deleteKeyWeight = .5f, bottomLetterWeight = 6f)

internal fun letterKeyboardColumns(rows: List<List<String>>, fourteenKey: Boolean): Float =
    letterKeyboardFrame(rows)?.columns ?: if (isHoneycomb(rows)) 7f
    else if (fourteenKey) 10f else rows.firstOrNull()?.size?.toFloat() ?: 10f

internal fun letterKeyboardBottomRow(rows: List<List<String>>, fourteenKey: Boolean): QwertyBottomRowWeights =
    if (fourteenKey || letterKeyboardFrame(rows) != null || isHoneycomb(rows) || rows.firstOrNull()?.size == 10)
        QwertyBottomRowWeights.Standard else QwertyBottomRowWeights.Legacy
