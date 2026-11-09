package com.kingzcheung.xime.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 11-08 反馈：主题与外观页试打后返回，位置跳到下面约一屏。关掉试打框后列表要回到打开前的位置。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h800dp-mdpi")
class TypingTrialPositionTest {
    @get:Rule val rule = createComposeRule()

    private var open by mutableStateOf(false)
    private lateinit var state: LazyListState

    private fun setContent() = rule.setContent {
        state = rememberLazyListState()
        CompositionLocalProvider(LocalTypingTrialOpen provides open) {
            TypingTrialKeepsPosition(state)
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items(200) { Text("设置 $it", Modifier.height(60.dp)) }
            }
        }
    }

    @Test fun closingTrialPutsListBackWhereItWas() {
        setContent()
        rule.runOnIdle { runBlocking { state.scrollToItem(7, 15) } }
        rule.runOnIdle { open = true }
        rule.waitForIdle()
        // 关试打框那一下列表被推到下面（焦点落到下面的设置项）。
        rule.runOnIdle { open = false; runBlocking { state.scrollToItem(20, 0) } }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(7, state.firstVisibleItemIndex)
            assertEquals(15, state.firstVisibleItemScrollOffset)
        }
    }

    @Test fun scrollingAfterTheShortWindowIsLeftAlone() {
        setContent()
        rule.runOnIdle { runBlocking { state.scrollToItem(3, 0) } }
        rule.runOnIdle { open = true }
        rule.waitForIdle()
        rule.runOnIdle { open = false }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(TYPING_TRIAL_RESTORE_MS + 200)
        rule.runOnIdle { runBlocking { state.scrollToItem(40, 0) } }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(40, state.firstVisibleItemIndex) }
    }

    @Test fun withoutTrialNothingIsRestored() {
        setContent()
        rule.runOnIdle { runBlocking { state.scrollToItem(12, 0) } }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(12, state.firstVisibleItemIndex) }
    }
}
