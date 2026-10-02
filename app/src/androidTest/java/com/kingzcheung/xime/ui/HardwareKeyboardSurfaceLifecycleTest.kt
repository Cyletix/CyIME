package com.kingzcheung.xime.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.rime.InputCommandQueue
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercise the actual keyboard mount boundary without loading a native dictionary. */
@OptIn(ExperimentalCoroutinesApi::class)
class HardwareKeyboardSurfaceLifecycleTest {
    @get:Rule val rule = createComposeRule()

    @Test fun overlayAndAcceptedInputSurviveSurfaceReplacementUntilTheOwnerIsCleared() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val queue = InputCommandQueue(scope, dispatcher)
        val store = ViewModelStore()
        val admission = AtomicLong(-1)
        val accepted = mutableListOf<String>()
        fun callbacks() = KeyboardCallbacks(
            onKeyPress = { _, _ -> },
            onCandidateSelect = {},
            inputCommands = queue,
            inputAdmissionTicket = { admission.get().takeIf { it >= 0 } },
        )
        val firstCallbacks = callbacks()
        val secondCallbacks = callbacks()
        var boundCallbacks by mutableStateOf(firstCallbacks)
        var mounted by mutableStateOf(true)
        lateinit var vm: KeyboardViewModel
        lateinit var controller: T9InputController
        val ui = KeyboardUiState(inputSessionId = 7, currentSchemaId = "rime_ice")

        try {
            rule.runOnUiThread {
                val app = ApplicationProvider.getApplicationContext<Application>()
                vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))
                    .get(KeyboardViewModel::class.java)
                // This editor begins while compact, before a KeyboardView has ever existed.
                vm.synchronizeInputSession(7, 0, false, "rime_ice")
                vm.showOverlay(OverlayRoute.Emoji)
            }
            rule.setContent {
                MaterialTheme {
                    if (mounted) {
                        Box(Modifier.fillMaxWidth().height(330.dp)) {
                            KeyboardView(vm, ui, boundCallbacks)
                        }
                    }
                }
            }
            rule.onNodeWithTag("keyboard-overlay").assertExists()
            rule.runOnIdle {
                assertEquals(OverlayRoute.Emoji, (vm.page.value as KeyboardPage.Overlay).route)
                controller = vm.bindT9Controller(firstCallbacks)
                // Keep commands pending across the actual Compose disposal/recreation.
                admission.set(1)
                firstCallbacks.onT9RunLiteralInput!!.invoke { accepted += "before collapse" }
                mounted = false
            }
            rule.waitForIdle()
            rule.runOnIdle {
                vm.closeOverlay()
                vm.showOverlay(OverlayRoute.Clipboard())
                vm.synchronizeInputSession(7, 0, false, "rime_ice")
                boundCallbacks = secondCallbacks
                mounted = true
            }
            rule.onNodeWithTag("keyboard-overlay").assertExists()
            rule.runOnIdle {
                assertEquals(OverlayRoute.Clipboard(), (vm.page.value as KeyboardPage.Overlay).route)
                assertSame(controller, vm.bindT9Controller(secondCallbacks))
                secondCallbacks.onT9RunLiteralInput!!.invoke { accepted += "after expansion" }
            }
            scheduler.advanceUntilIdle()
            assertEquals(listOf("before collapse", "after expansion"), accepted)

            rule.runOnIdle { mounted = false }
            rule.waitForIdle()
            rule.runOnIdle {
                secondCallbacks.onT9RunLiteralInput!!.invoke { accepted += "pending at destruction" }
                store.clear()
                secondCallbacks.onT9RunLiteralInput!!.invoke { accepted += "after destruction" }
            }
            var sharedQueueStillOpen = false
            queue.submit { sharedQueueStillOpen = true }
            scheduler.advanceUntilIdle()
            assertEquals(listOf("before collapse", "after expansion"), accepted)
            assertTrue("Controller disposal must not close the service's shared queue", sharedQueueStillOpen)
        } finally {
            rule.runOnUiThread { store.clear() }
            queue.close()
            scope.cancel()
        }
    }
}
