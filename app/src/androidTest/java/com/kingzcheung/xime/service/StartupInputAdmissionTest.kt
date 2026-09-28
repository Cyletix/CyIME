package com.kingzcheung.xime.service

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.T9InputController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StartupInputAdmissionTest {
    @Test fun blockedT9InputAndInvalidatedQueueNeverReplay(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(RimeConfigHelper.prepareEngine(context))
        val engine = RimeEngine.getInstance()
        assertTrue(engine.ensureSession())
        val previous = engine.getCurrentSchema()
        val previousAscii = engine.isAsciiMode()
        try {
            assertTrue(engine.switchSchema("t9_pinyin"))
            engine.setOption("ascii_mode", false)
            engine.clearQueuedT9Composition()
            val gate = InputReadiness()
            val controller = T9InputController(inputAdmissionTicket = gate::ticket)
            val commits = AtomicInteger()
            repeat(20) {
                controller.onDigitPressed("7")
                controller.enqueueLiteralInput { commits.incrementAndGet() }
                controller.onDeleted { commits.incrementAndGet() }
            }
            gate.completeStartup()
            val first = CountDownLatch(1)
            controller.enqueueLiteralInput { first.countDown() }
            assertTrue(first.await(10, TimeUnit.SECONDS))
            assertEquals(0, commits.get())
            assertEquals("", engine.getInput())

            val entered = CountDownLatch(1)
            val release = CompletableDeferred<Unit>()
            controller.enqueueLiteralInput { entered.countDown(); release.await() }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            controller.onDigitPressed("7")
            repeat(5) { controller.onDeleted { commits.incrementAndGet() } }
            gate.deployment(true)
            gate.deployment(false)
            release.complete(Unit)
            val after = CountDownLatch(1)
            controller.enqueueLiteralInput { after.countDown() }
            assertTrue(after.await(10, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals("", engine.getInput())
            assertEquals("Old delete callbacks must never reach the host", 0, commits.get())

            controller.onDigitPressed("7")
            val typed = CountDownLatch(1)
            controller.enqueueLiteralInput { typed.countDown() }
            assertTrue(typed.await(10, TimeUnit.SECONDS))
            assertTrue("Fresh input must work after readiness", engine.getInput().isNotEmpty())
        } finally {
            engine.clearQueuedT9Composition()
            if (previous.isNotEmpty()) engine.switchSchema(previous)
            engine.setOption("ascii_mode", previousAscii)
        }
    }

    @Test fun oldSystemTouchIsConsumedButFreshTouchReachesTheKey() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val gate = InputReadiness { 100L }
            gate.completeStartup()
            var ups = 0
            val container = VoiceKeyboardContainer(instrumentation.targetContext,
                { InputUIState() }, {}, {}, {}, {}, {}, { false }, {},
                acceptTouchDown = gate::acceptsEvent)
            val child = View(instrumentation.targetContext).apply {
                setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_UP) ups++; true }
            }
            container.addView(child, FrameLayout.LayoutParams(200, 200))
            val spec = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
            container.measure(spec, spec); container.layout(0, 0, 200, 200)
            fun gesture(down: Long) {
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(down, down + action, action, 50f, 50f, 0)
                    container.dispatchTouchEvent(event); event.recycle()
                }
            }
            gesture(50L)
            assertEquals(0, ups)
            gesture(110L)
            assertEquals(1, ups)
        }
    }
}
