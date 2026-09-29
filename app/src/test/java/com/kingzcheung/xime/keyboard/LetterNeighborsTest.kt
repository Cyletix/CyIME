package com.kingzcheung.xime.keyboard

import org.junit.Assert.*
import org.junit.Test

class LetterNeighborsTest {
    private fun keyboard(): Map<Char, LetterKeyRect> = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").flatMapIndexed { row, text ->
        text.mapIndexed { col, key -> key to LetterKeyRect(col * 40f + row * 8f, row * 60f, 40f, 60f) }
    }.toMap()
    private fun near(keys: Map<Char, LetterKeyRect>, key: Char) = encodeLetterNeighbors(keys).split(';').first { it.startsWith("$key:") }.substring(2)

    @Test fun measuredRowsIncludeHorizontalAndStaggeredNeighbors() {
        val near = near(keyboard(), 's')
        assertTrue(near.contains('a')); assertTrue(near.contains('d')); assertTrue(near.contains('w'))
        assertFalse(near.contains('p'))
    }
    @Test fun customLayoutUsesMovedLettersRatherThanQwertyNames() {
        val keys = keyboard().toMutableMap(); val oldA = keys.getValue('a')
        keys['a'] = keys.getValue('p'); keys['p'] = oldA
        assertTrue(near(keys, 's').contains('p')); assertFalse(near(keys, 's').contains('a'))
    }
    @Test fun scalingAndSplitGapRespectTheActualPanel() {
        val keys = keyboard()
        assertEquals(encodeLetterNeighbors(keys), encodeLetterNeighbors(keys.mapValues { (_, r) ->
            LetterKeyRect(r.x * 2, r.y * 3, r.width * 2, r.height * 3)
        }))
        val split = keys.mapValues { (_, r) -> r.copy(x = r.x + if (r.x >= 200f) 400f else 0f) }
        assertFalse(near(split, 't').contains('y'))
    }
    @Test fun incompleteMergedOrInvalidGeometryCannotEnableWrongLayoutCorrection() {
        assertEquals("", encodeLetterNeighbors(keyboard() - 'a'))
        assertEquals("", encodeLetterNeighbors(keyboard() + ('a' to LetterKeyRect(0f, 0f, 0f, 60f))))
    }
}
