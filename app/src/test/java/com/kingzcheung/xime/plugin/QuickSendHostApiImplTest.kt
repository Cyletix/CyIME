package com.kingzcheung.xime.plugin

import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSendHostApiImplTest {
    /** Pinned clipboard items hold accounts and passwords; no plugin may read them, whatever it declares. */
    @Test fun pluginsNeverReceivePinnedClipboardText() {
        assertTrue(QuickSendHostApiImpl().list().isEmpty())
    }
}
