package com.kingzcheung.xime.service

import android.view.InputDevice

/** Configuration.keyboard can describe virtual/OEM keys and can lag hot-plug events. */
internal fun hasPhysicalTextKeyboard(devices: Iterable<InputDevice>): Boolean = devices.any {
    it.isEnabled && !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
        it.supportsSource(InputDevice.SOURCE_KEYBOARD)
}
