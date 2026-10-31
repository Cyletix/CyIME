package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.KeysConfigHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyShapeTest {
    @After fun reset() { KeyShapes.current = KeyShape.ROUNDED }

    @Test fun roundedKeepsTheThemeRadius() {
        assertEquals(8, KeyShape.ROUNDED.radius(8))
        assertEquals(12, KeyShape.ROUNDED.radius(12))
        assertEquals(14.dp, KeyShape.ROUNDED.radius(14.dp))
    }

    /** A shape only ever makes corners squarer: a theme that is already square stays as it is. */
    @Test fun squareAndSharpCapTheRadius() {
        assertEquals(5, KeyShape.SQUARE.radius(8))
        assertEquals(3, KeyShape.SQUARE.radius(3))
        assertEquals(5.dp, KeyShape.SQUARE.radius(14.dp))
        assertEquals(0, KeyShape.SHARP.radius(8))
        assertEquals(0.dp, KeyShape.SHARP.radius(14.dp))
    }

    /** 大圆角只把圆角放大到 12dp；本来就更圆的（展开候选 14dp）不变，直角主题也变圆。 */
    @Test fun rounderRaisesTheRadius() {
        assertEquals(12, KeyShape.ROUNDER.radius(8))
        assertEquals(12, KeyShape.ROUNDER.radius(0))
        assertEquals(16, KeyShape.ROUNDER.radius(16))
        assertEquals(12.dp, KeyShape.ROUNDER.radius(8.dp))
        assertEquals(14.dp, KeyShape.ROUNDER.radius(14.dp))
    }

    /** 默认仍是圆角：没存过或存了未知值都读成 ROUNDED，旧的三种 id 不变。 */
    @Test fun defaultStaysRounded() {
        assertEquals(KeyShape.ROUNDED, KeyShape.entries.first())
        assertEquals(listOf("rounded", "rounder", "square", "sharp"), KeyShape.entries.map { it.id })
    }

    @Test fun storedIdsStayReadable() {
        assertEquals(KeyShape.ROUNDED, KeyShape.fromId(null))
        assertEquals(KeyShape.ROUNDED, KeyShape.fromId("hexagon"))
        KeyShape.entries.forEach { assertEquals(it, KeyShape.fromId(it.id)) }
    }

    /** Every layout reads its key radius from the key config, so the shape applies there once. */
    @Test fun keyConfigFollowsTheShape() {
        val configured = KeysConfigHelper.configuredKeyCornerRadius()
        KeyShapes.current = KeyShape.SQUARE
        assertEquals(minOf(5, configured), KeysConfigHelper.getKeyboardKeyConfig().cornerRadius)
        KeyShapes.current = KeyShape.SHARP
        assertEquals(0, KeysConfigHelper.getKeyboardKeyConfig().cornerRadius)
        KeyShapes.current = KeyShape.ROUNDER
        assertEquals(maxOf(12, configured), KeysConfigHelper.getKeyboardKeyConfig().cornerRadius)
        KeyShapes.current = KeyShape.ROUNDED
        assertEquals(configured, KeysConfigHelper.getKeyboardKeyConfig().cornerRadius)
        assertEquals(configured, KeysConfigHelper.configuredKeyCornerRadius())
    }
}
