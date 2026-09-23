package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingDragAxisTest {
    @Test fun highDensitySinglePixelStepsAreNotDiscarded() {
        val axis = FloatingDragAxis()
        var position = 0
        repeat(30) { position = axis.move(position, 1f / 3f, -100, 100) }
        assertEquals(10, position)
        repeat(60) { position = axis.move(position, -1f / 3f, -100, 100) }
        assertEquals(-10, position)
    }

    @Test fun edgeClampsDoNotCreateDragDebt() {
        val axis = FloatingDragAxis()
        var position = 10
        repeat(30) { position = axis.move(position, 0.3f, -10, 10) }
        assertEquals(10, position)
        position = axis.move(position, -1f, -10, 10)
        assertEquals(9, position)
    }

    @Test fun externalPositionChangeAndGestureEndDiscardRemainder() {
        val axis = FloatingDragAxis()
        assertEquals(0, axis.move(0, 0.4f, -100, 100))
        assertEquals(20, axis.move(20, 0.2f, -100, 100))
        axis.reset()
        assertEquals(20, axis.move(20, 0.4f, -100, 100))
    }
}
