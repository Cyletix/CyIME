package com.kingzcheung.xime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardCommitSoundTest {
    @Test fun aTouchedRecordSoundsLikeEnter() {
        assertTrue(clipboardCommitNeedsSound(nowUptime = 10_000, lastHardwareKeyUptime = 0))
        // A physical key long before does not count.
        assertTrue(clipboardCommitNeedsSound(nowUptime = 10_000, lastHardwareKeyUptime = 5_000))
    }

    @Test fun aRecordPickedWithAPhysicalKeyIsNotSoundedTwice() {
        assertFalse(clipboardCommitNeedsSound(nowUptime = 10_000, lastHardwareKeyUptime = 9_900))
    }
}
