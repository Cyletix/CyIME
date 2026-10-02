package com.kingzcheung.xime.rime

import android.os.SystemClock
import org.junit.Assert.assertTrue

internal fun RimeEngine.awaitT9Refinement() {
    val revision = readQueuedComposition()!!.engineRevision
    val deadline = SystemClock.elapsedRealtime() + 5_000
    while (t9RefinementState() == 1 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
    assertTrue("sentence worker did not finish", t9RefinementState() != 1)
    if (t9RefinementState() == 2) refineQueuedT9Result(revision)
}
