package com.kingzcheung.xime.service

import android.view.InputDevice
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class PhysicalKeyboardConnectionTest {
    private fun device(virtual: Boolean = false, enabled: Boolean = true,
                       type: Int = InputDevice.KEYBOARD_TYPE_ALPHABETIC, keyboardSource: Boolean = true) = mock<InputDevice> {
        on { isVirtual } doReturn virtual
        on { isEnabled } doReturn enabled
        on { keyboardType } doReturn type
        on { supportsSource(InputDevice.SOURCE_KEYBOARD) } doReturn keyboardSource
    }

    @Test fun `virtual keyboards and media buttons do not enable candidate numbers`() {
        assertFalse(hasPhysicalTextKeyboard(listOf(device(virtual = true),
            device(type = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC),
            device(type = InputDevice.KEYBOARD_TYPE_NONE))))
    }

    @Test fun `disabled and non keyboard devices do not count`() {
        assertFalse(hasPhysicalTextKeyboard(listOf(device(enabled = false), device(keyboardSource = false))))
    }

    @Test fun `physical alphabetic keyboard connects and removal clears connection`() {
        val keyboard = device()
        assertTrue(hasPhysicalTextKeyboard(listOf(device(virtual = true), keyboard)))
        assertFalse(hasPhysicalTextKeyboard(listOf(device(virtual = true))))
        assertFalse(hasPhysicalTextKeyboard(emptyList()))
    }
}
