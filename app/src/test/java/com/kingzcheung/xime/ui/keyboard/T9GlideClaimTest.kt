package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 九键滑行什么时候接管（用户 11-09：开了九键滑行根本打不了字，滑一点就判成滑行）。
 * 点按落在键边、手指抖一下滑进隔壁键，都不能算滑行；从键中间滑到隔壁键中间照旧算。
 */
class T9GlideClaimTest {
    // 手机九键的比例：键宽 100、键高 50、缝 4；1 键不在滑行键里。
    private val keys: Map<Char, Rect> = buildMap {
        for (digit in '2'..'9') {
            val index = digit - '1'
            val col = index % 3; val row = index / 3
            put(digit, Rect(col * 104f, row * 54f, col * 104f + 100f, row * 54f + 50f))
        }
    }
    private fun center(digit: Char) = keys.getValue(digit).center

    // Offset is a value class, so no vararg: the calls below pass the points as a list.
    private fun stroke(points: List<Offset>): DigitStroke {
        val first = keys.entries.first { it.value.contains(points.first()) }.key
        return DigitStroke(keys, points.first(), first).also { s -> points.drop(1).forEach(s::move) }
    }

    @Test fun tapNearTheEdgeThatDriftsIntoTheNextKeyIsNotAGlide() {
        val five = keys.getValue('5')
        // 落在 5 的上沿往下 3，往上抖进 2 的中间一圈（2 的下沿往上 12，过了 18% 的边）。
        val start = Offset(five.center.x, five.top + 3f)
        val inTwo = Offset(five.center.x, keys.getValue('2').bottom - 12f)
        val s = stroke(listOf(start, inTwo))
        assertTrue("确实进了 2", s.digits.toString() == "52")
        assertFalse("只走了 19，不到 0.6 个键高", s.claimed)
        // 左右、斜着抖进隔壁也一样。
        assertFalse(stroke(listOf(Offset(five.right - 3f, five.center.y), Offset(keys.getValue('6').left + 12f, five.center.y))).claimed)
        val three = keys.getValue('3')
        assertFalse(stroke(listOf(Offset(five.right - 3f, five.top + 3f), Offset(three.left + 11f, three.bottom - 11f))).claimed)
    }

    @Test fun jitterAcrossTheBorderIsNotAGlide() {
        val five = keys.getValue('5'); val two = keys.getValue('2')
        val inFive = Offset(five.center.x, five.top + 4f)
        val inTwo = Offset(five.center.x, two.bottom - 11f)
        val s = stroke(listOf(inFive, inTwo, inFive, inTwo, inFive, inTwo, inFive))
        assertFalse(s.claimed)
    }

    @Test fun deliberateGlidesStillClaim() {
        assertTrue("5 中间到 2 中间", stroke(listOf(center('5'), center('2'))).claimed)
        assertTrue("5 中间到 6 中间", stroke(listOf(center('5'), center('6'))).claimed)
        assertTrue("9 斜着到 4", stroke(listOf(center('9'), lerp(center('9'), center('4'), 0.5f), center('4'))).claimed)
        assertTrue("5→2→4", stroke(listOf(center('5'), center('2'), center('4'))).claimed)
        // 从键边起滑，多走大半个键也算。
        val five = keys.getValue('5')
        assertTrue(stroke(listOf(Offset(five.center.x, five.top + 3f), center('2'))).claimed)
    }

    @Test fun stayingInsideOneKeyOrLeavingTheGridNeverClaims() {
        val five = keys.getValue('5')
        assertFalse("一个键里滑来滑去", stroke(listOf(center('5'), Offset(five.left + 5f, five.top + 5f), Offset(five.right - 5f, five.bottom - 5f))).claimed)
        val out = stroke(listOf(center('5'), center('2'), Offset(center('2').x, -40f)))
        assertFalse("滑出数字区作废", out.claimed)
    }
}
