package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.BroadcastFrameClock
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.MainType
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PanelType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardPanelExpansionTest {
    private val main = KeyboardPage.Main(MainType.FULL)
    private fun overlay(route: OverlayRoute) = KeyboardPage.Overlay(route, emptyList(), main)

    @Test fun onlyOpenClipboardTabsAcceptExpansion() {
        for (tab in 0..2) {
            val page = overlay(OverlayRoute.Clipboard(tab))
            assertTrue(clipboardPanelCanExpand(page, false, false))
            assertFalse(clipboardPanelCanExpand(page, true, false))
            assertFalse(clipboardPanelCanExpand(page, false, true))
        }
        for (page in listOf(main, KeyboardPage.Main(MainType.HANDWRITING),
            KeyboardPage.Panel(PanelType.NUMBER, MainType.FULL), overlay(OverlayRoute.Menu),
            overlay(OverlayRoute.Emoji), overlay(OverlayRoute.Edit), overlay(OverlayRoute.SplitWords("文字")))) {
            assertFalse("$page", clipboardPanelCanExpand(page, false, false))
        }
    }

    @Test fun heightReservesBarsPaddingAndFloatingOffset() {
        assertEquals(ClipboardPanelBounds(280, 576), clipboardPanelBounds(280, 800, 0))
        assertEquals(ClipboardPanelBounds(270, 270), clipboardPanelBounds(280, 300, 30))
        for (screen in listOf(240, 360, 800, 1280)) for (normal in listOf(180, 280, 500)) {
            for (reserved in listOf(0, 24, 80, 160)) {
                val bounds = clipboardPanelBounds(normal, screen, reserved)
                assertTrue(bounds.collapsed in 1..bounds.expanded)
                assertTrue(bounds.expanded + reserved <= screen)
            }
        }
    }

    @Test fun tinyAndInvalidAvailableSpaceNeverCreatesReversedBounds() {
        assertEquals(ClipboardPanelBounds(1, 1), clipboardPanelBounds(0, 0, 10))
        assertEquals(ClipboardPanelBounds(100, 216), clipboardPanelBounds(100, 300, -20))
        assertFalse(ClipboardPanelBounds(270, 270).shouldExpand(270f))
    }

    @Test fun dragTracksDistanceBothWaysAndHasNoCoordinateFeedback() {
        val drag = ClipboardPanelDrag(ClipboardPanelBounds(200, 500), 200f, 700f)
        assertEquals(245f, drag.heightAt(655f), 0f)
        assertEquals(310f, drag.heightAt(590f), 0f)
        // Toolbar moving to a different local origin must not add another 110 dp.
        assertEquals(310f, drag.heightAt(590f), 0f)
        assertEquals(260f, drag.heightAt(640f), 0f)
        assertEquals(200f, drag.heightAt(700f), 0f)
    }

    @Test fun dragClampsWithoutLosingOriginAfterOverscroll() {
        val drag = ClipboardPanelDrag(ClipboardPanelBounds(200, 500), 350f, 700f)
        assertEquals(500f, drag.heightAt(0f), 0f)
        assertEquals(400f, drag.heightAt(650f), 0f)
        assertEquals(200f, drag.heightAt(1000f), 0f)
        assertEquals(300f, drag.heightAt(750f), 0f)
    }

    @Test fun releaseChoosesNearestEndpoint() = runTest {
        val changes = mutableListOf<Boolean>()
        val panel = ClipboardPanelExpansion(ClipboardPanelBounds(200, 500), backgroundScope, changes::add)
        panel.beginDrag(700f)
        panel.dragTo(650f)
        assertEquals(250f, panel.height, 0f) // No early jump to expanded.
        panel.finishDrag()
        panel.beginDrag(700f)
        panel.dragTo(500f)
        panel.finishDrag()
        assertEquals(listOf(false, true), changes)
        panel.dispose()
    }

    @Test fun cancelRestoresThePreviousTargetInsteadOfCommittingADrag() = runTest {
        val changes = mutableListOf<Boolean>()
        val panel = ClipboardPanelExpansion(ClipboardPanelBounds(200, 500), backgroundScope, changes::add)
        panel.beginDrag(700f)
        panel.dragTo(400f)
        panel.finishDrag(cancelled = true)
        assertEquals(listOf(false), changes)
        panel.animateTo(true)
        panel.beginDrag(700f)
        panel.dragTo(900f)
        panel.finishDrag(cancelled = true)
        assertEquals(listOf(false, true), changes)
        panel.dispose()
    }

    @Test fun buttonExpansionInterpolatesAndCanBeInterruptedByTheFinger() = runTest {
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(backgroundScope.coroutineContext + clock)
        val panel = ClipboardPanelExpansion(ClipboardPanelBounds(200, 500), scope) {}
        panel.animateTo(true)
        runCurrent()
        clock.sendFrame(0L)
        runCurrent()
        clock.sendFrame(100_000_000L)
        runCurrent()
        val middle = panel.height
        assertTrue("Animation must pass through intermediate heights: $middle", middle > 200f && middle < 500f)
        panel.beginDrag(700f)
        panel.dragTo(720f)
        assertEquals(middle - 20f, panel.height, .001f)
        clock.sendFrame(300_000_000L)
        runCurrent()
        assertEquals(middle - 20f, panel.height, .001f)
        panel.dispose()
    }

    @Test fun completedAnimationAndDisposalLeaveNoFurtherDragCallbacks() = runTest {
        val clock = BroadcastFrameClock()
        var changes = 0
        val panel = ClipboardPanelExpansion(ClipboardPanelBounds(200, 500),
            CoroutineScope(backgroundScope.coroutineContext + clock)) { changes++ }
        panel.animateTo(true)
        runCurrent(); clock.sendFrame(0L); runCurrent()
        clock.sendFrame(220_000_000L); runCurrent()
        assertEquals(500f, panel.height, .001f)
        panel.beginDrag(600f)
        panel.dispose()
        panel.finishDrag()
        assertEquals(0, changes)
    }
}
